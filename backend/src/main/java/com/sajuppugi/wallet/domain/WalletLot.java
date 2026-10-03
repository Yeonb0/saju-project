package com.sajuppugi.wallet.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record WalletLot(UUID id, BalanceType balanceType, int grantedAmount,
        int remainingAmount, Instant expiresAt, Instant createdAt) {
    public WalletLot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(balanceType, "balanceType");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(createdAt, "createdAt");
        if (grantedAmount <= 0 || remainingAmount < 0 || remainingAmount > grantedAmount) {
            throw new IllegalArgumentException("Invalid lot balance");
        }
        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException("Lot expiry must follow grant time");
        }
    }

    public boolean isUsableAt(Instant now) {
        Objects.requireNonNull(now, "now");
        return remainingAmount > 0 && !now.isBefore(createdAt) && now.isBefore(expiresAt);
    }

    public enum BalanceType { PAID, BONUS }
}
