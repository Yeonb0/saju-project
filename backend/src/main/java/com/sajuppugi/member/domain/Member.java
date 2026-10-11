package com.sajuppugi.member.domain;

import java.time.Instant;
import java.util.UUID;

public record Member(UUID id, String nickname, Status status, Instant createdAt,
                     Instant withdrawalRequestedAt, Instant deletedAt) {
    public enum Status { ACTIVE, WITHDRAWAL_PENDING, WITHDRAWN, BLOCKED }
}
