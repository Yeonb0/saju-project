package com.sajuppugi.common.idempotency;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcPurchaseRequestBindings {
    private final JdbcTemplate jdbc;

    public JdbcPurchaseRequestBindings(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public void insert(UUID actor, String keyHash, String requestHash, Instant now) {
        jdbc.update("""
                INSERT INTO purchase_request_bindings(actor_id, key_hash, request_hash, created_at)
                VALUES (?, ?, ?, ?)
                """, actor, keyHash, requestHash, Timestamp.from(now));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true, timeout = 5)
    public Optional<String> find(UUID actor, String keyHash) {
        return jdbc.query("SELECT request_hash FROM purchase_request_bindings WHERE actor_id = ? AND key_hash = ?",
                (rs, row) -> rs.getString(1), actor, keyHash).stream().findFirst();
    }
}
