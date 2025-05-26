package ua.foxminded.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ua.foxminded.model.LinkValidationResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class LinkCacheService {

    private final MoodleApiService moodleApiService;
    private final LinkValidatorService linkValidatorService;
    private final long cacheDurationHours;
    
    private final AtomicReference<List<LinkValidationResult>> cachedResults = new AtomicReference<>();
    private final AtomicReference<LocalDateTime> lastUpdateTime = new AtomicReference<>();

    public LinkCacheService(
            final MoodleApiService moodleApiService,
            final LinkValidatorService linkValidatorService,
            @Value("${link.checker.cache.duration-hours:24}") final long cacheDurationHours
    ) {
        this.moodleApiService = moodleApiService;
        this.linkValidatorService = linkValidatorService;
        this.cacheDurationHours = cacheDurationHours;
        log.info("Link cache initialized with duration of {} hours", cacheDurationHours);
    }

    public Mono<List<LinkValidationResult>> getLinkValidationResults(final boolean forceRefresh) {
        if (!forceRefresh && isCacheValid()) {
            log.info("Returning cached link validation results");
            return Mono.just(cachedResults.get());
        }
        
        log.info("Fetching fresh link validation results");
        return fetchFreshResults()
                .doOnSuccess(results -> {
                    cachedResults.set(results);
                    lastUpdateTime.set(LocalDateTime.now());
                    log.info("Cache updated with {} links", results.size());
                });
    }

    private boolean isCacheValid() {
        final List<LinkValidationResult> results = cachedResults.get();
        final LocalDateTime lastUpdate = lastUpdateTime.get();
        
        if (results == null || lastUpdate == null) {
            return false;
        }
        
        final LocalDateTime expirationTime = lastUpdate.plusHours(cacheDurationHours);
        return LocalDateTime.now().isBefore(expirationTime);
    }

    private Mono<List<LinkValidationResult>> fetchFreshResults() {
        return moodleApiService.getCourseIds()
                .transform(moodleApiService::extractAssignmentLinks)
                .transform(linkValidatorService::validateLinks)
                .collectList()
                .doOnError(e -> log.error("Error fetching link validation results: {}", e.getMessage()));
    }

    public LocalDateTime getLastUpdateTime() {
        return lastUpdateTime.get();
    }
}