package com.sajuppugi.auth;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAuthRepository implements AuthRepository {
    private final JdbcTemplate jdbc;
    JdbcAuthRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void saveState(String hash, String returnTo, Instant expiresAt) {
        jdbc.update("INSERT INTO oauth_login_states (state_hash, return_to, expires_at) VALUES (?, ?, ?)", hash, returnTo, ts(expiresAt));
    }
    public Optional<String> consumeState(String hash, Instant now) {
        return jdbc.query("""
                UPDATE oauth_login_states SET consumed_at = ?
                WHERE state_hash = ? AND consumed_at IS NULL AND expires_at > ?
                RETURNING return_to
                """, (rs, row) -> rs.getString(1), ts(now), hash, ts(now)).stream().findFirst();
    }
    public UUID upsertActiveMember(String subject, String nickname, Instant now) {
        UUID id = UUID.randomUUID();
        return jdbc.query("""
                INSERT INTO users (id, kakao_subject, nickname, status, created_at, updated_at)
                VALUES (?, ?, ?, 'ACTIVE', ?, ?)
                ON CONFLICT (kakao_subject) DO UPDATE SET nickname = EXCLUDED.nickname, updated_at = EXCLUDED.updated_at
                WHERE users.status = 'ACTIVE'
                RETURNING id
                """, (rs, row) -> rs.getObject(1, UUID.class), id, subject, nickname, ts(now), ts(now)).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Kakao member is not active"));
    }
    public void createSession(UUID id, UUID userId, String tokenHash, String csrfHash, Instant expiresAt, Instant now) {
        jdbc.update("""
                INSERT INTO user_sessions (id, user_id, token_hash, csrf_secret_hash, expires_at, last_seen_at)
                VALUES (?, ?, ?, ?, ?, ?)""", id, userId, tokenHash, csrfHash, ts(expiresAt), ts(now));
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
