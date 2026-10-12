package com.sajuppugi.payment.port;

import com.sajuppugi.payment.application.TopUpUseCase.Confirmation;
import java.util.UUID;
import com.sajuppugi.payment.domain.TopUpConfirmationIntent;

/** A timeout is UNKNOWN, not evidence of a declined payment. */
public interface PaymentProviderPort {
    Result confirm(UUID orderId, Confirmation confirmation);

    Result lookup(UUID orderId);

    Result cancel(UUID orderId, UUID refundId, int amountKrw, String reasonCode);

    default Result cancelVerified(TopUpConfirmationIntent intent, UUID refundId) {
        Result evidence = lookup(intent.orderId());
        if (evidence == null || !evidence.matches(intent))
            return new Result(Outcome.UNKNOWN, intent.orderId(), null, 0, null, "REFUND_EVIDENCE_MISMATCH");
        if (evidence.outcome() == Outcome.CANCELED) return evidence;
        if (!evidence.approves(intent)) return evidence;
        return cancel(intent.orderId(), refundId, intent.amountKrw(), "UNUSED_TOP_UP");
    }

    enum Outcome { APPROVED, DECLINED, CANCELED, CANCEL_REJECTED, PENDING, UNKNOWN }

    record Result(Outcome outcome, UUID orderId, String paymentKeyHash, int amountKrw,
                  String currency, String safeFailureCode) {
        /** Required again at the grant boundary, including results obtained by order lookup. */
        public boolean approves(TopUpConfirmationIntent intent) {
            return outcome == Outcome.APPROVED && matches(intent);
        }

        public boolean matches(TopUpConfirmationIntent intent) {
            return intent != null && intent.orderId().equals(orderId)
                    && intent.paymentKeyHash().equals(paymentKeyHash)
                    && intent.amountKrw() == amountKrw && "KRW".equals(currency);
        }

        @Override
        public String toString() {
            return "PaymentResult[outcome=" + outcome + ", orderId=" + orderId + ", paymentKeyHash=REDACTED]";
        }
    }
}
