package com.sajuppugi.fortune.reading.application;

import com.sajuppugi.fortune.reading.port.ReadingRecoveryRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = {"app.wallet.purchases-enabled", "app.wallet.recovery.enabled"}, havingValue = "true")
public class FailedPurchaseRecoveryScheduler {
    private static final Logger log = LoggerFactory.getLogger(FailedPurchaseRecoveryScheduler.class);
    private final ReadingRecoveryRepository readings;
    private final FailedPurchaseCompensationService recovery;
    private final WalletRecoveryProperties properties;
    private final Clock clock;

    public FailedPurchaseRecoveryScheduler(ReadingRecoveryRepository readings,
            FailedPurchaseCompensationService recovery, WalletRecoveryProperties properties, Clock clock) {
        this.readings = readings;
        this.recovery = recovery;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.wallet.recovery.poll-delay:30s}",
            initialDelayString = "${app.wallet.recovery.poll-delay:30s}")
    public void poll() {
        for (var id : readings.findReadyFailed(clock.instant(), properties.maxAttempts(), properties.batchSize())) {
            try { recovery.recover(id); }
            catch (RuntimeException failure) {
                log.warn("Purchase recovery transaction failed: purchaseId={}, exception={}", id,
                        failure.getClass().getSimpleName());
            }
        }
    }
}
