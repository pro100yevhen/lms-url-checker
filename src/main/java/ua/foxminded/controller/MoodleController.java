package ua.foxminded.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ua.foxminded.service.LinkValidatorService;
import ua.foxminded.service.MoodleApiService;

@RestController
@RequestMapping("/api/moodle")
public class MoodleController {

    private final MoodleApiService moodleService;
    private final LinkValidatorService linkValidatorService;

    private final static String GET_COURSES_FUNCTION = "core_course_get_courses";

    public MoodleController(final MoodleApiService moodleService,
                            final LinkValidatorService linkValidatorService) {
        this.moodleService = moodleService;
        this.linkValidatorService = linkValidatorService;
    }

    @PostMapping("/check-links")
    public Mono<String> checkAllLinks() {
        final String moodleFunction = GET_COURSES_FUNCTION;

        final Flux<Integer> courseIds = moodleService.getCourseIds(moodleFunction);

        return moodleService.extractAssignmentLinks(courseIds)
                .flatMap(Flux::fromIterable)
                .distinct()
                .collectList()
                .flatMap(linkValidatorService::validateLinks);
    }
}