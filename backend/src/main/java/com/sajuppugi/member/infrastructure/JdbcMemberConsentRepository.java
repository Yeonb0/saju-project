package com.sajuppugi.member.infrastructure;

import com.sajuppugi.member.domain.MemberConsent;
import com.sajuppugi.member.port.MemberConsentRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcMemberConsentRepository implements MemberConsentRepository {
    private final JdbcTemplate jdbc;

    public JdbcMemberConsentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<MemberConsent> find(UUID userId, String termsVersion, String privacyVersion) {
        return jdbc.query("""
                SELECT * FROM member_consents
                WHERE user_id = ? AND terms_version = ? AND privacy_version = ?
                """, this::map, userId, termsVersion, privacyVersion).stream().findFirst();
    }

    @Override
    public MemberConsent saveIfAbsent(MemberConsent consent) {
        if (isPostgres()) insertPostgres(consent);
        else insertH2(consent);
        return find(consent.userId(), consent.termsVersion(), consent.privacyVersion()).orElseThrow();
    }

    private void insertPostgres(MemberConsent consent) {
        jdbc.update("""
                INSERT INTO member_consents (id, user_id, terms_version, privacy_version,
                    age_over_14_confirmed, terms_agreed, privacy_agreed, marketing_agreed, agreed_at)
                VALUES (?, ?, ?, ?, TRUE, TRUE, TRUE, ?, ?)
                ON CONFLICT (user_id, terms_version, privacy_version) DO NOTHING
                """, consent.id(), consent.userId(), consent.termsVersion(), consent.privacyVersion(),
                consent.marketingAgreed(), Timestamp.from(consent.agreedAt()));
    }

    private void insertH2(MemberConsent consent) {
        jdbc.update("""
                MERGE INTO member_consents target
                USING (VALUES (?, ?, ?, ?, ?, ?))
                    incoming (id, user_id, terms_version, privacy_version, marketing_agreed, agreed_at)
                ON target.user_id = incoming.user_id AND target.terms_version = incoming.terms_version
                    AND target.privacy_version = incoming.privacy_version
                WHEN NOT MATCHED THEN INSERT (id, user_id, terms_version, privacy_version,
                    age_over_14_confirmed, terms_agreed, privacy_agreed, marketing_agreed, agreed_at)
                VALUES (incoming.id, incoming.user_id, incoming.terms_version, incoming.privacy_version,
                    TRUE, TRUE, TRUE, incoming.marketing_agreed, incoming.agreed_at)
                """, consent.id(), consent.userId(), consent.termsVersion(), consent.privacyVersion(),
                consent.marketingAgreed(), Timestamp.from(consent.agreedAt()));
    }

    private MemberConsent map(ResultSet rs, int row) throws SQLException {
        return new MemberConsent(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                rs.getString("terms_version"), rs.getString("privacy_version"),
                rs.getBoolean("age_over_14_confirmed"), rs.getBoolean("terms_agreed"),
                rs.getBoolean("privacy_agreed"), rs.getBoolean("marketing_agreed"),
                rs.getTimestamp("agreed_at").toInstant());
    }

    private boolean isPostgres() {
        return Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) connection ->
                "PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())));
    }
}
