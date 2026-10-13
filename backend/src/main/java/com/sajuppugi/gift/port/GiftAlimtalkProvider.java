package com.sajuppugi.gift.port;

import java.util.UUID;

/** Dealer adapter boundary. UNKNOWN is not evidence of failure or permission to send a new job. */
public interface GiftAlimtalkProvider {
    Result send(Request request);
    Result lookup(UUID jobId);

    enum Outcome {
        DELIVERED, ACCEPTED, RETRYABLE_REJECTION, PERMANENT_REJECTION, NOT_FOUND, UNKNOWN
    }
    /** jobId is the stable correlation/idempotency key; rejection means confirmed non-acceptance. */
    record Request(UUID jobId, UUID giftId, String recipientPhone, String senderName, String token) {
        @Override public String toString() { return "GiftAlimtalkRequest[REDACTED]"; }
    }
    record Result(UUID jobId, Outcome outcome) {
        public boolean matches(UUID expected) { return jobId != null && jobId.equals(expected) && outcome != null; }
    }
}
