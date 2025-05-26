package ua.foxminded.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.Model;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ua.foxminded.model.LinkValidationResult;
import ua.foxminded.service.LinkCacheService;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MoodlePageControllerTest {

    @Mock
    private LinkCacheService linkCacheService;

    @Mock
    private Model model;

    @InjectMocks
    private MoodlePageController controller;

    private List<LinkValidationResult> testResults;
    private LocalDateTime testUpdateTime;

    @BeforeEach
    void setUp() {
        testResults = List.of(
                new LinkValidationResult("https://valid.com", true, "Course1", "Task1", "200 OK"),
                new LinkValidationResult("https://invalid.com", false, "Course1", "Task2", "404 NOT_FOUND")
        );
        
        testUpdateTime = LocalDateTime.now();
        
        when(linkCacheService.getLinkValidationResults(anyBoolean())).thenReturn(Mono.just(testResults));
        when(linkCacheService.getLastUpdateTime()).thenReturn(testUpdateTime);
    }

    @Test
    void showLinks_shouldAddAllLinksAndBrokenLinksToModel() {
        // Arrange
        when(model.addAttribute(anyString(), any())).thenReturn(model);

        // Act
        Mono<String> result = controller.showLinks(model, false);

        // Assert
        StepVerifier.create(result)
                .expectNext("links")
                .verifyComplete();

        verify(model).addAttribute("allLinks", testResults);
        verify(model).addAttribute(eq("brokenLinks"), argThat(list -> 
            ((List<LinkValidationResult>) list).size() == 1 && 
            ((List<LinkValidationResult>) list).get(0).link().equals("https://invalid.com")
        ));
        verify(model).addAttribute("showAllLinks", false);
        verify(model).addAttribute(eq("lastUpdate"), contains("Last updated:"));
    }

    @Test
    void showLinks_shouldRefreshCache_whenRefreshIsTrue() {
        // Arrange
        when(model.addAttribute(anyString(), any())).thenReturn(model);

        // Act
        controller.showLinks(model, true).block();

        // Assert
        verify(linkCacheService).getLinkValidationResults(true);
    }

    @Test
    void showAllLinks_shouldAddAllLinksAndBrokenLinksToModel() {
        // Arrange
        when(model.addAttribute(anyString(), any())).thenReturn(model);

        // Act
        Mono<String> result = controller.showAllLinks(model, false);

        // Assert
        StepVerifier.create(result)
                .expectNext("links")
                .verifyComplete();

        verify(model).addAttribute("allLinks", testResults);
        verify(model).addAttribute(eq("brokenLinks"), argThat(list -> 
            ((List<LinkValidationResult>) list).size() == 1 && 
            ((List<LinkValidationResult>) list).get(0).link().equals("https://invalid.com")
        ));
        verify(model).addAttribute("showAllLinks", true);
        verify(model).addAttribute(eq("lastUpdate"), contains("Last updated:"));
    }

    @Test
    void showAllLinks_shouldRefreshCache_whenRefreshIsTrue() {
        // Arrange
        when(model.addAttribute(anyString(), any())).thenReturn(model);

        // Act
        controller.showAllLinks(model, true).block();

        // Assert
        verify(linkCacheService).getLinkValidationResults(true);
    }

    @Test
    void addLastUpdateTimeToModel_shouldAddFormattedTime_whenTimeExists() {
        // Arrange
        when(model.addAttribute(anyString(), any())).thenReturn(model);
        
        // Act
        controller.showLinks(model, false).block();
        
        // Assert
        verify(model).addAttribute(eq("lastUpdate"), contains("Last updated:"));
    }

    @Test
    void addLastUpdateTimeToModel_shouldAddEmptyString_whenTimeIsNull() {
        // Arrange
        when(model.addAttribute(anyString(), any())).thenReturn(model);
        when(linkCacheService.getLastUpdateTime()).thenReturn(null);
        
        // Act
        controller.showLinks(model, false).block();
        
        // Assert
        verify(model).addAttribute("lastUpdate", "");
    }

    private static String contains(String substring) {
        return argThat(arg -> ((String) arg).contains(substring));
    }
}