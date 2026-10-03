package com.sajuppugi.wallet.domain;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

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
}
