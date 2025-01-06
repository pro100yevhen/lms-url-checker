package ua.foxminded.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ua.foxminded.model.LinkValidationResult;
import ua.foxminded.service.LinkValidatorService;
import ua.foxminded.service.MoodleApiService;

import java.util.List;

@RestController
@RequestMapping("/api/moodle")
public class MoodleController {

    private final MoodleApiService moodleService;
    private final LinkValidatorService linkValidatorService;

    public MoodleController(final MoodleApiService moodleService,
                            final LinkValidatorService linkValidatorService) {
        this.moodleService = moodleService;
        this.linkValidatorService = linkValidatorService;
    }

    @PostMapping("/check-links")
    public Mono<List<LinkValidationResult>> checkAllLinks() {
        final Flux<Integer> courseIds = moodleService.getCourseIds();

        return moodleService.extractAssignmentLinks(courseIds)
                .distinct()
                .collectList()
                .flatMap(linkValidatorService::validateLinks);
    }
}
