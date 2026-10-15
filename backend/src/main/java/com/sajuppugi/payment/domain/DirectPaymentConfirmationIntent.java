package com.sajuppugi.payment.domain;

import java.time.Instant;
import java.util.UUID;

/** Payment identity stored before contacting the PG; raw payment keys are never persisted. */
public record DirectPaymentConfirmationIntent(UUID batchId, String paymentKeyHash, int amountKrw, Instant createdAt) {
    public DirectPaymentConfirmationIntent {
        if (batchId == null || paymentKeyHash == null || !paymentKeyHash.matches("[a-f0-9]{64}")
                || amountKrw <= 0 || createdAt == null) throw new IllegalArgumentException("Invalid payment intent");
    }
}
