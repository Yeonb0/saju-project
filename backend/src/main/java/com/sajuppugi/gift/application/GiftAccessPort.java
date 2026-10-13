package com.sajuppugi.gift.application;

import java.time.Instant;
import java.util.UUID;

/** A supplies token/state authorization; B supplies recipient data and content. Never log rawToken. */
public interface GiftAccessPort {
    GiftAccess verify(String rawToken);

    PublicGift describe(String rawToken);

    GiftAccess verifyTalismanAccess(String rawToken, UUID talismanId);

    record GiftAccess(UUID giftId, Instant accessExpiresAt) {}

    record PublicGift(String recipientName, String senderName, String message, ProductOption option,
                      AccessState state, Instant accessExpiresAt) {
        public enum ProductOption { TALISMAN_ONLY, READING_WITH_TALISMAN }
        public enum AccessState { INPUT_REQUIRED, PROCESSING, READY }
    }
}
