package com.sajuppugi.fortune.talisman.port;

import com.sajuppugi.fortune.talisman.domain.Talisman.AssetKeys;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Durable renderer work. The lease token and fence prevent a stale worker from publishing files. */
public interface TalismanRenderJobRepository {
    void enqueue(UUID talismanId, Instant now);

    List<Lease> claimReady(Instant now, int maxAttempts, int batchSize, Duration leaseDuration);

    void complete(Lease lease, AssetKeys assets, Instant now);

    void fail(Lease lease, String failureCode, Instant now, int maxAttempts, Duration retryDelay);

    record Lease(UUID talismanId, UUID token, long fence) {}
}
