package com.sajuppugi.member.application;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.member.domain.Member;
import com.sajuppugi.member.port.MemberRepository;
import com.sajuppugi.person.port.PersonRepository;
import com.sajuppugi.wallet.application.WalletQueryUseCase;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {
    public static final Duration WITHDRAWAL_RETENTION = Duration.ofDays(30);
    private final MemberRepository repository;
    private final Clock clock;
    private final WalletQueryUseCase wallets;
    private final PersonRepository people;

    public MemberService(MemberRepository repository, Clock clock, WalletQueryUseCase wallets,
                         PersonRepository people) {
        this.repository = repository;
        this.clock = clock;
        this.wallets = wallets;
        this.people = people;
    }

    @Transactional(readOnly = true)
    public Member get(UUID userId) {
        return repository.find(required(userId))
                .orElseThrow(() -> new ApiException(ErrorCode.MEMBER_NOT_FOUND));
    }

    @Transactional
    public Member requestWithdrawal(UUID userId) {
        userId = required(userId);
        Instant now = clock.instant();
        Member current = get(userId);
        if (wallets.getBalance(userId).paidBalance() > 0) {
            throw new ApiException(ErrorCode.PAID_BALANCE_REMAINS);
        }
        if (current.status() != Member.Status.ACTIVE || !repository.requestWithdrawal(userId, now)) {
            throw new ApiException(ErrorCode.MEMBER_WITHDRAWAL_NOT_ALLOWED);
        }
        repository.revokeSessions(userId, now);
        return get(userId);
    }

    @Transactional(readOnly = true)
    public MemberProfile profile(UUID userId) {
        Member member = get(userId);
        return new MemberProfile(member, wallets.getBalance(userId).balance(), people.activeSelfExists(userId));
    }

    @Transactional
    public int purgeDueWithdrawals() {
        Instant now = clock.instant();
        Instant cutoff = now.minus(WITHDRAWAL_RETENTION);
        int purged = 0;
        for (UUID userId : repository.findWithdrawalDue(cutoff)) {
            if (repository.anonymizeWithdrawal(userId, now)) purged++;
        }
        return purged;
    }

    private UUID required(UUID value) {
        if (value == null) throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED);
        return value;
    }

    public record MemberProfile(Member member, int walletBalance, boolean hasPrimaryPerson) {}
}
