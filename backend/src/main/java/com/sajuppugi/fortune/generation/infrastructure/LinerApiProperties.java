package com.sajuppugi.fortune.generation.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.liner")
public record LinerApiProperties(
        String baseUrl,
        String apiKey,
        String model,
        Duration connectTimeout,
        Duration readTimeout,
        int maxOutputTokens,
        String reasoningEffort) {
}
