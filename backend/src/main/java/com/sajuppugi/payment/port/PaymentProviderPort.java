package com.sajuppugi.payment.port;

import com.sajuppugi.payment.application.TopUpUseCase.Confirmation;
import java.util.UUID;

/** A timeout is UNKNOWN, not evidence of a declined payment. */
public interface PaymentProviderPort {
    Result confirm(UUID orderId, Confirmation confirmation);

    Result lookup(UUID orderId);

    Result cancel(UUID orderId, UUID refundId, int amountKrw, String reasonCode);

    enum Outcome { APPROVED, DECLINED, CANCELED, PENDING, UNKNOWN }

    record Result(Outcome outcome, int amountKrw, String safeFailureCode) {}
}
