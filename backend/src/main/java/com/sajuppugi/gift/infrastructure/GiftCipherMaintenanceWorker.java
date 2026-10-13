package com.sajuppugi.gift.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.gift.payload-reencryption-enabled", havingValue = "true")
public class GiftCipherMaintenanceWorker {
    private static final Logger log = LoggerFactory.getLogger(GiftCipherMaintenanceWorker.class);
    private final JdbcGiftCipherMaintenance maintenance;
    public GiftCipherMaintenanceWorker(JdbcGiftCipherMaintenance maintenance, GiftPayloadCipher cipher) {
        this.maintenance = maintenance;
        if (!cipher.versionedActive()) throw new IllegalStateException("Gift re-encryption requires a versioned active key");
    }
    @Scheduled(fixedDelayString = "${app.gift.payload-reencryption-poll-delay:30000}", initialDelayString = "${app.gift.payload-reencryption-poll-delay:30000}")
    public void reencrypt() {
        for (var id : maintenance.candidates()) {
            try { maintenance.reencrypt(id); }
            catch (RuntimeException failure) {
                try { maintenance.failed(id); }
                catch (RuntimeException persistence) { log.warn("Gift re-encryption recovery persistence failed: orderId={}, type={}", id, persistence.getClass().getSimpleName()); }
                log.warn("Gift re-encryption failed: orderId={}, type={}", id, failure.getClass().getSimpleName());
            }
        }
    }
}
