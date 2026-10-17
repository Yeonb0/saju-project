package com.sajuppugi.payment.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bounded local concurrency for scheduled PG recovery work. */
@ConfigurationProperties("app.toss.operations-scheduler")
public record PaymentOperationsSchedulerProperties(int poolSize) {
    public PaymentOperationsSchedulerProperties {
        if (poolSize < 1 || poolSize > 8) {
            throw new IllegalArgumentException("Invalid payment operations scheduler pool size");
        }
    }
}
