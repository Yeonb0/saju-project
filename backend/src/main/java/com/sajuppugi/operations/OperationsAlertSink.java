package com.sajuppugi.operations;

import java.time.Instant;
import java.util.UUID;

public interface OperationsAlertSink {
    /** At-least-once delivery. The receiver must deduplicate alertId; receipt is not operator acknowledgement. */
    boolean send(Alert alert);
    record Alert(UUID alertId, String kind, UUID resourceId, int generation, Instant detectedAt) {}
}
