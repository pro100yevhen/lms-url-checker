package ua.foxminded.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import ua.foxminded.model.LinkValidationResult;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

@Service
public class MoodleApiService {

    private final String moodleToken;
    private final String baseUrl;
    private final WebClient webClient;

    private static final String FORMAT = "json";
    private static final String WSTOKEN = "wstoken";
    private static final String WSFUNCTION = "wsfunction";
    private static final String MOODLE_WS_REST_FORMAT = "moodlewsrestformat";

    public MoodleApiService(final WebClient.Builder webClientBuilder,
                            @Value("${moodle.base-url}") final String baseUrl,
                            @Value("${moodle.token}") final String moodleToken) {
        this.baseUrl = baseUrl;
        this.moodleToken = moodleToken;
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
    }

    public Flux<Integer> getCourseIds() {
        final String function = "core_course_get_courses";
        final String ID = "id";

        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .queryParam(WSTOKEN, moodleToken)
                        .queryParam(WSFUNCTION, function)
                        .queryParam(MOODLE_WS_REST_FORMAT, FORMAT)
                        .build())
                .header("Accept", "application/json")
                .retrieve()
                .bodyToFlux(Map.class)
                .map(map -> (Integer) map.get(ID));
    }

    public Flux<LinkValidationResult> extractAssignmentLinks(final Flux<Integer> courseIds) {
        return courseIds
                .flatMap(this::fetchAssignmentsForCourse);
    }

    public Flux<LinkValidationResult> fetchAssignmentsForCourse(final Integer courseId) {
        final String functionAssignments = "mod_assign_get_assignments";
        final String courseIdKey = "courseids[0]";

        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .queryParam(WSTOKEN, moodleToken)
                        .queryParam(WSFUNCTION, functionAssignments)
                        .queryParam(MOODLE_WS_REST_FORMAT, FORMAT)
                        .queryParam(courseIdKey, courseId)
                        .build())
                .header("Accept", "application/json")
                .retrieve()
                .bodyToFlux(DataBuffer.class)
                .map(dataBuffer -> {
                    final byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return new String(bytes, StandardCharsets.UTF_8);
                })
                .collectList()
                .map(chunks -> String.join("", chunks))
                .flatMapMany(this::processResponse);
    }

    private Flux<LinkValidationResult> processResponse(final String response) {
        final Set<String> processedLinks = ConcurrentHashMap.newKeySet();

        final String introBlock = "intro";
        final String coursesBlock = "courses";
        final String assignmentsBlock = "assignments";
        try {
            final ObjectMapper objectMapper = new ObjectMapper();
            final Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);
            final List<Map<String, Object>> courses = (List<Map<String, Object>>) responseMap.get(coursesBlock);

            return Flux.fromIterable(courses)
                    .flatMap(course -> {
                        final String courseName = (String) course.get("shortname");
                        final List<Map<String, Object>> assignments = (List<Map<String, Object>>) course.get(
                                assignmentsBlock);

                        return Flux.fromIterable(assignments)
                                .flatMap(assignment -> {
                                    final String name = "name";
                                    final String taskName = (String) assignment.get(name);
                                    final String intro = (String) assignment.get(introBlock);
                                    final List<String> links = extractLinks(intro);
                                    final String emptyStatusMessage = "";
                                    return Flux.fromIterable(links)
                                            .filter(processedLinks::add)
                                            .map(link -> new LinkValidationResult(link, false, courseName, taskName, emptyStatusMessage));
                                });
                    });

        } catch (final Exception e) {
            return Flux.error(e);
        }
    }

    private List<String> extractLinks(final String intro) {
        final List<String> links = new ArrayList<>();
        final Document doc = Jsoup.parse(intro);
        final Elements anchors = doc.select("a[href]");

        for (final Element anchor : anchors) {
            final String link = anchor.attr("href");

            if (link.startsWith("http://") || link.startsWith("https://")) {
                links.add(link);
            }
        }
        return links;
    }
}