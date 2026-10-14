package com.sajuppugi.fortune.talisman.port;

import com.sajuppugi.fortune.talisman.domain.Talisman;
import com.sajuppugi.fortune.talisman.domain.Talisman.AssetKeys;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TalismanRepository {
    Optional<Talisman> findByReadingId(UUID readingId);
    Optional<Talisman> findById(UUID talismanId);
    Optional<Talisman> findOwned(UUID ownerUserId, UUID talismanId);
    List<Talisman> findOwnedPage(UUID ownerUserId, Instant beforeAcquiredAt, UUID beforeTalismanId, int limit);
    Talisman createPending(Talisman talisman);
    void markReady(UUID talismanId, AssetKeys assets, Instant readyAt);
    void markFailed(UUID talismanId, String failureCode);
    void retry(UUID talismanId);
}
