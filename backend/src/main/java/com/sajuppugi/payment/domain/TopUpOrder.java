package com.sajuppugi.payment.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record TopUpOrder(UUID id, UUID userId, String productCode, int amountKrw,
                         int paidShells, int bonusShells, Status status, Instant createdAt) {
    public TopUpOrder {
        Objects.requireNonNull(id);
        Objects.requireNonNull(userId);
        Objects.requireNonNull(status);
        Objects.requireNonNull(createdAt);
        if (productCode == null || productCode.isBlank()
                || amountKrw <= 0 || paidShells <= 0 || bonusShells < 0) {
            throw new IllegalArgumentException("Invalid top-up snapshot");
        }
        Math.addExact(paidShells, bonusShells);
    }

    public enum Status { CREATED, PAYMENT_PENDING, PAID, CREDITED, FAILED, CANCELED, REFUNDED }
}
