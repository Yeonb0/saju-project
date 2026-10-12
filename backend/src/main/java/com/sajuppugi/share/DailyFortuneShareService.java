package com.sajuppugi.share;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.fortune.daily.application.DailyFortuneService;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DailyFortuneShareService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final DailyFortuneService fortunes;
    private final JdbcDailyFortuneShares shares;
    private final Clock clock;

    public DailyFortuneShareService(DailyFortuneService fortunes, JdbcDailyFortuneShares shares, Clock clock) {
        this.fortunes = fortunes;
        this.shares = shares;
        this.clock = clock;
    }

    public Created create(UUID userId, UUID personId) {
        var fortune = fortunes.today(userId, personId);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        return shares.createOrReuse(userId, personId, fortune, now, now.plus(24, ChronoUnit.HOURS), this::token);
    }

    public Snapshot get(String token) {
        if (token == null || token.length() < 32 || token.length() > 64) {
            throw new ApiException(ErrorCode.SHARE_NOT_FOUND);
        }
        var stored = shares.find(token).orElseThrow(() -> new ApiException(ErrorCode.SHARE_NOT_FOUND));
        Instant now = clock.instant();
        if (stored.revokedAt() != null) throw new ApiException(ErrorCode.SHARE_REVOKED);
        if (!stored.expiresAt().isAfter(now)) throw new ApiException(ErrorCode.SHARE_EXPIRED);
        return stored.snapshot();
    }

    public void revoke(UUID userId, UUID shareId) {
        if (!shares.revokeOwned(userId, shareId, clock.instant().truncatedTo(ChronoUnit.MICROS))) {
            throw new ApiException(ErrorCode.SHARE_NOT_FOUND);
        }
    }

    private String token() {
        byte[] value = new byte[32];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    public record Created(UUID shareId, String token, Instant expiresAt, Snapshot fortune) {}
    public record Snapshot(java.time.LocalDate date, Scores scores, Content content) {}
    public record Scores(int overall, int love, int wealth, int studyWork, int condition) {}
    public record Content(String oneLineFortune, String luckyColor, String luckyItem, String luckyPlace) {}
}
