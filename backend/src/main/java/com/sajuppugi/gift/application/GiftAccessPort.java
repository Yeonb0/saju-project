package com.sajuppugi.gift.application;

import java.time.Instant;
import java.util.UUID;

/** A supplies token/state authorization; B supplies recipient data and content. Never log rawToken. */
public interface GiftAccessPort {
    GiftAccess verify(String rawToken);

    GiftAccess verifyTalismanAccess(String rawToken, UUID talismanId);

    record GiftAccess(UUID giftId, Instant accessExpiresAt) {}
}
