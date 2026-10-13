package com.sajuppugi.wallet.application;

import java.util.UUID;

/** A fulfilled purchase cannot later restore its debit through normal generation recovery. */
public interface WalletPurchaseSettlementPort {
    boolean fulfilled(UUID buyer, UUID quote, UUID transaction);
}
