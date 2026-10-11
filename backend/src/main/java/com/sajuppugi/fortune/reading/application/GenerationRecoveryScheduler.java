package com.sajuppugi.fortune.reading.application;

import com.sajuppugi.fortune.reading.port.ReadingRepository;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = {"app.wallet.purchases-enabled", "app.reading.generation-recovery.enabled"}, havingValue = "true")
public class GenerationRecoveryScheduler {
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
        for (var id : readings.findReadyGenerationRecoveries(clock.instant(), properties.maxAttempts(), properties.batchSize())) {
            recovery.recover(id);
        }
    }
}
