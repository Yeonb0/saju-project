package com.sajuppugi.payment.domain;

import java.time.Instant;
import java.util.UUID;
import java.util.Objects;

/** Durable request identity only; this is not proof of provider approval or wallet credit. */
public record TopUpConfirmationIntent(UUID orderId, String paymentKeyHash, int amountKrw, Instant createdAt) {
    public TopUpConfirmationIntent {
        Objects.requireNonNull(orderId);
        Objects.requireNonNull(createdAt);
        if (paymentKeyHash == null || !paymentKeyHash.matches("[a-f0-9]{64}") || amountKrw <= 0) {
            throw new IllegalArgumentException("Invalid confirmation identity");
        }
    }

    @Override
    public String toString() {
        return "TopUpConfirmationIntent[orderId=" + orderId + ", paymentKeyHash=REDACTED]";
    }
}
