package com.sajuppugi.operations;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.operations.alerts")
public record OperationsAlertProperties(String webhookUrl, String webhookToken, Duration stalledAfter) {
    public OperationsAlertProperties {
        if (stalledAfter == null) stalledAfter = Duration.ofMinutes(15);
        if (stalledAfter.compareTo(Duration.ofMinutes(1)) < 0 || stalledAfter.compareTo(Duration.ofDays(7)) > 0)
            throw new IllegalStateException("Operations alert threshold is invalid");
    }
    @Override public String toString() { return "OperationsAlertProperties[webhook=REDACTED, token=REDACTED]"; }
}
