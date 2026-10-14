package com.sajuppugi.wallet.application;

import java.util.Optional;
import java.util.UUID;

/** Owner-scoped durable facts for B recovery, not a current-balance query. */
public interface WalletPurchaseReceiptPort {
    Optional<WalletPurchasePort.DebitResult> findDebit(UUID userId, UUID quoteId);
    Optional<DebitEvidence> findDebitEvidence(UUID userId, UUID quoteId);
    record DebitEvidence(WalletPurchasePort.DebitResult receipt, java.time.Instant committedAt) {}
    Optional<WalletPurchasePort.CompensationResult> findCompensation(UUID userId, UUID quoteId, UUID originalTransactionId);
}
