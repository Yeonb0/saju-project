package com.sajuppugi.wallet.application;

import com.sajuppugi.common.idempotency.IdempotencyKey;
import java.util.UUID;

/** Trusted recovery boundary that verifies the purchase's ledger owner and quote linkage. */
public interface WalletPurchaseRecoveryPort {
    WalletPurchasePort.CompensationResult compensatePurchase(UUID userId, UUID quoteId,
            UUID originalTransactionId, String reasonCode, IdempotencyKey key);
}
