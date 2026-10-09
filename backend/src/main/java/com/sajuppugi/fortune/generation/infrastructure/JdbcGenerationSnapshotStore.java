package com.sajuppugi.fortune.generation.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajuppugi.fortune.generation.domain.GenerationModels.GeneratedReading;
import com.sajuppugi.fortune.generation.domain.GenerationModels.FortuneType;
import com.sajuppugi.fortune.generation.domain.GenerationModels.GenerationMode;
import com.sajuppugi.fortune.generation.domain.GenerationModels.LinerRequest;
import com.sajuppugi.fortune.generation.domain.GenerationModels.LinerResponse;
import com.sajuppugi.fortune.generation.port.GenerationSnapshotStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcGenerationSnapshotStore implements GenerationSnapshotStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Clock clock;

    public JdbcGenerationSnapshotStore(JdbcTemplate jdbc, ObjectMapper mapper, Clock clock) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public Optional<GeneratedReading> findSucceeded(String generationKey) {
        return jdbc.query("SELECT * FROM reading_results WHERE generation_key = ? AND status = 'SUCCEEDED'",
                (rs, row) -> reading(rs), generationKey).stream().findFirst();
    }

    @Override
    public boolean tryClaim(LinerRequest request, String contentVersion, Duration lease) {
        Instant now = clock.instant();
        try {
            return jdbc.update("""
                    INSERT INTO reading_results (id, generation_key, fortune_type, reference_date, facts_json,
                    calculation_version, generation_version, content_version, status, lease_until, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'GENERATING', ?, ?)
                    """, UUID.randomUUID(), request.generationKey(), request.fortuneType().name(), request.referenceDate(),
                    json(request.facts()), request.calculationVersion(), request.generationVersion(), contentVersion,
                    Timestamp.from(now.plus(lease)), Timestamp.from(now)) == 1;
        } catch (DuplicateKeyException ignored) {
            return jdbc.update("""
                    UPDATE reading_results SET status = 'GENERATING', failure_code = NULL, lease_until = ?
                    WHERE generation_key = ? AND status <> 'SUCCEEDED'
                    AND (status = 'FAILED' OR lease_until IS NULL OR lease_until < ?)
                    """, Timestamp.from(now.plus(lease)), request.generationKey(), Timestamp.from(now)) == 1;
        }
    }

    @Override
    public int nextAttemptNumber(String generationKey) {
        Integer next = jdbc.queryForObject("""
                SELECT COALESCE(MAX(a.attempt_no), 0) + 1
                FROM reading_results r
                LEFT JOIN generation_attempts a ON a.reading_result_id = r.id
                WHERE r.generation_key = ?
                """, Integer.class, generationKey);
        if (next == null) throw new IllegalStateException("Generation claim does not exist");
        return next;
    }

    @Override
    public GeneratedReading saveSucceeded(LinerRequest request, LinerResponse response, String contentVersion,
                                          GenerationMode generationMode) {
        int updated = jdbc.update("""
                UPDATE reading_results SET sections_json = ?, status = 'SUCCEEDED', failure_code = NULL,
                generation_mode = ?, lease_until = NULL, completed_at = ?
                WHERE generation_key = ? AND status = 'GENERATING'
                """, json(response), generationMode.name(), Timestamp.from(clock.instant()), request.generationKey());
        if (updated != 1) throw new IllegalStateException("Generation claim was lost before saving");
        return findSucceeded(request.generationKey()).orElseThrow();
    }

    @Override
    public void recordAttempt(String generationKey, int attempt, String provider, String inputHash,
                              String status, long latencyMs, String errorCode) {
        Instant ended = clock.instant();
        jdbc.update("""
                INSERT INTO generation_attempts (id, reading_result_id, attempt_no, provider, input_hash,
                status, latency_ms, error_code, started_at, ended_at)
                SELECT ?, id, ?, ?, ?, ?, ?, ?, ?, ? FROM reading_results WHERE generation_key = ?
                """, UUID.randomUUID(), attempt, provider, inputHash, status, latencyMs, errorCode,
                Timestamp.from(ended.minusMillis(latencyMs)), Timestamp.from(ended), generationKey);
    }

    @Override
    public void markFailed(String generationKey, String failureCode) {
        jdbc.update("""
                UPDATE reading_results SET status = 'FAILED', failure_code = ?, lease_until = NULL,
                completed_at = ? WHERE generation_key = ? AND status = 'GENERATING'
                """, failureCode, Timestamp.from(clock.instant()), generationKey);
    }

    private GeneratedReading reading(ResultSet rs) throws SQLException {
        try {
            return new GeneratedReading(rs.getObject("id", UUID.class), rs.getString("generation_key"),
                    FortuneType.valueOf(rs.getString("fortune_type")),
                    mapper.readValue(rs.getString("sections_json"), LinerResponse.class),
                    rs.getString("calculation_version"), rs.getString("generation_version"),
                    rs.getString("content_version"), GenerationMode.valueOf(rs.getString("generation_mode")), false);
        } catch (JsonProcessingException exception) {
            throw new SQLException("Invalid stored generation JSON", exception);
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize generation JSON", exception);
        }
    }
}
