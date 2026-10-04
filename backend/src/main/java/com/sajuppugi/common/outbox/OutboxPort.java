package com.sajuppugi.common.outbox;

import java.time.Instant;
import java.util.UUID;

public interface OutboxPort {
    /** Enqueue in the same DB transaction as the originating business change. */
    void enqueue(Message message);

    // Raw gift links and recipient details must be encrypted before this boundary.
    record Message(UUID id, UUID aggregateId, String type, String encryptedPayload, Instant availableAt) {
        @Override
        public String toString() {
            return "Message[id=" + id + ", type=" + type + ", payload=REDACTED]";
        }
    }
}
