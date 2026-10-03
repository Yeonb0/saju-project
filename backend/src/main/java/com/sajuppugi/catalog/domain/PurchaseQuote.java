package com.sajuppugi.catalog.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PurchaseQuote(UUID id, UUID requesterUserId, Product productSnapshot,
        String contextHash, Instant createdAt, Instant expiresAt) {
    public static final Duration VALIDITY = Duration.ofMinutes(30);

    public PurchaseQuote {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(requesterUserId, "requesterUserId");
        Objects.requireNonNull(productSnapshot, "productSnapshot");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (contextHash == null || !contextHash.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("Quote context must be a normalized fingerprint");
        }
        if (!expiresAt.equals(createdAt.plus(VALIDITY))) {
            throw new IllegalArgumentException("Quote validity must be 30 minutes");
        }
    }

    public static PurchaseQuote issue(UUID id, UUID requesterUserId, Product product,
            String contextHash, Instant now) {
        if (!product.isAvailableAt(now)) {
            throw new IllegalArgumentException("Product is not available");
        }
        return new PurchaseQuote(id, requesterUserId, product, contextHash, now, now.plus(VALIDITY));
    }

    public boolean isExpiredAt(Instant now) {
        return !Objects.requireNonNull(now, "now").isBefore(expiresAt);
    }
}
