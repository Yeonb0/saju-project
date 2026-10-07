package com.sajuppugi.wallet.port;

import com.sajuppugi.wallet.application.WalletQueryUseCase.TransactionPage;
import com.sajuppugi.wallet.domain.WalletBalance;
import java.time.Instant;
import java.util.UUID;

/** Read-only storage; does not expose the purchase repository's locking or mutation operations. */
public interface WalletReadRepository {
    WalletBalance usableBalance(UUID userId, Instant now);

    TransactionPage transactions(UUID userId, UUID afterTransactionId, int limit);
}
