package com.sajuppugi.fortune.reading.infrastructure;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.reading.generation-context-reencryption", name = "enabled", havingValue = "true")
public class GenerationContextReencryptionRunner implements ApplicationRunner {
    private final GenerationContextReencryptionService service;
    private final int batchSize;

    public GenerationContextReencryptionRunner(GenerationContextReencryptionService service,
                                               @Value("${app.reading.generation-context-reencryption.batch-size:100}")
                                               int batchSize) {
        this.service = service;
        this.batchSize = batchSize;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        while (service.reencryptBatch(batchSize) == batchSize) {
            // Continue in bounded transactions until no full batch remains.
        }
    }
}
