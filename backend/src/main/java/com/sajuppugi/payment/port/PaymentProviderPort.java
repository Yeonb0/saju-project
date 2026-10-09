package com.sajuppugi.payment.port;

import com.sajuppugi.payment.application.TopUpUseCase.Confirmation;
import java.util.UUID;
import com.sajuppugi.payment.domain.TopUpConfirmationIntent;

/** A timeout is UNKNOWN, not evidence of a declined payment. */
public interface PaymentProviderPort {
    Result confirm(UUID orderId, Confirmation confirmation);

    Result lookup(UUID orderId);

    Result cancel(UUID orderId, UUID refundId, int amountKrw, String reasonCode);

    enum Outcome { APPROVED, DECLINED, CANCELED, PENDING, UNKNOWN }

    record Result(Outcome outcome, UUID orderId, String paymentKeyHash, int amountKrw,
                  String currency, String safeFailureCode) {
        /** Required again at the grant boundary, including results obtained by order lookup. */
        public boolean approves(TopUpConfirmationIntent intent) {
            return outcome == Outcome.APPROVED && intent != null && intent.orderId().equals(orderId)
                    && intent.paymentKeyHash().equals(paymentKeyHash)
                    && intent.amountKrw() == amountKrw && "KRW".equals(currency);
        }

        @Override
        public String toString() {
            return "PaymentResult[outcome=" + outcome + ", orderId=" + orderId + ", paymentKeyHash=REDACTED]";
        }
    }
}
