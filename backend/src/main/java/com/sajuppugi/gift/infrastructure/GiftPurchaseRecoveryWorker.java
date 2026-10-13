package com.sajuppugi.gift.infrastructure;

import com.sajuppugi.gift.application.GiftPurchaseService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.gift.recovery-enabled", havingValue = "true")
public class GiftPurchaseRecoveryWorker {
    private final GiftPurchaseService purchases;
    private final com.sajuppugi.gift.application.GiftCancellationService cancellations;
    public GiftPurchaseRecoveryWorker(GiftPurchaseService purchases,
            com.sajuppugi.gift.application.GiftCancellationService cancellations) {
        this.purchases = purchases; this.cancellations = cancellations;
    }
    @Scheduled(fixedDelayString = "${app.gift.recovery.interval-ms:30000}",
            initialDelayString = "${app.gift.recovery.interval-ms:30000}")
    public void recover() { purchases.recoverDue(); cancellations.recoverDue(); }
}
