package com.sajuppugi.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Per-instance PostgreSQL pool budget. The deployment must multiply this by its maximum app replicas. */
@ConfigurationProperties("app.database.pool")
public record DatabasePoolProperties(int maxSize, int minIdle, int connectionTimeoutMs, int validationTimeoutMs) {
    public DatabasePoolProperties {
        if (maxSize < 1 || maxSize > 20 || minIdle < 0 || minIdle > maxSize
                || connectionTimeoutMs < 250 || connectionTimeoutMs > 10_000
                || validationTimeoutMs < 250 || validationTimeoutMs >= connectionTimeoutMs) {
            throw new IllegalArgumentException("Invalid database pool limits");
        }
    }
}
