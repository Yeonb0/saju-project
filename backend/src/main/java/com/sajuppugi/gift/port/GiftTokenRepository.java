package com.sajuppugi.gift.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface GiftTokenRepository {
    Optional<TokenAccess> findByHash(String tokenHash);

    /** Revoke the old hash and activate the replacement atomically with optimistic version checking. */
    boolean rotate(UUID giftId, long expectedVersion, String newTokenHash);

    record TokenAccess(UUID giftId, long version, Instant expiresAt, boolean revoked) {}
}
