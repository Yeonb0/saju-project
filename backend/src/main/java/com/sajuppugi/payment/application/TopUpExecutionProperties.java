package com.sajuppugi.payment.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.toss.recovery")
public record TopUpExecutionProperties(int maxAttempts, Duration retryDelay, Duration leaseDuration, int batchSize) {
    public TopUpExecutionProperties {
        if (maxAttempts < 1 || maxAttempts > 10 || batchSize < 1 || batchSize > 100
                || retryDelay == null || retryDelay.compareTo(Duration.ofSeconds(1)) < 0
                || retryDelay.compareTo(Duration.ofMinutes(10)) > 0
                || leaseDuration == null || leaseDuration.compareTo(Duration.ofMinutes(3)) < 0
                || leaseDuration.compareTo(Duration.ofMinutes(10)) > 0) {
            throw new IllegalArgumentException("Invalid payment recovery limits");
        }
    }
}
