package com.sajuppugi.member.port;

import com.sajuppugi.member.domain.Member;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemberRepository {
    Optional<Member> find(UUID userId);
    boolean requestWithdrawal(UUID userId, Instant now);
    void revokeSessions(UUID userId, Instant now);
    List<UUID> findWithdrawalDue(Instant cutoff);
    boolean anonymizeWithdrawal(UUID userId, Instant now);
}
