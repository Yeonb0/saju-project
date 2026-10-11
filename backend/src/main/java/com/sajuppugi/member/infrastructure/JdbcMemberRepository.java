package com.sajuppugi.member.infrastructure;

import com.sajuppugi.member.domain.Member;
import com.sajuppugi.member.port.MemberRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcMemberRepository implements MemberRepository {
    private final JdbcTemplate jdbc;

    public JdbcMemberRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Member> find(UUID userId) {
        return jdbc.query("SELECT * FROM users WHERE id = ?", this::map, userId).stream().findFirst();
    }

    @Override
    public boolean requestWithdrawal(UUID userId, Instant now) {
        return jdbc.update("""
                UPDATE users SET status = 'WITHDRAWAL_PENDING', withdrawal_requested_at = ?, updated_at = ?
                WHERE id = ? AND status = 'ACTIVE'
                """, timestamp(now), timestamp(now), userId) == 1;
    }

    @Override
    public void revokeSessions(UUID userId, Instant now) {
        jdbc.update("UPDATE user_sessions SET revoked_at = ? WHERE user_id = ? AND revoked_at IS NULL",
                timestamp(now), userId);
    }

    @Override
    public List<UUID> findWithdrawalDue(Instant cutoff) {
        return jdbc.queryForList("""
                SELECT id FROM users
                WHERE status = 'WITHDRAWAL_PENDING' AND withdrawal_requested_at <= ?
                ORDER BY withdrawal_requested_at, id
                """, UUID.class, timestamp(cutoff));
    }

    @Override
    public boolean anonymizeWithdrawal(UUID userId, Instant now) {
        int updated = jdbc.update("""
                UPDATE users SET kakao_subject = ?, nickname = NULL, status = 'WITHDRAWN',
                    updated_at = ?, deleted_at = ?
                WHERE id = ? AND status = 'WITHDRAWAL_PENDING'
                """, "withdrawn:" + userId, timestamp(now), timestamp(now), userId);
        if (updated != 1) return false;
        jdbc.update("DELETE FROM user_sessions WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM people WHERE owner_user_id = ?", userId);
        jdbc.update("""
                UPDATE readings SET subject_display_name = '탈퇴한 사용자',
                    counterpart_display_name = CASE WHEN counterpart_display_name IS NULL THEN NULL ELSE '삭제된 인물' END
                WHERE owner_user_id = ?
                """, userId);
        return true;
    }

    private Member map(ResultSet rs, int row) throws SQLException {
        return new Member(rs.getObject("id", UUID.class), rs.getString("nickname"),
                Member.Status.valueOf(rs.getString("status")), instant(rs, "created_at"),
                instant(rs, "withdrawal_requested_at"), instant(rs, "deleted_at"));
    }

    private static Timestamp timestamp(Instant value) { return Timestamp.from(value); }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
