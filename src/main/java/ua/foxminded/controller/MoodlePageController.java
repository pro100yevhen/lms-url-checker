package ua.foxminded.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import reactor.core.publisher.Mono;
import ua.foxminded.service.LinkValidatorService;
import ua.foxminded.service.MoodleApiService;

@Controller
public class MoodlePageController {

    private final MoodleApiService moodleService;
    private final LinkValidatorService linkValidatorService;

    public MoodlePageController(final MoodleApiService moodleService,
                                final LinkValidatorService linkValidatorService) {
        this.moodleService = moodleService;
        this.linkValidatorService = linkValidatorService;
    }

    @GetMapping
    public Mono<String> showLinks(final Model model) {
        final String validationResultsKey = "validationResults";
        final String linksKey = "links";
        return moodleService.getCourseIds()
                .transform(moodleService::extractAssignmentLinks)
                .transform(linkValidatorService::validateLinks)
                .filter(result -> !result.valid())
                .collectList()
                .doOnNext(results -> model.addAttribute(validationResultsKey, results))
                .thenReturn(linksKey);
    }
}
