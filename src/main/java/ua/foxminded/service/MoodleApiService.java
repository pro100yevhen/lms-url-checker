package ua.foxminded.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MoodleApiService {

    private final String moodleToken;
    private final String baseUrl;
    private final WebClient webClient;

    private static final String FORMAT = "json";
    private static final String WSTOKEN = "wstoken";
    private static final String WSFUNCTION = "wsfunction";
    private static final String MOODLE_WS_REST_FORMAT = "moodlewsrestformat";
    private static final String ID = "id";
    private static final String INTRO = "intro";
    private static final String COURSES = "courses";
    private static final String ASSIGNMENTS = "assignments";
    private static final String FUNCTION_ASSIGNMENTS = "mod_assign_get_assignments";
    private static final String COURSE_ID_KEY = "courseids[0]";
    private static final String REGEX_HREF = "href=\\\"(.*?)\\\"";

    public MoodleApiService(final WebClient.Builder webClientBuilder,
                            @Value("${moodle.base-url}") final String baseUrl,
                            @Value("${moodle.token}") final String moodleToken) {
        this.baseUrl = baseUrl;
        this.moodleToken = moodleToken;
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
    }

    public Flux<Integer> getCourseIds(final String function) {
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

    public Flux<List<String>> extractAssignmentLinks(Flux<Integer> courseIds) {
        final int maxConcurrentRequests = 10;
        return courseIds
                .flatMap(this::fetchAssignmentsForCourse, maxConcurrentRequests)
                .flatMap(taskData -> Flux.fromIterable(extractLinks(taskData)), maxConcurrentRequests)
                .buffer(10);
    }

    public Flux<String> fetchAssignmentsForCourse(Integer courseId) {
        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .queryParam(WSTOKEN, moodleToken)
                        .queryParam(WSFUNCTION, FUNCTION_ASSIGNMENTS)
                        .queryParam(MOODLE_WS_REST_FORMAT, FORMAT)
                        .queryParam(COURSE_ID_KEY, courseId)
                        .build())
                .header("Accept", "application/json")
                .retrieve()
                .bodyToFlux(DataBuffer.class)
                .map(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return new String(bytes, StandardCharsets.UTF_8);
                })
                .collectList()
                .map(chunks -> String.join("", chunks))
                .flatMapMany(this::processResponse);
    }

    private Flux<String> processResponse(String response) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);

            final List<Map<String, Object>> courses = (List<Map<String, Object>>) responseMap.get(COURSES);

            if (courses == null || courses.isEmpty()) {
                return Flux.empty();
            }

            final Map<String, Object> firstCourse = courses.get(0);
            final List<Map<String, Object>> assignments = (List<Map<String, Object>>) firstCourse.get(ASSIGNMENTS);

            if (assignments == null || assignments.isEmpty()) {
                return Flux.empty();
            }

            return Flux.fromIterable(assignments)
                    .map(assignment -> (String) assignment.get(INTRO));

        } catch (Exception e) {
            return Flux.error(e);
        }
    }

    private List<String> extractLinks(String intro) {
        final List<String> links = new ArrayList<>();
        final Pattern pattern = Pattern.compile(REGEX_HREF);
        final Matcher matcher = pattern.matcher(intro);

        while (matcher.find()) {
            links.add(matcher.group(1));
        }
        return links;
    }
}