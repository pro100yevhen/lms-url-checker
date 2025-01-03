package ua.foxminded.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ua.foxminded.service.LinkValidatorService;
import ua.foxminded.service.MoodleApiService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    public Mono<String> checkAllLinks() {
        final String moodleFunction = "core_course_get_courses";

        // Retrieve the course IDs
        final Flux<Integer> courseIds = moodleService.getCourseIds(moodleFunction);

        // Extract links and validate them asynchronously
        return moodleService.extractAssignmentLinks(courseIds)
                .flatMap(Flux::fromIterable)
                .distinct()
                .collectList()  // Collect all links into a list for link validation
                .flatMap(linkValidatorService::validateLinks);  // Validate links asynchronously
    }
}