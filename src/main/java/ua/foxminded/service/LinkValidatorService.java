package ua.foxminded.service;

import io.netty.channel.ChannelOption;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;
import ua.foxminded.model.LinkValidationResult;
import ua.foxminded.model.ValidationReport;

import java.time.Duration;
import java.util.List;

@Service
public class LinkValidatorService {

    private final WebClient webClient;
    private final int maxConcurrentRequests = 10;

    public LinkValidatorService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create()
                                .responseTimeout(Duration.ofSeconds(30))
                                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
                ))
                .build();
    }

    public Mono<String> validateLinks(List<String> links) {
        int chunkSize = 100;

        return Flux.fromIterable(links)
                .buffer(chunkSize)
                .flatMap(this::validateChunk, maxConcurrentRequests)
                .reduceWith(ValidationReport::new, ValidationReport::merge)
                .map(ValidationReport::generateSummary);
    }

    private Mono<ValidationReport> validateChunk(List<String> chunk) {
        return Flux.fromIterable(chunk)
                .flatMap(link -> checkLink(link).delayElement(Duration.ofMillis(500)), maxConcurrentRequests)
                .collectList()
                .map(ValidationReport::fromResults);
    }

    private Mono<LinkValidationResult> checkLink(String link) {
        if (!link.startsWith("http://") && !link.startsWith("https://")) {
            return Mono.empty();
        }
        return webClient.head()
                .uri(link)
                .retrieve()
                .toBodilessEntity()
                .flatMap(response -> {
                    if (response.getStatusCode().is3xxRedirection() && response.getHeaders().getLocation() != null) {
                        String redirectedLink = response.getHeaders().getLocation().toString();
                        return checkLink(redirectedLink);
                    }
                    boolean isValid = response.getStatusCode().is2xxSuccessful();
                    return Mono.just(new LinkValidationResult(link, isValid));
                })
                .onErrorResume(e -> {
                    System.out.println("Error checking link: " + link + " - " + e.getMessage());
                    return Mono.just(new LinkValidationResult(link, false));
                })
                .timeout(Duration.ofSeconds(30));
    }
}