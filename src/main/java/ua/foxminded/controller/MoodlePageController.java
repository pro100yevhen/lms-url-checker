package ua.foxminded.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import reactor.core.publisher.Mono;
import ua.foxminded.service.LinkValidatorService;
import ua.foxminded.service.MoodleApiService;

import java.util.stream.Collectors;

@Controller
@RequestMapping("/moodle")
public class MoodlePageController {

    private final MoodleApiService moodleService;
    private final LinkValidatorService linkValidatorService;

    public MoodlePageController(final MoodleApiService moodleService,
                                final LinkValidatorService linkValidatorService) {
        this.moodleService = moodleService;
        this.linkValidatorService = linkValidatorService;
    }

    @GetMapping("/links")
    public Mono<String> showLinks(final Model model) {
        return moodleService.extractAssignmentLinks(moodleService.getCourseIds())
                .collectList()
                .flatMapMany(linkValidatorService::validateLinks)
                .collectList()
                .map(results -> results.stream()
                        .filter(result -> !result.isValid())
                        .collect(Collectors.toList()))
                .doOnNext(sortedResults -> model.addAttribute("validationResults", sortedResults))
                .thenReturn("links");
    }
}
