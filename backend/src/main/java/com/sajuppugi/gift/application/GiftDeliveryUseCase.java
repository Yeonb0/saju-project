package com.sajuppugi.gift.application;

import com.sajuppugi.common.idempotency.IdempotencyKey;
import java.time.Instant;
import java.util.UUID;

public interface GiftDeliveryUseCase {
    DeliveryView get(UUID purchaserId, UUID giftOrderId);

    /** A manual send rotates the token; automatic retries reuse the same job token. */
    DeliveryView resend(UUID purchaserId, UUID giftOrderId, IdempotencyKey key);

    DeliveryView reissueLink(UUID purchaserId, UUID giftOrderId, IdempotencyKey key);

    enum Status { PENDING, SENT, FAILED }

    record DeliveryView(Status status, boolean canResend, int remainingResends, Instant nextResendAt) {}
}
