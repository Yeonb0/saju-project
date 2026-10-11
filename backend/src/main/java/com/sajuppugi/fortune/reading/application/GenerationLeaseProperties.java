package com.sajuppugi.fortune.reading.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.reading.generation")
public record GenerationLeaseProperties(Duration leaseDuration) {
    public GenerationLeaseProperties {
        if (leaseDuration == null || leaseDuration.isNegative() || leaseDuration.isZero()) {
            leaseDuration = Duration.ofMinutes(2);
        }
    }
}
