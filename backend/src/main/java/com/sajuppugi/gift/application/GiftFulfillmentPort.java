package com.sajuppugi.gift.application;

import java.time.Instant;
import java.util.UUID;

/** Internal B integration only. Callbacks must store data in the same DB transaction; never call a provider here. */
public interface GiftFulfillmentPort {
    Receipt accept(String rawToken, UUID requestId, String recipientSnapshotHash, Runnable saveRecipient);
    Receipt provide(UUID receiptId, UUID contentId, Runnable saveContent);
    Receipt link(String rawToken, UUID recipientUserId);
    Receipt owned(UUID recipientUserId, UUID giftId);

    record Receipt(UUID receiptId, UUID orderId, UUID giftId, String state, UUID contentId,
                   Instant submittedAt, Instant firstProvidedAt, Instant accessExpiresAt) {}
}
