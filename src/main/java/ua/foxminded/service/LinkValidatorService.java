package ua.foxminded.service;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;
import ua.foxminded.model.LinkValidationResult;

import javax.net.ssl.SSLException;
import java.time.Duration;
import java.util.regex.Pattern;

@Slf4j
@Service
public class LinkValidatorService {

    private static final int MAX_REDIRECTS = 5;
    private static final int TIMEOUT_SECONDS = 30;
    private final int timeoutSeconds;
    private final int parallelism;

    private final WebClient webClient;

    public LinkValidatorService(
            final WebClient.Builder webClientBuilder,
            @Value("${link.checker.timeout}") final int timeoutSeconds,
            @Value("${link.checker.parallelism}") final int parallelism
    ) {
        this.timeoutSeconds = timeoutSeconds;
        this.parallelism = parallelism;

        System.setProperty("java.net.preferIPv4Stack", "true");

        final SslContext sslContext;
        try {
            sslContext = SslContextBuilder
                    .forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build();
        } catch (final SSLException e) {
            log.error("Error creating SSL context: {}", e.getMessage());
            throw new RuntimeException("Failed to create SSL context", e);
        }

        final HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(timeoutSeconds))
                .followRedirect(true)
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutSeconds * 1000)
                .secure(sslContextSpec -> sslContextSpec.sslContext(sslContext));

        this.webClient = webClientBuilder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    public Flux<LinkValidationResult> validateLinks(final Flux<LinkValidationResult> links) {
        return links
                .distinct(LinkValidationResult::link)
                .parallel(parallelism)
                .runOn(Schedulers.boundedElastic())
                .flatMap(this::checkLink)
                .sequential()
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .onErrorResume(e -> {
                    log.error("Validation error: {}", e.getMessage());
                    return Flux.empty();
                });
    }

    private Mono<LinkValidationResult> checkLink(final LinkValidationResult linkValidationResult) {
        // Skip validation for Figma links and mark them as valid
        if (linkValidationResult.link().contains("figma.com")) {
            log.info("Skipping validation for Figma link: {}", linkValidationResult.link());
            return Mono.just(new LinkValidationResult(
                    linkValidationResult.link(), true, linkValidationResult.courseName(),
                    linkValidationResult.taskName(), "Figma link (validation skipped)"));
        }

        return checkLink(linkValidationResult.link(), linkValidationResult.courseName(),
                linkValidationResult.taskName(), 0)
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .onErrorResume(e -> Mono.just(new LinkValidationResult(
                        linkValidationResult.link(), false, linkValidationResult.courseName(),
                        linkValidationResult.taskName(), cleanErrorMessage(e.getMessage()))));
    }

    private Mono<LinkValidationResult> checkLink(final String link, final String courseName,
                                                 final String taskName, final int redirectionDepth) {
        if (redirectionDepth > MAX_REDIRECTS) {
            return Mono.just(new LinkValidationResult(link, false, courseName, taskName,
                    "Too many redirects (max " + MAX_REDIRECTS + " allowed)"));
        }

        final WebClient.RequestHeadersSpec<?> requestSpec = webClient.get().uri(link);

        return requestSpec
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
        final Pattern IP_PATTERN = Pattern.compile(":\\d{1,5}$");
        final String emptyStatusMessage = "";
        return IP_PATTERN.matcher(message).replaceAll(emptyStatusMessage);
    }
}
