package ua.foxminded.service;

import io.netty.channel.ChannelOption;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.List;

@Service
public class LinkValidatorService {

    private final WebClient webClient;

    public LinkValidatorService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.clientConnector(new ReactorClientHttpConnector(
                        HttpClient.create()
                                .responseTimeout(Duration.ofSeconds(30))  // Increase response timeout
                                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000) // Connection timeout
                ))
                .build();
    }

    public Mono<String> validateLinks(List<String> links) {
        int chunkSize = 100; // Process in manageable batches
        int parallelism = 5; // Limit concurrency to avoid resource exhaustion

        return Flux.fromIterable(links)
                .buffer(chunkSize) // Split into chunks
                .flatMap(this::validateChunk, parallelism) // Process chunks concurrently
                .reduceWith(ValidationReport::new, ValidationReport::merge) // Aggregate results
                .map(ValidationReport::generateSummary); // Generate final report
    }

    private Mono<ValidationReport> validateChunk(List<String> chunk) {
        return Flux.fromIterable(chunk)
                .flatMap(this::checkLink, 10) // Limit concurrency per chunk
                .collectList()
                .map(ValidationReport::fromResults);
    }

    private Mono<LinkValidationResult> checkLink(String link) {
        return webClient.head()
                .uri(link)
                .retrieve()
                .toBodilessEntity()
                .flatMap(response -> {
                    if (response.getStatusCode().is3xxRedirection() && response.getHeaders().getLocation() != null) {
                        String redirectedLink = response.getHeaders().getLocation().toString();
                        return checkLink(redirectedLink); // Follow redirection
                    }
                    boolean isValid = response.getStatusCode().is2xxSuccessful();
                    return Mono.just(new LinkValidationResult(link, isValid));
                })
                .onErrorResume(e -> {
                    System.out.println("Error checking link: " + link + " - " + e.getMessage());
                    return Mono.just(new LinkValidationResult(link, false)); // Handle errors as invalid link
                })
                .timeout(Duration.ofSeconds(10)) // Add timeout to avoid hanging requests
                .retry(2); // Retry on failure (max 2 retries)
    }

    private static class ValidationReport {
        private long validCount;
        private long invalidCount;
        private final StringBuilder invalidLinks;

        public ValidationReport() {
            this.validCount = 0;
            this.invalidCount = 0;
            this.invalidLinks = new StringBuilder();
        }

        public static ValidationReport fromResults(List<LinkValidationResult> results) {
            ValidationReport report = new ValidationReport();
            results.forEach(result -> {
                if (result.isValid()) {
                    report.validCount++;
                } else {
                    report.invalidCount++;
                    report.invalidLinks.append(result.getLink()).append("\n");
                }
            });
            return report;
        }

        public ValidationReport merge(ValidationReport other) {
            this.validCount += other.validCount;
            this.invalidCount += other.invalidCount;
            this.invalidLinks.append(other.invalidLinks);
            return this;
        }

        public String generateSummary() {
            return String.format(
                    "Checked links: %d\nValid links: %d\nInvalid links: %d\nInvalid links list:\n%s",
                    validCount + invalidCount, validCount, invalidCount,
                    invalidLinks.length() > 0 ? invalidLinks.toString() : "No invalid links"
            );
        }
    }
    private static class LinkValidationResult {
        private final String link;
        private final boolean valid;

        public LinkValidationResult(String link, boolean valid) {
            this.link = link;
            this.valid = valid;
        }

        public String getLink() {
            return link;
        }

        public boolean isValid() {
            return valid;
        }
    }
}