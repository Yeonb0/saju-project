package com.sajuppugi.wallet.application;

import com.sajuppugi.wallet.domain.WalletBalance;
import com.sajuppugi.wallet.domain.WalletTransaction;
import java.util.List;
import java.util.UUID;

public interface WalletQueryUseCase {
    WalletBalance getBalance(UUID userId);

    TransactionPage getTransactions(UUID userId, String cursor, int limit);

    record TransactionPage(List<WalletTransaction> items, String nextCursor) {
        public TransactionPage {
            items = List.copyOf(items);
        }
    }
}
