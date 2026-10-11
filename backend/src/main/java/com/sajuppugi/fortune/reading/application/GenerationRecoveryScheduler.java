package com.sajuppugi.fortune.reading.application;

import com.sajuppugi.fortune.reading.port.ReadingRepository;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = {"app.wallet.purchases-enabled", "app.reading.generation-recovery.enabled"}, havingValue = "true")
public class GenerationRecoveryScheduler {
    private static final Logger log = LoggerFactory.getLogger(GenerationRecoveryScheduler.class);
    private final ReadingRepository readings;
    private final GenerationRecoveryService recovery;
    private final GenerationRecoveryProperties properties;
    private final Clock clock;

    public GenerationRecoveryScheduler(ReadingRepository readings, GenerationRecoveryService recovery,
                                       GenerationRecoveryProperties properties, Clock clock) {
        this.readings = readings;
        this.recovery = recovery;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.reading.generation-recovery.poll-delay:30s}",
            initialDelayString = "${app.reading.generation-recovery.poll-delay:30s}")
    public void poll() {
        var now = clock.instant();
        for (var id : readings.findExhaustedGenerationRecoveries(now, properties.maxAttempts(), properties.batchSize())) {
            try {
                if (readings.markGenerationExhausted(id, now, properties.maxAttempts())) {
                    log.warn("Reading generation retries exhausted: purchaseId={}", id);
                }
            } catch (RuntimeException failure) {
                log.warn("Cannot finalize exhausted reading generation: purchaseId={}, exception={}", id,
                        failure.getClass().getSimpleName());
            }
        }
        for (var id : readings.findReadyGenerationRecoveries(now, properties.maxAttempts(), properties.batchSize())) {
            try {
                recovery.recover(id);
            } catch (RuntimeException failure) {
                log.warn("Reading generation recovery failed transiently: purchaseId={}, exception={}", id,
                        failure.getClass().getSimpleName());
            }
        }
    }
}
