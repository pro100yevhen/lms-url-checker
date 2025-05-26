package ua.foxminded.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ua.foxminded.model.LinkValidationResult;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinkValidatorServiceTest {

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private LinkValidatorService linkValidatorService;

    @BeforeEach
    void setUp() {
        // Use lenient() to avoid UnnecessaryStubbingException for tests that don't use these mocks
        lenient().when(webClientBuilder.clientConnector(any())).thenReturn(webClientBuilder);
        lenient().when(webClientBuilder.build()).thenReturn(webClient);
        lenient().when(webClient.get()).thenReturn(requestHeadersUriSpec);
        lenient().when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);

        linkValidatorService = new LinkValidatorService(webClientBuilder, 30, 10);
    }

    @Test
    void validateLinks_shouldReturnValidResult_whenLinkIsValid() {
        // Arrange
        String validLink = "https://example.com";
        LinkValidationResult input = new LinkValidationResult(validLink, false, "Course", "Task", "");

        ClientResponse clientResponse = ClientResponse.create(HttpStatus.OK).build();

        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.just(clientResponse.toEntity(Void.class).block()));

        // Act & Assert
        StepVerifier.create(linkValidatorService.validateLinks(Flux.just(input)))
                .expectNextMatches(result -> 
                    result.link().equals(validLink) && 
                    result.valid() && 
                    result.courseName().equals("Course") && 
                    result.taskName().equals("Task") && 
                    result.statusMessage().equals("200 OK"))
                .verifyComplete();
    }

    @Test
    void validateLinks_shouldReturnInvalidResult_whenLinkIsInvalid() {
        // Arrange
        String invalidLink = "https://invalid-example.com";
        LinkValidationResult input = new LinkValidationResult(invalidLink, false, "Course", "Task", "");

        ClientResponse clientResponse = ClientResponse.create(HttpStatus.NOT_FOUND).build();

        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.just(clientResponse.toEntity(Void.class).block()));

        // Act & Assert
        StepVerifier.create(linkValidatorService.validateLinks(Flux.just(input)))
                .expectNextMatches(result -> 
                    result.link().equals(invalidLink) && 
                    !result.valid() && 
                    result.courseName().equals("Course") && 
                    result.taskName().equals("Task") && 
                    result.statusMessage().equals("404 NOT_FOUND"))
                .verifyComplete();
    }

    @Test
    void validateLinks_shouldSkipFigmaLinks() {
        // Arrange
        String figmaLink = "https://figma.com/design";
        LinkValidationResult input = new LinkValidationResult(figmaLink, false, "Course", "Task", "");

        // Act & Assert
        StepVerifier.create(linkValidatorService.validateLinks(Flux.just(input)))
                .expectNextMatches(result -> 
                    result.link().equals(figmaLink) && 
                    result.valid() && 
                    result.courseName().equals("Course") && 
                    result.taskName().equals("Task") && 
                    result.statusMessage().equals("Figma link (validation skipped)"))
                .verifyComplete();
    }

    @Test
    void validateLinks_shouldHandleError_whenExceptionOccurs() {
        // Arrange
        String errorLink = "https://error.com";
        LinkValidationResult input = new LinkValidationResult(errorLink, false, "Course", "Task", "");

        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.error(new RuntimeException("Connection error")));

        // Act & Assert
        StepVerifier.create(linkValidatorService.validateLinks(Flux.just(input)))
                .expectNextMatches(result -> 
                    result.link().equals(errorLink) && 
                    !result.valid() && 
                    result.courseName().equals("Course") && 
                    result.taskName().equals("Task") && 
                    result.statusMessage().equals("Connection error"))
                .verifyComplete();
    }
}
