package com.sajuppugi.payment.application;

import com.sajuppugi.common.idempotency.IdempotencyKey;
import com.sajuppugi.payment.domain.TopUpOrder;
import com.sajuppugi.wallet.domain.WalletBalance;
import java.util.UUID;

/** User IDs come from authenticated server context, never from a trusted HTTP body. */
public interface TopUpUseCase {
    TopUpOrder create(UUID userId, String productCode, IdempotencyKey key);

    OrderView confirm(UUID userId, UUID orderId, Confirmation confirmation, IdempotencyKey key);

    OrderView get(UUID userId, UUID orderId);

    record Confirmation(String paymentKey, int amountKrw) {
        public Confirmation {
            if (paymentKey == null || paymentKey.isBlank() || amountKrw <= 0) {
                throw new IllegalArgumentException("Invalid payment confirmation");
            }
        }

        @Override
        public String toString() {
            return "Confirmation[paymentKey=REDACTED, amountKrw=" + amountKrw + "]";
        }
    }

    // Wallet balance is present only after the credit transaction commits.
    record OrderView(TopUpOrder order, boolean processing, WalletBalance walletBalance) {}
}
