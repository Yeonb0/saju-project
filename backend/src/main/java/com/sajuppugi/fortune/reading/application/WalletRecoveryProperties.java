package com.sajuppugi.fortune.reading.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.wallet.recovery")
public record WalletRecoveryProperties(@DefaultValue("5") int maxAttempts,
        @DefaultValue("30s") Duration retryDelay, @DefaultValue("20") int batchSize) {
    public WalletRecoveryProperties {
        if (maxAttempts < 1 || maxAttempts > 20 || batchSize < 1 || batchSize > 100
                || retryDelay == null || retryDelay.compareTo(Duration.ofSeconds(1)) < 0
                || retryDelay.compareTo(Duration.ofMinutes(10)) > 0) {
            throw new IllegalArgumentException("Invalid wallet recovery limits");
        }
    }
}
