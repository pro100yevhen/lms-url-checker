package ua.foxminded.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;
import reactor.core.scheduler.Schedulers;
import ua.foxminded.model.LinkValidationResult;

import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import java.io.IOException;
import java.net.ConnectException;

@Service
public class LinkValidatorService {

    private static final HttpHeaders HEADERS = new HttpHeaders();
    private static final Pattern IP_PATTERN = Pattern.compile(":\\d{1,5}$");
    private static final int MAX_REDIRECTS = 5;
    private static final int TIMEOUT_SECONDS = 30;
    private static final int MAX_CONCURRENT_REQUESTS = 10;

    static {
        HEADERS.add(HttpHeaders.USER_AGENT,
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        HEADERS.add(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
        HEADERS.add(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9");
        HEADERS.add(HttpHeaders.CACHE_CONTROL, "no-cache");
        HEADERS.add(HttpHeaders.PRAGMA, "no-cache");
    }

    private final WebClient webClient;
    private final Logger log = LoggerFactory.getLogger(LinkValidatorService.class);

    public LinkValidatorService(final WebClient.Builder webClientBuilder) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .followRedirect(true)
                .headers(headers -> headers.add("Connection", "keep-alive"));

        this.webClient = webClientBuilder
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeaders(headers -> headers.addAll(HEADERS))
                .build();
    }

    public Flux<LinkValidationResult> validateLinks(final List<LinkValidationResult> links) {
        return Flux.fromIterable(links)
                .parallel(MAX_CONCURRENT_REQUESTS)
                .runOn(Schedulers.boundedElastic())
                .flatMap(this::checkLink)
                .sequential();
    }

    private Mono<LinkValidationResult> checkLink(final LinkValidationResult linkValidationResult) {
        return checkLink(linkValidationResult.getLink(), linkValidationResult.getCourseName(),
                linkValidationResult.getTaskName(), 0)
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .onErrorResume(e -> handleError(linkValidationResult, e));
    }

    private Mono<LinkValidationResult> checkLink(final String link, final String courseName,
                                                 final String taskName, final int redirectionDepth) {
        if (redirectionDepth > MAX_REDIRECTS) {
            return Mono.just(new LinkValidationResult(link, false, courseName, taskName,
                    "Too many redirects (max " + MAX_REDIRECTS + " allowed)"));
        }

        if (!isValidUrl(link)) {
            return Mono.just(new LinkValidationResult(link, false, courseName, taskName,
                    "Invalid URL format"));
        }

        return webClient.get()
                .uri(link)
                .retrieve()
                .toBodilessEntity()
                .flatMap(response -> handleResponse(response.getStatusCode(), link, courseName, taskName))
                .onErrorResume(e -> handleError(new LinkValidationResult(link, false, courseName, taskName, ""), e));
    }

    private Mono<LinkValidationResult> handleResponse(HttpStatusCode statusCode, String link,
                                                      String courseName, String taskName) {
        String statusMessage = getStatusMessage(statusCode);
        boolean isValid = statusCode.is2xxSuccessful();
        return Mono.just(new LinkValidationResult(link, isValid, courseName, taskName, statusMessage));
    }

    private String getStatusMessage(HttpStatusCode statusCode) {
        return switch (statusCode.value()) {
            case 200 -> "OK";
            case 403 -> "Access Forbidden - The server understood the request but refuses to authorize it";
            case 404 -> "Page Not Found";
            case 429 -> "Too Many Requests - Rate limit exceeded";
            case 500 -> "Internal Server Error";
            case 503 -> "Service Unavailable - The server is temporarily unable to handle the request";
            default -> "HTTP " + statusCode.value() + " - " + statusCode.toString();
        };
    }

    private Mono<LinkValidationResult> handleError(LinkValidationResult result, Throwable error) {
        String errorMessage = formatErrorMessage(error);
        log.debug("Error validating link {}: {}", result.getLink(), errorMessage);
        return Mono.just(new LinkValidationResult(result.getLink(), false, result.getCourseName(),
                result.getTaskName(), errorMessage));
    }

    private String formatErrorMessage(Throwable error) {
        if (error instanceof TimeoutException) {
            return "Connection timed out after " + TIMEOUT_SECONDS + " seconds";
        } else if (error instanceof ConnectException) {
            return "Connection failed - Server is unreachable";
        } else if (error instanceof UnknownHostException) {
            return "Unknown host - Domain cannot be resolved";
        } else if (error instanceof IOException) {
            return "Network error - " + cleanErrorMessage(error.getMessage());
        }
        return cleanErrorMessage(error.getMessage());
    }

    private String cleanErrorMessage(String message) {
        if (message == null) {
            return "Unknown error";
        }
        // Remove IP addresses and ports from error messages
        return IP_PATTERN.matcher(message).replaceAll("");
    }

    private boolean isValidUrl(String url) {
        try {
            new URI(url);
            return url.startsWith("http://") || url.startsWith("https://");
        } catch (Exception e) {
            return false;
        }
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