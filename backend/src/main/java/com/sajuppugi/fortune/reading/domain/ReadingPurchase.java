package com.sajuppugi.fortune.reading.domain;

import java.time.Instant;
import java.util.UUID;

public record ReadingPurchase(
        UUID id,
        UUID buyerUserId,
        UUID productId,
        UUID quoteId,
        UUID subjectPersonId,
        UUID counterpartPersonId,
        String relationType,
        String questionKey,
        UUID walletTransactionId,
        Status status,
        UUID readingId,
        Instant createdAt,
        Instant fulfilledAt) {

    public ReadingPurchase(UUID id, UUID buyerUserId, UUID productId, UUID quoteId, UUID subjectPersonId,
                           UUID walletTransactionId, Status status, UUID readingId,
                           Instant createdAt, Instant fulfilledAt) {
        this(id, buyerUserId, productId, quoteId, subjectPersonId, null, null, null,
                walletTransactionId, status, readingId, createdAt, fulfilledAt);
    }

    public enum Status { CREATED, DEBITED, GENERATING, FULFILLED, FAILED, REFUNDED }
}
