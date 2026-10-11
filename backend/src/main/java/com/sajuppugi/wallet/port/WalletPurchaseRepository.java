package com.sajuppugi.wallet.port;

import com.sajuppugi.wallet.domain.WalletBalance;
import com.sajuppugi.wallet.domain.WalletTransaction;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** All writes require the owning wallet lock in the same transaction. */
public interface WalletPurchaseRepository {
    Optional<WalletTransaction> findTransaction(UUID transactionId);
    Optional<Receipt> findDebit(UUID userId, UUID quoteId);
    Optional<Receipt> findCompensation(UUID userId, UUID originalTransactionId);
    Optional<Command> findCommand(UUID userId, String keyHash);
    void saveDebit(UUID quoteId, Receipt receipt);
    void saveCompensation(UUID originalTransactionId, Receipt receipt);
    void saveCommand(UUID userId, String keyHash, String requestHash, Receipt receipt, Instant now);

    record Receipt(WalletTransaction transaction, WalletBalance balance) {}
    record Command(String requestHash, Receipt receipt) {}
}
