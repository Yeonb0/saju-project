package com.sajuppugi.common.idempotency;

import java.util.UUID;

/** Implementations must claim atomically and compare the canonical request hash. */
public interface IdempotencyStore {
    Claim claim(UUID actorId, String operation, IdempotencyKey key, String requestHash);

    void complete(UUID claimId, StoredResponse response);

    enum Outcome { ACQUIRED, PROCESSING, COMPLETED, REQUEST_MISMATCH }

    record Claim(Outcome outcome, UUID claimId, StoredResponse response) {}

    // Store only sanitized response bodies; retention is not decided here.
    record StoredResponse(int status, String contentType, String body) {
        @Override
        public String toString() {
            return "StoredResponse[status=" + status + ", body=REDACTED]";
        }
    }
}
