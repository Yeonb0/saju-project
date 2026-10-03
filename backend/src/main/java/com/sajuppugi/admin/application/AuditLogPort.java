package com.sajuppugi.admin.application;

import java.time.Instant;
import java.util.UUID;

public interface AuditLogPort {
    /** Caller must authorize the operator; this contract does not grant ADMIN or CS permissions. */
    void append(Entry entry);

    // Do not place phone numbers, tokens, birth data, or provider secrets in reasonCode.
    record Entry(UUID operatorId, String action, String targetType, UUID targetId,
                 String reasonCode, String traceId, Instant occurredAt) {}
}
