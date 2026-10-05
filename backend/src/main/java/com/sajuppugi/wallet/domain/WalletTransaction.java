package com.sajuppugi.wallet.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable ledger facts. Corrections append a reversal rather than editing this record. */
public record WalletTransaction(UUID id, UUID userId, Type type, int amount,
        UUID referenceId, UUID reversalOfId, List<Allocation> allocations, Instant createdAt) {
    public WalletTransaction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(referenceId, "referenceId");
        Objects.requireNonNull(createdAt, "createdAt");
        allocations = List.copyOf(allocations);
        if (amount == 0 || allocations.isEmpty()) {
            throw new IllegalArgumentException("Ledger transaction must have nonzero allocations");
        }
        boolean validDirection = switch (type) {
            case TOP_UP -> amount > 0;
            case PURCHASE, EXPIRY -> amount < 0;
            // Refunds may restore a purchase or reclaim a cash-refunded top-up.
            case REFUND, ADJUSTMENT -> true;
        };
        if (!validDirection) {
            throw new IllegalArgumentException("Ledger amount direction does not match transaction type");
        }
        if (id.equals(reversalOfId)) {
            throw new IllegalArgumentException("Ledger transaction cannot reverse itself");
        }
        var ids = new HashSet<UUID>();
        long sum = 0;
        for (Allocation allocation : allocations) {
            if (!ids.add(allocation.lotId()) || Integer.signum(allocation.amount()) != Integer.signum(amount)) {
                throw new IllegalArgumentException("Duplicate lot or inconsistent allocation sign");
            }
            sum += allocation.amount();
        }
        if (sum != amount) {
            throw new IllegalArgumentException("Ledger allocations do not match transaction amount");
        }
    }

    public enum Type { TOP_UP, PURCHASE, REFUND, EXPIRY, ADJUSTMENT }

    public record Allocation(UUID lotId, int amount) {
        public Allocation {
            Objects.requireNonNull(lotId, "lotId");
            if (amount == 0) {
                throw new IllegalArgumentException("Allocation must be nonzero");
            }
        }
    }
}
