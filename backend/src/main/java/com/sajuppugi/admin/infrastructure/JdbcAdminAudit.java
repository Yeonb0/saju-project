package com.sajuppugi.admin.infrastructure;

import com.sajuppugi.admin.application.AuditLogPort;
import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class JdbcAdminAudit implements AuditLogPort {
    private final JdbcTemplate jdbc;
    public JdbcAdminAudit(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void append(Entry entry) {
        if (!entry.action().matches("[A-Z_]{1,60}") || !entry.targetType().matches("[A-Z_]{1,40}")
                || !entry.reasonCode().matches("[A-Z_]{1,40}")) throw new IllegalArgumentException("Invalid audit vocabulary");
        jdbc.update("INSERT INTO admin_audit_events(id, operator_id, action, target_type, target_id, reason_code, trace_id, occurred_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), entry.operatorId(), entry.action(), entry.targetType(), entry.targetId(), entry.reasonCode(), entry.traceId(), Timestamp.from(entry.occurredAt()));
    }
}
