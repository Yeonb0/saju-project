package com.sajuppugi.gift.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.gift.phone-cleanup-enabled", havingValue = "true")
public class GiftPhoneCleanupWorker {
    private static final Logger log = LoggerFactory.getLogger(GiftPhoneCleanupWorker.class);
    private final JdbcGiftPhoneCleanup cleanup;
    public GiftPhoneCleanupWorker(JdbcGiftPhoneCleanup cleanup) { this.cleanup = cleanup; }
    @Scheduled(fixedDelayString = "${app.gift.phone-cleanup-poll-delay:60000}", initialDelayString = "${app.gift.phone-cleanup-poll-delay:60000}")
    public void purge() {
        for (var order : cleanup.candidates()) {
            try { cleanup.purge(order); }
            catch (RuntimeException failure) {
                log.warn("Gift phone cleanup failed: orderId={}, type={}", order, failure.getClass().getSimpleName());
                try { cleanup.failed(order); }
                catch (RuntimeException persistence) { log.warn("Gift phone cleanup retry record failed: orderId={}", order); }
            }
        }
    }
}
