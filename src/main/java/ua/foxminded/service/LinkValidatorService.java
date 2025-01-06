package ua.foxminded.service;

import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;
import ua.foxminded.model.LinkValidationResult;

import java.time.Duration;
import java.util.List;

@Service
public class LinkValidatorService {

    private final WebClient webClient;
    private final int maxConcurrentRequests = 10;

    public LinkValidatorService(final WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create()
                ))
                .build();
    }

    public Mono<List<LinkValidationResult>> validateLinks(final List<LinkValidationResult> links) {
        return Flux.fromIterable(links)
                .buffer(100)  // Process links in batches of 100
                .flatMap(this::validateChunk, maxConcurrentRequests)
                .flatMap(Flux::fromIterable)  // Flatten the list of lists into a single Flux
                .collectList();  // Collect all results in a single list
    }

    private Mono<List<LinkValidationResult>> validateChunk(final List<LinkValidationResult> chunk) {
        return Flux.fromIterable(chunk)
                .flatMap(link -> checkLink(link.getLink(), link.getCourseName(), link.getTaskName()),
                        maxConcurrentRequests)
                .collectList();
    }

    private Mono<LinkValidationResult> checkLink(final String link, final String courseName, final String taskName) {
        final int timeoutInSeconds = 30;
        return webClient.head()
                .uri(link)
                .retrieve()
                .toBodilessEntity()
                .flatMap(response -> {
                    if (response.getStatusCode().is3xxRedirection() && response.getHeaders().getLocation() != null) {
                        final String redirectedLink = response.getHeaders().getLocation().toString();
                        return checkLink(redirectedLink, courseName, taskName);
                    }
                    final boolean isValid = response.getStatusCode().is2xxSuccessful();
                    return Mono.just(
                            new LinkValidationResult(link, isValid, courseName, taskName));
                })
                .onErrorResume(e -> Mono.just(
                        new LinkValidationResult(link, false, courseName, taskName)))
                .timeout(Duration.ofSeconds(timeoutInSeconds));
    }
}