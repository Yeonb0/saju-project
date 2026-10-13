package com.sajuppugi.share;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.fortune.daily.application.DailyFortuneService.DailyFortune;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import com.sajuppugi.person.infrastructure.PersonDataProtector;

@Repository
public class JdbcDailyFortuneShares {
    private final JdbcTemplate jdbc;
    private final PersonDataProtector protector;
    public JdbcDailyFortuneShares(JdbcTemplate jdbc, PersonDataProtector protector) {
        this.jdbc = jdbc;
        this.protector = protector;
    }

    @Transactional(timeout = 5)
    public DailyFortuneShareService.Created createOrReuse(UUID user, UUID person, DailyFortune fortune,
            Instant now, Instant expiresAt, Supplier<String> tokens) {
        var owned = jdbc.query("SELECT id FROM people WHERE id = ? AND owner_user_id = ? AND deleted_at IS NULL FOR UPDATE",
                (rs, row) -> rs.getObject(1, UUID.class), person, user);
        if (owned.isEmpty()) throw new ApiException(ErrorCode.PERSON_NOT_FOUND);
        var existing = jdbc.query("SELECT * FROM daily_fortune_shares WHERE owner_user_id = ? AND person_id = ? AND fortune_date = ? FOR UPDATE",
                (rs, row) -> mapReusable(rs), user, person, fortune.date());
        if (!existing.isEmpty()) {
            Stored value = existing.getFirst();
            if (value.revokedAt() == null && value.expiresAt().isAfter(now)) {
                return new DailyFortuneShareService.Created(value.id(), value.token(), value.expiresAt(), value.snapshot());
            }
            String token = tokens.get();
            jdbc.update("""
                    UPDATE daily_fortune_shares SET public_token_hash=?, public_token_enc=?, overall_score=?, love_score=?, wealth_score=?,
                    study_work_score=?, condition_score=?, one_line_fortune=?, lucky_color=?, lucky_item=?,
                    lucky_place=?, created_at=?, expires_at=?, revoked_at=NULL WHERE id=?
                    """, hash(token), protector.encrypt(token), fortune.scores().overall(), fortune.scores().love(), fortune.scores().wealth(),
                    fortune.scores().studyWork(), fortune.scores().condition(), fortune.content().oneLineFortune(),
                    fortune.content().luckyColor(), fortune.content().luckyItem(), fortune.content().luckyPlace(),
                    Timestamp.from(now), Timestamp.from(expiresAt), value.id());
            return new DailyFortuneShareService.Created(value.id(), token, expiresAt, snapshot(fortune));
        }
        UUID id = UUID.randomUUID(); String token = tokens.get();
        jdbc.update("""
                INSERT INTO daily_fortune_shares(id,public_token_hash,public_token_enc,owner_user_id,person_id,fortune_date,
                overall_score,love_score,wealth_score,study_work_score,condition_score,one_line_fortune,
                lucky_color,lucky_item,lucky_place,created_at,expires_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, hash(token), protector.encrypt(token), user, person, fortune.date(), fortune.scores().overall(), fortune.scores().love(),
                fortune.scores().wealth(), fortune.scores().studyWork(), fortune.scores().condition(),
                fortune.content().oneLineFortune(), fortune.content().luckyColor(), fortune.content().luckyItem(),
                fortune.content().luckyPlace(), Timestamp.from(now), Timestamp.from(expiresAt));
        return new DailyFortuneShareService.Created(id, token, expiresAt, snapshot(fortune));
    }

    @Transactional(readOnly = true, timeout = 5)
    public Optional<Stored> find(String token) {
        return jdbc.query("SELECT * FROM daily_fortune_shares WHERE public_token_hash = ?",
                        (rs, row) -> mapPublic(rs), hash(token))
                .stream().findFirst();
    }

    @Transactional(readOnly = true, timeout = 5)
    public Optional<DailyFortuneShareService.Created> findOwnedActive(UUID user, UUID person, LocalDate date, Instant now) {
        return jdbc.query("""
                SELECT s.* FROM daily_fortune_shares s
                JOIN people p ON p.id = s.person_id
                WHERE s.owner_user_id = ? AND s.person_id = ? AND s.fortune_date = ?
                  AND p.owner_user_id = ? AND p.deleted_at IS NULL
                  AND s.revoked_at IS NULL AND s.expires_at > ?
                """, (rs, row) -> mapReusable(rs), user, person, date, user, Timestamp.from(now))
                .stream().findFirst()
                .map(value -> new DailyFortuneShareService.Created(value.id(), value.token(), value.expiresAt(), value.snapshot()));
    }

    @Transactional(timeout = 5)
    public boolean revokeOwned(UUID user, UUID id, Instant now) {
        int changed = jdbc.update("UPDATE daily_fortune_shares SET revoked_at=? WHERE id=? AND owner_user_id=? AND revoked_at IS NULL",
                Timestamp.from(now), id, user);
        if (changed == 1) return true;
        return jdbc.queryForObject("SELECT COUNT(*) FROM daily_fortune_shares WHERE id=? AND owner_user_id=?", Integer.class, id, user) == 1;
    }

    private Stored mapReusable(ResultSet rs) throws SQLException {
        return map(rs, protector.decrypt(rs.getBytes("public_token_enc")));
    }

    private Stored mapPublic(ResultSet rs) throws SQLException {
        return map(rs, null);
    }

    private Stored map(ResultSet rs, String token) throws SQLException {
        var snapshot = new DailyFortuneShareService.Snapshot(rs.getDate("fortune_date").toLocalDate(),
                new DailyFortuneShareService.Scores(rs.getInt("overall_score"), rs.getInt("love_score"),
                        rs.getInt("wealth_score"), rs.getInt("study_work_score"), rs.getInt("condition_score")),
                new DailyFortuneShareService.Content(rs.getString("one_line_fortune"), rs.getString("lucky_color"),
                        rs.getString("lucky_item"), rs.getString("lucky_place")));
        return new Stored(rs.getObject("id", UUID.class), token, snapshot,
                rs.getTimestamp("expires_at").toInstant(),
                rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant());
    }

    private DailyFortuneShareService.Snapshot snapshot(DailyFortune fortune) {
        return new DailyFortuneShareService.Snapshot(fortune.date(),
                new DailyFortuneShareService.Scores(fortune.scores().overall(), fortune.scores().love(),
                        fortune.scores().wealth(), fortune.scores().studyWork(), fortune.scores().condition()),
                new DailyFortuneShareService.Content(fortune.content().oneLineFortune(), fortune.content().luckyColor(),
                        fortune.content().luckyItem(), fortune.content().luckyPlace()));
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record Stored(UUID id, String token, DailyFortuneShareService.Snapshot snapshot,
                         Instant expiresAt, Instant revokedAt) {}
}
