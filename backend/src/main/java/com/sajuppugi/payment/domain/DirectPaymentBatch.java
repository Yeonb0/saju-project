package com.sajuppugi.payment.domain;

import java.time.Instant;
import java.util.UUID;

/** Immutable, server-priced payment intent for a fortune, addon, or gift batch. */
public record DirectPaymentBatch(UUID id, UUID buyerUserId, UUID quoteId, String productCode,
                                 String productName, int unitAmountKrw, int quantity,
                                 int totalAmountKrw, Status status, Instant createdAt) {
    public DirectPaymentBatch {
        if (id == null || buyerUserId == null || productCode == null || productName == null
                || unitAmountKrw <= 0 || quantity <= 0 || totalAmountKrw <= 0 || totalAmountKrw >= 50_000
                || totalAmountKrw != Math.multiplyExact(unitAmountKrw, quantity) || status == null || createdAt == null) {
            throw new IllegalArgumentException("Invalid direct payment batch");
        }
    }
    public enum Status { PAYMENT_PENDING, PAID, FAILED, CANCELED, REVIEW_REQUIRED }
}
