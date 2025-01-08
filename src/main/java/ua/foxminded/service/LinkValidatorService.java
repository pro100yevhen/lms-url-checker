package ua.foxminded.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;
import reactor.core.scheduler.Schedulers;
import ua.foxminded.model.LinkValidationResult;

import java.net.URI;
import java.time.Duration;
import java.util.List;

@Service
public class LinkValidatorService {

    private final WebClient webClient;
    private final int maxConcurrentRequests = 10;
    private final Logger log = LoggerFactory.getLogger(LinkValidatorService.class);

    public LinkValidatorService(final WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create()
                ))
                .build();
    }

    public Mono<List<LinkValidationResult>> validateLinks(final List<LinkValidationResult> links) {
        return Flux.fromIterable(links)
                .parallel(maxConcurrentRequests)
                .runOn(Schedulers.boundedElastic())
                .flatMap(this::checkLink)
                .sequential()
                .collectList();
    }

    private Mono<LinkValidationResult> checkLink(final LinkValidationResult linkValidationResult) {
        final int timeoutInSeconds = 100;
        return checkLink(linkValidationResult.getLink(), linkValidationResult.getCourseName(),
                linkValidationResult.getTaskName(), 0)
                .timeout(Duration.ofSeconds(timeoutInSeconds));
    }

    private Mono<LinkValidationResult> checkLink(final String link, final String courseName,
                                                 final String taskName, final int redirectionDepth) {
        if (redirectionDepth > 3) {
            return Mono.just(new LinkValidationResult(link, false, courseName, taskName, "Too many redirects"));
        }

        if (!link.startsWith("http://") && !link.startsWith("https://")) {
            log.info("Skipping non-HTTP(S) link: {}", link);
            return Mono.just(new LinkValidationResult(link, false, courseName, taskName, "Invalid scheme"));
        }

        return webClient.get()
                .uri(link)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.82 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .retrieve()
                .toBodilessEntity()
                .flatMap(response -> {
                    if (response.getStatusCode().is3xxRedirection() && response.getHeaders().getLocation() != null) {
                        String redirectedLink = response.getHeaders().getLocation().toString();

                        // Ensure redirectedLink is properly constructed
                        if (!redirectedLink.startsWith("http://") && !redirectedLink.startsWith("https://")) {
                            redirectedLink = resolveRelativeUrl(link, redirectedLink);
                        }

                        return checkLink(redirectedLink, courseName, taskName, redirectionDepth + 1);
                    }
                    final boolean isValid = response.getStatusCode().is2xxSuccessful();
                    final String statusMessage = "It works fine";
                    return Mono.just(new LinkValidationResult(link, isValid, courseName, taskName, statusMessage));
                })
                .onErrorResume(
                        e -> Mono.just(new LinkValidationResult(link, false, courseName, taskName, e.getMessage())));
    }

    private String resolveRelativeUrl(final String baseUrl, final String relativeUrl) {
        try {
            return new URI(baseUrl).resolve(relativeUrl).toString();
        } catch (final Exception e) {
            log.error("Error resolving relative URL: base={}, relative={}", baseUrl, relativeUrl, e);
            return relativeUrl;
        }
    }
}