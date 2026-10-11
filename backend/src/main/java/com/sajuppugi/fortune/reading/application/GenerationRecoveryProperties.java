package com.sajuppugi.fortune.reading.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.reading.generation-recovery")
public record GenerationRecoveryProperties(boolean enabled, int maxAttempts, int batchSize, Duration pollDelay) {
    public GenerationRecoveryProperties {
        if (maxAttempts < 1) maxAttempts = 3;
        if (batchSize < 1) batchSize = 20;
        if (pollDelay == null || pollDelay.isNegative() || pollDelay.isZero()) pollDelay = Duration.ofSeconds(30);
    }
}
