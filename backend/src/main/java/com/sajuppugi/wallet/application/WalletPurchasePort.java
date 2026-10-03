package com.sajuppugi.wallet.application;

import com.sajuppugi.common.idempotency.IdempotencyKey;
import com.sajuppugi.wallet.domain.WalletBalance;
import java.util.UUID;

/** BE-A implementation boundary consumed by BE-B's reading/talisman purchases. */
public interface WalletPurchasePort {
    DebitResult debit(UUID userId, UUID quoteId, IdempotencyKey key);

    CompensationResult compensate(UUID originalTransactionId, String reasonCode, IdempotencyKey key);

    record DebitResult(UUID transactionId, int debitedAmount, WalletBalance balance) {
    }

    record CompensationResult(UUID transactionId, UUID originalTransactionId, WalletBalance balance) {
    }
}
