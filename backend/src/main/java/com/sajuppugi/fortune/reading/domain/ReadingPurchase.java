package com.sajuppugi.fortune.reading.domain;

import java.time.Instant;
import java.util.UUID;

public record ReadingPurchase(
        UUID id,
        UUID buyerUserId,
        UUID productId,
        UUID quoteId,
        UUID subjectPersonId,
        UUID walletTransactionId,
        Status status,
        UUID readingId,
        Instant createdAt,
        Instant fulfilledAt) {

    public enum Status { CREATED, DEBITED, GENERATING, FULFILLED, FAILED, REFUNDED }
}
