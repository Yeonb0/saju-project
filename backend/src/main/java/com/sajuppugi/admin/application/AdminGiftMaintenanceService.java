package com.sajuppugi.admin.application;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.common.logging.TraceContext;
import com.sajuppugi.gift.application.GiftMaintenanceRecoveryPort;
import com.sajuppugi.gift.application.GiftMaintenanceRecoveryPort.Kind;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminGiftMaintenanceService {
    private final JdbcTemplate jdbc;
    private final AdminAccessService access;
    private final AuditLogPort audit;
    private final ObjectProvider<GiftMaintenanceRecoveryPort> maintenance;
    private final Clock clock;
    private final boolean enabled;
    public AdminGiftMaintenanceService(JdbcTemplate jdbc, AdminAccessService access, AuditLogPort audit,
            ObjectProvider<GiftMaintenanceRecoveryPort> maintenance, Clock clock,
            @Value("${app.admin.recovery-enabled:false}") boolean enabled) {
        this.jdbc = jdbc; this.access = access; this.audit = audit; this.maintenance = maintenance; this.clock = clock; this.enabled = enabled;
    }
    @Transactional(timeout = 10)
    public Reopened request(UUID actor, UUID order, Kind kind, String key, Reason reason) {
        access.require(actor, true);
        if (key == null || key.isBlank() || key.length() > 512 || reason == null || kind == null) throw new ApiException(ErrorCode.INVALID_REQUEST);
        var port = maintenance.getIfAvailable();
        if (!enabled || port == null) throw new ApiException(ErrorCode.PAYMENT_FULFILLMENT_UNAVAILABLE);
        String binding = hash(key);
        port.reopen(order, kind, () -> reserve(actor, order, kind, binding, reason));
        return find(actor, binding);
    }
    private boolean reserve(UUID actor, UUID order, Kind kind, String key, Reason reason) {
        // Prevent grant/member revocation from racing the mutation and audit commit.
        var roles = jdbc.query("SELECT g.user_id FROM admin_operator_grants g JOIN users u ON u.id = g.user_id WHERE g.user_id = ? AND g.active = TRUE AND g.can_recover = TRUE AND u.status = 'ACTIVE' FOR NO KEY UPDATE OF g,u",
                (rs,row) -> rs.getObject(1, UUID.class), actor);
        if (roles.isEmpty()) throw new ApiException(ErrorCode.FORBIDDEN);
        int inserted = jdbc.update("INSERT INTO admin_gift_maintenance_requests(id,operator_id,order_id,kind,key_hash,reason_code,created_at) VALUES (?,?,?,?,?,?,?) ON CONFLICT DO NOTHING",
                UUID.randomUUID(), actor, order, kind.name(), key, reason.name(), java.sql.Timestamp.from(clock.instant()));
        var saved = find(actor, key);
        if (!saved.orderId().equals(order) || saved.kind() != kind || saved.reasonCode() != reason) throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        if (inserted == 0) return false;
        int cycles = jdbc.queryForObject("SELECT COUNT(*) FROM admin_gift_maintenance_requests WHERE order_id = ? AND kind = ?", Integer.class, order, kind.name());
        if (cycles > 3) throw new ApiException(ErrorCode.INVALID_REQUEST);
        audit.append(new AuditLogPort.Entry(actor, "ADMIN_GIFT_MAINTENANCE_REOPEN", "GIFT_ORDER", order, reason.name(), TraceContext.currentId(), clock.instant()));
        return true;
    }
    private Reopened find(UUID actor, String key) {
        return jdbc.query("SELECT id,order_id,kind,reason_code,created_at FROM admin_gift_maintenance_requests WHERE operator_id = ? AND key_hash = ?",
                (rs,row) -> new Reopened(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), Kind.valueOf(rs.getString(3)),
                        Reason.valueOf(rs.getString(4)), "REOPENED", rs.getTimestamp(5).toInstant()), actor, key).getFirst();
    }
    private String hash(String key) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    public enum Reason { SYSTEM_RECOVERY, CUSTOMER_INQUIRY }
    public record Reopened(UUID requestId, UUID orderId, Kind kind, Reason reasonCode, String state, Instant requestedAt) {}
}
