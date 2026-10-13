package com.sajuppugi.auth;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class JdbcAuthRepository implements AuthRepository {
    private final JdbcTemplate jdbc;
    JdbcAuthRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void saveState(String hash, String returnTo, Instant expiresAt) {
        jdbc.update("INSERT INTO oauth_login_states (state_hash, return_to, expires_at) VALUES (?, ?, ?)", hash, returnTo, ts(expiresAt));
    }
    public Optional<String> consumeState(String hash, Instant now) {
        int consumed = jdbc.update("""
                UPDATE oauth_login_states SET consumed_at = ?
                WHERE state_hash = ? AND consumed_at IS NULL AND expires_at > ?
                """, ts(now), hash, ts(now));
        if (consumed != 1) return Optional.empty();
        return jdbc.query("SELECT return_to FROM oauth_login_states WHERE state_hash = ?",
                (rs, row) -> rs.getString(1), hash).stream().findFirst();
    }
    public UUID upsertActiveMember(String subject, String nickname, Instant now) {
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO users (id, kakao_subject, nickname, status, created_at, updated_at)
                    VALUES (?, ?, ?, 'ACTIVE', ?, ?)
                    """, id, subject, nickname, ts(now), ts(now));
        } catch (DuplicateKeyException duplicate) {
            jdbc.update("""
                    UPDATE users SET nickname = ?, updated_at = ?
                    WHERE kakao_subject = ? AND status = 'ACTIVE'
                    """, nickname, ts(now), subject);
        }
        return jdbc.query("""
                SELECT id FROM users WHERE kakao_subject = ? AND status = 'ACTIVE'
                """, (rs, row) -> rs.getObject(1, UUID.class), subject).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Kakao member is not active"));
    }
    @Transactional
    public void createSession(UUID id, UUID userId, String tokenHash, String csrfHash, Instant expiresAt, Instant now,
                              int maxActiveSessions) {
        // Serialise only this user's short session write. OAuth provider calls occur before this method.
        jdbc.queryForObject("SELECT id FROM users WHERE id = ? FOR UPDATE", UUID.class, userId);
        jdbc.update("""
                INSERT INTO user_sessions (id, user_id, token_hash, csrf_secret_hash, expires_at, last_seen_at)
                VALUES (?, ?, ?, ?, ?, ?)""", id, userId, tokenHash, csrfHash, ts(expiresAt), ts(now));
        // The new login is always preserved. Retain the most recently used older sessions and revoke the rest.
        var sessionsToRevoke = jdbc.query("""
                SELECT id FROM user_sessions
                WHERE user_id = ? AND id <> ? AND revoked_at IS NULL AND expires_at > ?
                ORDER BY last_seen_at DESC, id DESC
                """, (rs, row) -> rs.getObject(1, UUID.class), userId, id, ts(now));
        sessionsToRevoke.stream().skip(maxActiveSessions - 1L).forEach(sessionId ->
                jdbc.update("UPDATE user_sessions SET revoked_at = ? WHERE id = ? AND revoked_at IS NULL", ts(now), sessionId));
    }
    public Optional<Session> findSession(String tokenHash, Instant now) {
        return jdbc.query("""
                SELECT s.id, s.user_id, u.nickname, s.expires_at FROM user_sessions s JOIN users u ON u.id = s.user_id
                WHERE s.token_hash = ? AND s.revoked_at IS NULL AND s.expires_at > ? AND u.status = 'ACTIVE'
                """, (rs,row) -> new Session(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getTimestamp(4).toInstant()), tokenHash, ts(now)).stream().findFirst();
    }
    public boolean rotateCsrf(UUID id, String csrfHash, Instant now) { return jdbc.update("UPDATE user_sessions SET csrf_secret_hash=?, last_seen_at=? WHERE id=? AND revoked_at IS NULL", csrfHash, ts(now), id) == 1; }
    public boolean matchesCsrf(UUID id, String csrfHash, Instant now) { return jdbc.queryForObject("SELECT COUNT(*) FROM user_sessions WHERE id=? AND csrf_secret_hash=? AND revoked_at IS NULL AND expires_at>?", Integer.class, id, csrfHash, ts(now)) == 1; }
    public boolean revokeSession(String tokenHash, Instant now) { return jdbc.update("UPDATE user_sessions SET revoked_at=? WHERE token_hash=? AND revoked_at IS NULL", ts(now), tokenHash) == 1; }
    private static Timestamp ts(Instant value) { return Timestamp.from(value); }
}
