package com.sajuppugi.gift.application;

import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Bounded removal of pseudonymous public-request buckets; never logs subject digests. */
@Component
@ConditionalOnProperty(name = "app.gift.public-rate-limit-cleanup-enabled", havingValue = "true", matchIfMissing = true)
public class PublicGiftRateLimitCleanupWorker {
    private static final int BATCH_SIZE = 1_000;
    private static final Duration RETENTION = Duration.ofHours(2);

    private final PublicGiftRateLimiter limits;
    private final Clock clock;

    public PublicGiftRateLimitCleanupWorker(PublicGiftRateLimiter limits, Clock clock) {
        this.limits = limits;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.gift.public-rate-limit-cleanup-delay:1h}")
    public void purge() {
        limits.purgeOlderThan(clock.instant().minus(RETENTION), BATCH_SIZE);
    }
}
