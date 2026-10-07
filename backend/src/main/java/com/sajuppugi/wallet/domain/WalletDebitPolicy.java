package com.sajuppugi.wallet.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public final class WalletDebitPolicy {
    private static final Comparator<WalletLot> ORDER = Comparator.comparing(WalletLot::expiresAt)
            .thenComparingInt(lot -> lot.balanceType() == WalletLot.BalanceType.BONUS ? 0 : 1)
            .thenComparing(WalletLot::createdAt)
            .thenComparing(WalletLot::id);

    private WalletDebitPolicy() {
    }

    public static List<WalletLot> orderedUsableLots(List<WalletLot> lots, Instant now) {
        return lots.stream().filter(lot -> lot.isUsableAt(now)).sorted(ORDER).toList();
    }

    /** Calculate negative ledger allocations without changing balances or persisting a purchase. */
    public static List<WalletTransaction.Allocation> allocateDebit(List<WalletLot> lots, int amount, Instant now) {
        Objects.requireNonNull(lots, "lots");
        Objects.requireNonNull(now, "now");
        if (amount <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }
        var ids = new HashSet<java.util.UUID>();
        for (WalletLot lot : lots) {
            if (!ids.add(Objects.requireNonNull(lot, "lot").id())) {
                throw new IllegalArgumentException("Duplicate wallet lot");
            }
        }
        int remaining = amount;
        var allocations = new ArrayList<WalletTransaction.Allocation>();
        for (WalletLot lot : orderedUsableLots(lots, now)) {
            int used = Math.min(remaining, lot.remainingAmount());
            allocations.add(new WalletTransaction.Allocation(lot.id(), -used));
            remaining -= used;
            if (remaining == 0) {
                return List.copyOf(allocations);
            }
        }
        throw new IllegalArgumentException("Insufficient usable wallet balance");
    }
}
