package com.sajuppugi.wallet.port;

import com.sajuppugi.wallet.domain.WalletBalance;
import com.sajuppugi.wallet.domain.WalletLot;
import com.sajuppugi.wallet.domain.WalletTransaction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WalletRepository {
    Optional<WalletBalance> findBalance(UUID userId);

    /** Must acquire the wallet lock within the caller's transaction. */
    LockedWallet lock(UUID userId);

    /** Lot changes, immutable ledger append and balance update must commit together. */
    void record(LockedWallet before, List<WalletLot> afterLots,
            WalletBalance afterBalance, WalletTransaction transaction);

    record LockedWallet(UUID userId, long version, WalletBalance balance, List<WalletLot> lots) {
        public LockedWallet {
            lots = List.copyOf(lots);
        }
    }
}
