package ua.foxminded.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MoodleApiServiceTest {

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private MoodleApiService moodleApiService;
    private final String moodleToken = "test-token";
    private final String baseUrl = "https://moodle-test.com";

    @BeforeEach
    void setUp() {
        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(java.util.function.Function.class))).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.header(anyString(), anyString())).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.retrieve()).thenReturn(responseSpec);

        moodleApiService = new MoodleApiService(webClientBuilder, baseUrl, moodleToken);
    }

    @Test
    void getCourseIds_shouldReturnCourseIds() {
        // Arrange
        List<Map<String, Object>> coursesList = List.of(
                Map.of("id", 1, "shortname", "Course1"),
                Map.of("id", 2, "shortname", "Course2")
        );

        when(responseSpec.bodyToFlux(Map.class)).thenReturn(Flux.fromIterable(coursesList));

        // Act & Assert
        StepVerifier.create(moodleApiService.getCourseIds())
                .expectNext(1)
                .expectNext(2)
                .verifyComplete();
    }

    @Test
    void fetchAssignmentsForCourse_shouldReturnLinkValidationResults() {
        // Arrange
        String jsonResponse = """
                {
                    "courses": [
                        {
                            "id": 1,
                            "shortname": "Course1",
                            "assignments": [
                                {
                                    "id": 101,
                                    "name": "Assignment1",
                                    "intro": "<p>Test assignment with <a href='https://example.com'>link</a></p>"
                                }
                            ]
                        }
                    ]
                }
                """;

        DataBuffer dataBuffer = new DefaultDataBufferFactory().wrap(jsonResponse.getBytes(StandardCharsets.UTF_8));
        when(responseSpec.bodyToFlux(DataBuffer.class)).thenReturn(Flux.just(dataBuffer));

        // Act & Assert
        StepVerifier.create(moodleApiService.fetchAssignmentsForCourse(1))
                .expectNextMatches(result -> 
                    result.link().equals("https://example.com") && 
                    !result.valid() && 
                    result.courseName().equals("Course1") && 
                    result.taskName().equals("Assignment1") && 
                    result.statusMessage().equals(""))
                .verifyComplete();
    }

    @Test
    void extractLinks_shouldExtractLinksFromHtml() {
        // This is a private method, so we'll test it indirectly through fetchAssignmentsForCourse
        // Arrange
        String jsonResponse = """
                {
                    "courses": [
                        {
                            "id": 1,
                            "shortname": "Course1",
                            "assignments": [
                                {
                                    "id": 101,
                                    "name": "Assignment1",
                                    "intro": "<p>Test with multiple links: <a href='https://example1.com'>link1</a> and <a href='https://example2.com'>link2</a></p>"
                                }
                            ]
                        }
                    ]
                }
                """;

        DataBuffer dataBuffer = new DefaultDataBufferFactory().wrap(jsonResponse.getBytes(StandardCharsets.UTF_8));
        when(responseSpec.bodyToFlux(DataBuffer.class)).thenReturn(Flux.just(dataBuffer));

        // Act & Assert
        StepVerifier.create(moodleApiService.fetchAssignmentsForCourse(1))
                .expectNextMatches(result -> 
                    result.link().equals("https://example1.com") && 
                    result.courseName().equals("Course1") && 
                    result.taskName().equals("Assignment1"))
                .expectNextMatches(result -> 
                    result.link().equals("https://example2.com") && 
                    result.courseName().equals("Course1") && 
                    result.taskName().equals("Assignment1"))
                .verifyComplete();
    }

    @Test
    void extractAssignmentLinks_shouldFlatMapCourseIds() {
        // Arrange
        lenient().when(responseSpec.bodyToFlux(Map.class)).thenReturn(Flux.just(Map.of("id", 1)));

        String jsonResponse = """
                {
                    "courses": [
                        {
                            "id": 1,
                            "shortname": "Course1",
                            "assignments": [
                                {
                                    "id": 101,
                                    "name": "Assignment1",
                                    "intro": "<p>Test assignment with <a href='https://example.com'>link</a></p>"
                                }
                            ]
                        }
                    ]
                }
                """;

        DataBuffer dataBuffer = new DefaultDataBufferFactory().wrap(jsonResponse.getBytes(StandardCharsets.UTF_8));
        when(responseSpec.bodyToFlux(DataBuffer.class)).thenReturn(Flux.just(dataBuffer));

        // Act & Assert
        StepVerifier.create(moodleApiService.extractAssignmentLinks(Flux.just(1)))
                .expectNextMatches(result -> 
                    result.link().equals("https://example.com") && 
                    result.courseName().equals("Course1") && 
                    result.taskName().equals("Assignment1"))
                .verifyComplete();
    }
}
