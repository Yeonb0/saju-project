package com.sajuppugi.fortune.reading;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sajuppugi.fortune.generation.infrastructure.LinerApiProperties;
import com.sajuppugi.fortune.reading.application.GenerationLeaseProperties;
import com.sajuppugi.fortune.reading.application.GenerationTimingValidator;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class GenerationTimingValidatorTest {
    @Test
    void acceptsLeaseLongerThanBoundedProviderAttempts() {
        assertThatCode(() -> new GenerationTimingValidator(new GenerationLeaseProperties(Duration.ofMinutes(2)),
                liner(Duration.ofSeconds(3), Duration.ofSeconds(30)))).doesNotThrowAnyException();
    }

    @Test
    void rejectsLeaseThatCanExpireDuringProviderRetries() {
        assertThatThrownBy(() -> new GenerationTimingValidator(new GenerationLeaseProperties(Duration.ofSeconds(60)),
                liner(Duration.ofSeconds(3), Duration.ofSeconds(30))))
                .isInstanceOf(IllegalStateException.class);
    }

    private LinerApiProperties liner(Duration connect, Duration read) {
        return new LinerApiProperties("http://localhost", "", "test", connect, read, 100, "low");
    }
}
