package com.sajuppugi.gift.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.gift.retention-enabled", havingValue = "true")
public class GiftPrivacyWorker {
    private static final Logger log = LoggerFactory.getLogger(GiftPrivacyWorker.class);
    private final JdbcGiftPrivacy privacy;
    public GiftPrivacyWorker(JdbcGiftPrivacy privacy) { this.privacy = privacy; }
    @Scheduled(fixedDelayString = "${app.gift.retention-poll-delay:60000}", initialDelayString = "${app.gift.retention-poll-delay:60000}")
    public void purge() {
        for (var id : privacy.candidates()) {
            try { privacy.purge(id); }
            catch (RuntimeException failure) {
                log.warn("Gift privacy cleanup failed: orderId={}, type={}", id, failure.getClass().getSimpleName());
                try { privacy.failed(id); }
                catch (RuntimeException persistence) { log.warn("Gift privacy retry record failed: orderId={}", id); }
            }
        }
    }
}
