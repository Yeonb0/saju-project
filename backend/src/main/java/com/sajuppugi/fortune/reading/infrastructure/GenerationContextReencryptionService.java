package com.sajuppugi.fortune.reading.infrastructure;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GenerationContextReencryptionService {
    private final JdbcTemplate jdbc;
    private final GenerationContextProtector protector;

    public GenerationContextReencryptionService(JdbcTemplate jdbc, GenerationContextProtector protector) {
        this.jdbc = jdbc;
        this.protector = protector;
    }

    @Transactional
    public int reencryptBatch(int batchSize) {
        if (batchSize < 1 || batchSize > 1_000) throw new IllegalArgumentException("batchSize must be 1..1000");
        List<StoredContext> contexts = jdbc.query("""
                SELECT id, generation_context FROM reading_purchases
                WHERE generation_context IS NOT NULL AND generation_context NOT LIKE 'enc:v2:%'
                ORDER BY id LIMIT ?
                """, (rs, row) -> new StoredContext(rs.getObject(1, UUID.class), rs.getString(2)), batchSize);
        int updated = 0;
        for (StoredContext context : contexts) {
            String encrypted = protector.encrypt(protector.decrypt(context.value()));
            updated += jdbc.update("""
                    UPDATE reading_purchases SET generation_context = ?
                    WHERE id = ? AND generation_context = ?
                    """, encrypted, context.id(), context.value());
        }
        return updated;
    }

    private record StoredContext(UUID id, String value) {}
}
