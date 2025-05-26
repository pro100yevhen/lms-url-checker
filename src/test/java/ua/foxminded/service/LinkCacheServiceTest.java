package ua.foxminded.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import ua.foxminded.model.LinkValidationResult;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LinkCacheServiceTest {

    @Mock
    private MoodleApiService moodleApiService;

    @Mock
    private LinkValidatorService linkValidatorService;

    private LinkCacheService linkCacheService;
    private final long cacheDurationHours = 24;

    @BeforeEach
    void setUp() {
        linkCacheService = new LinkCacheService(moodleApiService, linkValidatorService, cacheDurationHours);
    }

    @Test
    void getLinkValidationResults_shouldReturnCachedResults_whenCacheIsValid() {
        // Arrange
        List<LinkValidationResult> cachedResults = List.of(
                new LinkValidationResult("https://example.com", true, "Course1", "Task1", "200 OK")
        );

        // Set up the cache with initial data
        when(moodleApiService.getCourseIds()).thenReturn(Flux.just(1));
        when(moodleApiService.extractAssignmentLinks(any())).thenReturn(Flux.just(
                new LinkValidationResult("https://example.com", false, "Course1", "Task1", "")
        ));
        when(linkValidatorService.validateLinks(any())).thenReturn(Flux.just(
                new LinkValidationResult("https://example.com", true, "Course1", "Task1", "200 OK")
        ));

        // First call to populate cache
        StepVerifier.create(linkCacheService.getLinkValidationResults(true))
                .expectNextMatches(results -> results.size() == 1)
                .verifyComplete();

        // Reset mocks to verify they're not called again
        reset(moodleApiService, linkValidatorService);

        // Act & Assert - Second call should use cache
        StepVerifier.create(linkCacheService.getLinkValidationResults(false))
                .expectNextMatches(results -> 
                    results.size() == 1 && 
                    results.get(0).link().equals("https://example.com") &&
                    results.get(0).valid())
                .verifyComplete();

        // Verify that the services were not called again
        verify(moodleApiService, never()).getCourseIds();
        verify(linkValidatorService, never()).validateLinks(any());
    }

    @Test
    void getLinkValidationResults_shouldFetchFreshResults_whenForceRefreshIsTrue() {
        // Arrange
        when(moodleApiService.getCourseIds()).thenReturn(Flux.just(1));
        when(moodleApiService.extractAssignmentLinks(any())).thenReturn(Flux.just(
                new LinkValidationResult("https://example.com", false, "Course1", "Task1", "")
        ));
        when(linkValidatorService.validateLinks(any())).thenReturn(Flux.just(
                new LinkValidationResult("https://example.com", true, "Course1", "Task1", "200 OK")
        ));

        // First call to populate cache
        StepVerifier.create(linkCacheService.getLinkValidationResults(true))
                .expectNextMatches(results -> results.size() == 1)
                .verifyComplete();

        // Set up different results for second call
        when(linkValidatorService.validateLinks(any())).thenReturn(Flux.just(
                new LinkValidationResult("https://example.com", false, "Course1", "Task1", "404 NOT_FOUND")
        ));

        // Act & Assert - Second call with forceRefresh=true should fetch fresh results
        StepVerifier.create(linkCacheService.getLinkValidationResults(true))
                .expectNextMatches(results -> 
                    results.size() == 1 && 
                    results.get(0).link().equals("https://example.com") &&
                    !results.get(0).valid() &&
                    results.get(0).statusMessage().equals("404 NOT_FOUND"))
                .verifyComplete();

        // Verify that the services were called again
        verify(moodleApiService, times(2)).getCourseIds();
        verify(linkValidatorService, times(2)).validateLinks(any());
    }

    @Test
    void getLinkValidationResults_shouldFetchFreshResults_whenCacheIsInvalid() {
        // Arrange
        when(moodleApiService.getCourseIds()).thenReturn(Flux.just(1));
        when(moodleApiService.extractAssignmentLinks(any())).thenReturn(Flux.just(
                new LinkValidationResult("https://example.com", false, "Course1", "Task1", "")
        ));
        when(linkValidatorService.validateLinks(any())).thenReturn(Flux.just(
                new LinkValidationResult("https://example.com", true, "Course1", "Task1", "200 OK")
        ));

        // Act & Assert - First call should fetch results
        StepVerifier.create(linkCacheService.getLinkValidationResults(false))
                .expectNextMatches(results -> results.size() == 1)
                .verifyComplete();

        // Verify that the services were called
        verify(moodleApiService).getCourseIds();
        verify(linkValidatorService).validateLinks(any());
    }

    @Test
    void getLastUpdateTime_shouldReturnNull_whenCacheIsEmpty() {
        // Act
        LocalDateTime lastUpdateTime = linkCacheService.getLastUpdateTime();

        // Assert
        assertNull(lastUpdateTime);
    }

    @Test
    void getLastUpdateTime_shouldReturnTime_whenCacheIsPopulated() {
        // Arrange
        when(moodleApiService.getCourseIds()).thenReturn(Flux.just(1));
        when(moodleApiService.extractAssignmentLinks(any())).thenReturn(Flux.just(
                new LinkValidationResult("https://example.com", false, "Course1", "Task1", "")
        ));
        when(linkValidatorService.validateLinks(any())).thenReturn(Flux.just(
                new LinkValidationResult("https://example.com", true, "Course1", "Task1", "200 OK")
        ));

        // Populate cache
        StepVerifier.create(linkCacheService.getLinkValidationResults(true))
                .expectNextCount(1)
                .verifyComplete();

        // Act
        LocalDateTime lastUpdateTime = linkCacheService.getLastUpdateTime();

        // Assert
        assertNotNull(lastUpdateTime);
        assertTrue(lastUpdateTime.isBefore(LocalDateTime.now().plusSeconds(1)));
        assertTrue(lastUpdateTime.isAfter(LocalDateTime.now().minusMinutes(1)));
    }
}