package ua.foxminded.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;
import ua.foxminded.service.LinkCacheService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Controller
public class MoodlePageController {

    private final LinkCacheService linkCacheService;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public MoodlePageController(final LinkCacheService linkCacheService) {
        this.linkCacheService = linkCacheService;
    }

    @GetMapping
    public Mono<String> showLinks(
            final Model model,
            @RequestParam(value = "refresh", required = false, defaultValue = "false") final boolean refresh) {

        final String brokenLinksKey = "brokenLinks";
        final String allLinksKey = "allLinks";
        final String showAllLinksKey = "showAllLinks";
        final String lastUpdateKey = "lastUpdate";
        final String linksKey = "links";

        return linkCacheService.getLinkValidationResults(refresh)
                .flatMap(allResults -> {
                    model.addAttribute(allLinksKey, allResults);

                    final var brokenLinks = allResults.stream()
                            .filter(result -> !result.valid())
                            .toList();
                    model.addAttribute(brokenLinksKey, brokenLinks);
                    model.addAttribute(showAllLinksKey, false);
                    addLastUpdateTimeToModel(model, lastUpdateKey);

                    return Mono.just(linksKey);
                });
    }

    @GetMapping("/all")
    public Mono<String> showAllLinks(
            final Model model,
            @RequestParam(value = "refresh", required = false, defaultValue = "false") final boolean refresh) {

        final String brokenLinksKey = "brokenLinks";
        final String allLinksKey = "allLinks";
        final String showAllLinksKey = "showAllLinks";
        final String lastUpdateKey = "lastUpdate";
        final String linksKey = "links";

        return linkCacheService.getLinkValidationResults(refresh)
                .flatMap(allResults -> {
                    model.addAttribute(allLinksKey, allResults);

                    final var brokenLinks = allResults.stream()
                            .filter(result -> !result.valid())
                            .toList();

                    model.addAttribute(brokenLinksKey, brokenLinks);
                    model.addAttribute(showAllLinksKey, true);
                    addLastUpdateTimeToModel(model, lastUpdateKey);

                    return Mono.just(linksKey);
                });
    }

    private void addLastUpdateTimeToModel(final Model model, final String lastUpdateKey) {
        final LocalDateTime lastUpdate = linkCacheService.getLastUpdateTime();
        if (lastUpdate != null) {
            model.addAttribute(lastUpdateKey, "Last updated: " + lastUpdate.format(FORMATTER));
        } else {
            model.addAttribute(lastUpdateKey, "");
        }
    }
}
