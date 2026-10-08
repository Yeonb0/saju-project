package com.sajuppugi.fortune.talisman.infrastructure;

import com.sajuppugi.fortune.calculation.domain.CalculationFacts.Element;
import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import com.sajuppugi.fortune.talisman.domain.Talisman;
import com.sajuppugi.fortune.talisman.domain.Talisman.Animal;
import com.sajuppugi.fortune.talisman.domain.Talisman.AssetKeys;
import com.sajuppugi.fortune.talisman.domain.Talisman.Status;
import com.sajuppugi.fortune.talisman.port.TalismanRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcTalismanRepository implements TalismanRepository {
    private final JdbcTemplate jdbc;

    public JdbcTalismanRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Talisman> findByReadingId(UUID readingId) {
        return jdbc.query("SELECT * FROM talismans WHERE source_reading_id = ?",
                (rs, row) -> talisman(rs), readingId).stream().findFirst();
    }

    @Override
    public Optional<Talisman> findOwned(UUID ownerUserId, UUID talismanId) {
        return jdbc.query("""
                SELECT t.* FROM talismans t
                JOIN talisman_ownerships o ON o.talisman_id = t.id
                WHERE o.owner_user_id = ? AND t.id = ?
                """, (rs, row) -> talisman(rs), ownerUserId, talismanId).stream().findFirst();
    }

    @Override
    @Transactional
    public Talisman createPending(Talisman talisman) {
        if (talisman.status() != Status.PENDING) {
            throw new IllegalArgumentException("Only pending talisman can be created");
        }
        int inserted = jdbc.update("""
                MERGE INTO talismans target
                USING (VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?))
                    AS incoming (id, owner_user_id, source_reading_id, fortune_type, element, animal,
                        phrase, description, content_version, created_at)
                ON target.source_reading_id = incoming.source_reading_id
                WHEN NOT MATCHED THEN INSERT (id, owner_user_id, source_reading_id, fortune_type, element,
                    animal, phrase, description, content_version, status, created_at)
                VALUES (incoming.id, incoming.owner_user_id, incoming.source_reading_id, incoming.fortune_type,
                    incoming.element, incoming.animal, incoming.phrase, incoming.description,
                    incoming.content_version, 'PENDING', incoming.created_at)
                """, talisman.id(), talisman.ownerUserId(), talisman.sourceReadingId(),
                talisman.fortuneType().name(), talisman.element().name(), talisman.animal().name(),
                talisman.phrase(), talisman.description(), talisman.contentVersion(),
                Timestamp.from(talisman.createdAt()));
        if (inserted == 0) {
            Talisman existing = findByReadingId(talisman.sourceReadingId()).orElseThrow();
            if (!existing.ownerUserId().equals(talisman.ownerUserId())) {
                throw new IllegalStateException("Reading is already linked to a different talisman owner");
            }
            return existing;
        }
        jdbc.update("""
                INSERT INTO talisman_ownerships (talisman_id, owner_user_id, source, acquired_at)
                VALUES (?, ?, 'PURCHASE', ?)
                """, talisman.id(), talisman.ownerUserId(), Timestamp.from(talisman.createdAt()));
        return talisman;
    }

    @Override
    public void markReady(UUID talismanId, AssetKeys assets, Instant readyAt) {
        requireOne(jdbc.update("""
                UPDATE talismans SET status = 'READY', original_object_key = ?, thumbnail_object_key = ?,
                share_object_key = ?, ready_at = ? WHERE id = ? AND status = 'PENDING'
                """, assets.original(), assets.thumbnail(), assets.share(), Timestamp.from(readyAt), talismanId),
                "mark talisman ready");
    }

    @Override
    public void markFailed(UUID talismanId, String failureCode) {
        if (failureCode == null || failureCode.isBlank()) {
            throw new IllegalArgumentException("failureCode is required");
        }
        requireOne(jdbc.update("""
                UPDATE talismans SET status = 'FAILED', failure_code = ?
                WHERE id = ? AND status = 'PENDING'
                """, failureCode, talismanId), "mark talisman failed");
    }

    @Override
    public void retry(UUID talismanId) {
        requireOne(jdbc.update("""
                UPDATE talismans SET status = 'PENDING', failure_code = NULL
                WHERE id = ? AND status = 'FAILED'
                """, talismanId), "retry talisman");
    }

    private Talisman talisman(ResultSet rs) throws SQLException {
        Status status = Status.valueOf(rs.getString("status"));
        AssetKeys assets = status == Status.READY ? new AssetKeys(
                rs.getString("original_object_key"), rs.getString("thumbnail_object_key"),
                rs.getString("share_object_key")) : null;
        return new Talisman(rs.getObject("id", UUID.class), rs.getObject("owner_user_id", UUID.class),
                rs.getObject("source_reading_id", UUID.class), FortuneType.valueOf(rs.getString("fortune_type")),
                Element.valueOf(rs.getString("element")), Animal.valueOf(rs.getString("animal")),
                rs.getString("phrase"), rs.getString("description"), rs.getString("content_version"), status,
                assets, rs.getString("failure_code"), instant(rs, "created_at"), instant(rs, "ready_at"));
    }

    private Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private void requireOne(int updated, String operation) {
        if (updated != 1) throw new IllegalStateException("Cannot " + operation + " from current state");
    }
}
