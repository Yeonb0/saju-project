package com.sajuppugi.fortune.talisman.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.talisman.render")
public record TalismanRenderProperties(boolean enabled, int maxAttempts, int batchSize,
        Duration pollDelay, Duration retryDelay, Duration leaseDuration) {
    public TalismanRenderProperties {
        if (maxAttempts < 1) maxAttempts = 3;
        if (batchSize < 1) batchSize = 10;
        if (pollDelay == null || pollDelay.isNegative() || pollDelay.isZero()) pollDelay = Duration.ofSeconds(30);
        if (retryDelay == null || retryDelay.isNegative() || retryDelay.isZero()) retryDelay = Duration.ofMinutes(1);
        if (leaseDuration == null || leaseDuration.compareTo(Duration.ofSeconds(30)) < 0) leaseDuration = Duration.ofMinutes(3);
    }
}
