package com.sajuppugi.fortune.reading.application;

import com.sajuppugi.fortune.generation.infrastructure.LinerApiProperties;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class GenerationTimingValidator {
    private static final int PROVIDER_ATTEMPTS = 3;
    private static final Duration COMPLETION_MARGIN = Duration.ofSeconds(10);

    public GenerationTimingValidator(GenerationLeaseProperties lease, LinerApiProperties liner) {
        Duration perAttempt = required(liner.connectTimeout()).plus(required(liner.readTimeout()));
        Duration requiredLease = perAttempt.multipliedBy(PROVIDER_ATTEMPTS).plus(COMPLETION_MARGIN);
        if (lease.leaseDuration().compareTo(requiredLease) <= 0) {
            throw new IllegalStateException("Reading generation lease must exceed provider retry deadline");
        }
    }

    private Duration required(Duration value) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalStateException("Liner timeouts must be positive");
        }
        return value;
    }
}
