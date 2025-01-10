package ua.foxminded.service;

import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;
import reactor.core.scheduler.Schedulers;
import ua.foxminded.model.LinkValidationResult;

import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class LinkValidatorService {

    private static final Pattern IP_PATTERN = Pattern.compile(":\\d{1,5}$");
    private static final int MAX_REDIRECTS = 5;
    private static final int TIMEOUT_SECONDS = 30;
    private static final int MAX_CONCURRENT_REQUESTS = 10;

    private final WebClient webClient;

    public LinkValidatorService(final WebClient.Builder webClientBuilder) {
        final HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .followRedirect(true);

        this.webClient = webClientBuilder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    public Flux<LinkValidationResult> validateLinks(final List<LinkValidationResult> links) {
        return Flux.fromIterable(links)
                .parallel(MAX_CONCURRENT_REQUESTS)
                .runOn(Schedulers.parallel())
                .flatMap(this::checkLink)
                .sequential();
    }

    private Mono<LinkValidationResult> checkLink(final LinkValidationResult linkValidationResult) {
        return checkLink(linkValidationResult.getLink(), linkValidationResult.getCourseName(),
                linkValidationResult.getTaskName(), 0)
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .onErrorResume(e -> Mono.just(new LinkValidationResult(
                        linkValidationResult.getLink(), false, linkValidationResult.getCourseName(),
                        linkValidationResult.getTaskName(), cleanErrorMessage(e.getMessage()))));
    }

    private Mono<LinkValidationResult> checkLink(final String link, final String courseName,
                                                 final String taskName, final int redirectionDepth) {
        if (redirectionDepth > MAX_REDIRECTS) {
            return Mono.just(new LinkValidationResult(link, false, courseName, taskName,
                    "Too many redirects (max " + MAX_REDIRECTS + " allowed)"));
        }

        return webClient.get()
                .uri(link)
                .retrieve()
                .toBodilessEntity()
                .flatMap(response -> handleResponse(response, link, courseName, taskName));
    }

    private Mono<LinkValidationResult> handleResponse(final ResponseEntity response, final String link,
                                                      final String courseName, final String taskName) {
        final boolean isValid = response.getStatusCode().is2xxSuccessful();
        return Mono.just(
                new LinkValidationResult(link, isValid, courseName, taskName, response.getStatusCode().toString()));
    }

    private String cleanErrorMessage(final String message) {
        return IP_PATTERN.matcher(message).replaceAll("");
    }
}