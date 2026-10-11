package com.sajuppugi.wallet.application;

import com.sajuppugi.wallet.domain.WalletBalance;
import java.util.UUID;

/** Internal verified-payment boundary; joins the caller's order completion transaction. */
public interface WalletTopUpPort {
    Receipt credit(UUID userId, UUID orderId, int paid, int bonus);
    record Receipt(UUID transactionId, WalletBalance balance) {}
}
