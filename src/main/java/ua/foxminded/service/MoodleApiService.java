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

    public MoodleApiService(final WebClient.Builder webClientBuilder,
                            @Value("${moodle.base-url}") final String baseUrl,
                            @Value("${moodle.token}") final String moodleToken) {
        this.baseUrl = baseUrl;
        this.moodleToken = moodleToken;
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
    }

    public Flux<Integer> getCourseIds(final String function) {
        final String id = "id";
        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .queryParam(WSTOKEN, moodleToken)
                        .queryParam(WSFUNCTION, function)
                        .queryParam(MOODLE_WS_REST_FORMAT, FORMAT)
                        .build())
                .header("Accept", "application/json")
                .retrieve()
                .bodyToFlux(Map.class)
                .map(map -> (Integer) map.get(id));
    }

    public Flux<List<String>> extractAssignmentLinks(Flux<Integer> courseIds) {
        final int maxConcurrentRequests = 10;
        return courseIds
                .flatMap(courseId -> fetchAssignmentsForCourse(courseId), maxConcurrentRequests)
                .map(this::extractLinks);
    }

    public Flux<String> fetchAssignmentsForCourse(Integer courseId) {
        final String function = "mod_assign_get_assignments";
        final String courseIdKey = "courseids[0]";
        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .queryParam(WSTOKEN, moodleToken)
                        .queryParam(WSFUNCTION, function)
                        .queryParam(MOODLE_WS_REST_FORMAT, FORMAT)
                        .queryParam(courseIdKey, courseId)
                        .build())
                .header("Accept", "application/json")
                .retrieve()
                .bodyToFlux(DataBuffer.class)
                .map(dataBuffer -> {
                    // Process each DataBuffer chunk
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    DataBufferUtils.release(dataBuffer);
                    return new String(bytes, StandardCharsets.UTF_8);
                })
                .collectList()
                .map(chunks -> String.join("", chunks))
                .flatMapMany(response -> processResponse(response));
    }

    private Flux<String> processResponse(String response) {
        final String intro = "intro";

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> responseMap = objectMapper.readValue(response, Map.class);

            final List<Map<String, Object>> courses = (List<Map<String, Object>>) responseMap.get("courses");

            if (courses == null || courses.isEmpty()) {
                final String emptyResponse = "No courses found in response";
                return Flux.just(emptyResponse);
            }

            final int courseIndex = 0;
            final Map<String, Object> firstCourse = courses.get(courseIndex);
            final List<Map<String, Object>> assignments = (List<Map<String, Object>>) firstCourse.get("assignments");

            if (assignments == null || assignments.isEmpty()) {
                final String emptyResponse = "No assignments found for course";
                return Flux.just(emptyResponse);
            }

            return Flux.fromIterable(assignments)
                    .map(assignment -> (String) assignment.get(intro));

        } catch (Exception e) {
            return Flux.error(e);
        }
    }

    private List<String> extractLinks(String intro) {
        final String regex = "href=\\\"(.*?)\\\"";
        final List<String> links = new ArrayList<>();

        final Pattern pattern = Pattern.compile(regex);
        final Matcher matcher = pattern.matcher(intro);

        while (matcher.find()) {
            links.add(matcher.group(1));
        }
        return links;
    }
}
