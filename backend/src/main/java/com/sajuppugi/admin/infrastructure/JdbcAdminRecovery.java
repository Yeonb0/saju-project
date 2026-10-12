package com.sajuppugi.admin.infrastructure;

import com.sajuppugi.admin.application.AdminAccessService;
import com.sajuppugi.admin.application.AuditLogPort;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.common.logging.TraceContext;
import com.sajuppugi.payment.application.VerifiedPaymentRecoveryService;
import com.sajuppugi.payment.port.PaymentProviderPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcAdminRecovery {
    private final JdbcTemplate jdbc;
    private final AdminAccessService access;
    private final AuditLogPort audit;
    private final VerifiedPaymentRecoveryService payments;
    private final Clock clock;
    public JdbcAdminRecovery(JdbcTemplate jdbc, AdminAccessService access, AuditLogPort audit, VerifiedPaymentRecoveryService payments, Clock clock) { this.jdbc = jdbc; this.access = access; this.audit = audit; this.payments = payments; this.clock = clock; }
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public UUID begin(UUID actor, UUID order, String key, Reason reason) {
        access.require(actor, true);
        if (key == null || key.isBlank() || key.length() > 512 || reason == null) throw new ApiException(ErrorCode.INVALID_REQUEST);
        // Match finish's order-first lock order before FK checks take member key-share locks.
        if (jdbc.query("SELECT id FROM top_up_orders WHERE id = ? FOR UPDATE",
                (rs, row) -> rs.getObject(1, UUID.class), order).isEmpty()) throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        String request = hash(order + ":" + reason.name());
        UUID id = UUID.randomUUID();
        int inserted = jdbc.update("INSERT INTO admin_recovery_commands(id,operator_id,order_id,key_hash,request_hash,reason_code,state,next_attempt_at,created_at) VALUES (?,?,?,?,?,?,'READY',?,?) ON CONFLICT DO NOTHING",
                id, actor, order, hash(key), request, reason.name(), Timestamp.from(clock.instant()), Timestamp.from(clock.instant()));
        var rows = jdbc.query("SELECT id, request_hash FROM admin_recovery_commands WHERE operator_id = ? AND key_hash = ? FOR UPDATE",
                (rs, row) -> new Binding(rs.getObject(1, UUID.class), rs.getString(2)), actor, hash(key));
        if (rows.isEmpty() || !request.equals(rows.getFirst().hash())) throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        if (inserted == 1) append(actor, "ADMIN_RECOVERY_INTENT", order, reason.name());
        return rows.getFirst().id();
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public Optional<Lease> claim(UUID id) {
        var rows = jdbc.query("SELECT * FROM admin_recovery_commands WHERE id = ? FOR UPDATE", (rs, row) ->
                new Attempt(rs.getObject("operator_id", UUID.class), rs.getObject("order_id", UUID.class), rs.getString("state"), rs.getInt("attempts"),
                        rs.getString("reason_code"), rs.getTimestamp("next_attempt_at").toInstant()), id);
        if (rows.isEmpty()) return Optional.empty(); var attempt = rows.getFirst(); Instant now = clock.instant();
        if (!List.of("READY","RETRY","IN_FLIGHT").contains(attempt.state()) || attempt.next().isAfter(now)) return Optional.empty();
        if (!access.permitted(attempt.actor(), true) || attempt.attempts() >= 3) {
            jdbc.update("UPDATE admin_recovery_commands SET state = 'REVIEW_REQUIRED', result_code = 'RECOVERY_ACCESS_OR_LIMIT', lease_token = NULL, lease_until = NULL WHERE id = ?", id);
            append(attempt.actor(), "ADMIN_RECOVERY_REVIEW", attempt.order(), attempt.reason());
            return Optional.empty();
        }
        UUID token = UUID.randomUUID();
        jdbc.update("UPDATE admin_recovery_commands SET state = 'IN_FLIGHT', attempts = attempts + 1, lease_token = ?, lease_until = ?, next_attempt_at = ? WHERE id = ?",
                token, Timestamp.from(now.plusSeconds(180)), Timestamp.from(now.plusSeconds(180)), id);
        return Optional.of(new Lease(id, attempt.order(), attempt.actor(), token, attempt.reason()));
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public void finish(Lease lease, PaymentProviderPort.Result evidence) {
        payments.lockOrder(lease.order());
        Instant now = clock.instant();
        var attempts = jdbc.query("SELECT attempts FROM admin_recovery_commands WHERE id = ? AND state = 'IN_FLIGHT' AND lease_token = ? AND lease_until > ? FOR UPDATE",
                (rs, row) -> rs.getInt(1), lease.id(), lease.token(), Timestamp.from(now));
        if (attempts.isEmpty()) return;
        // Lock the role and member rows until the financial update/audit commit; revocation cannot race the commit.
        var roles = jdbc.query("SELECT g.user_id FROM admin_operator_grants g JOIN users u ON u.id = g.user_id WHERE g.user_id = ? AND g.active = TRUE AND g.can_recover = TRUE AND u.status = 'ACTIVE' FOR NO KEY UPDATE OF g, u",
                (rs, row) -> rs.getObject(1, UUID.class), lease.actor());
        String result = roles.isEmpty() ? "OPERATOR_AUTHORIZATION_REVOKED" : payments.apply(lease.order(), evidence);
        boolean completed = List.of("CREDITED","ALREADY_CREDITED","REFUNDED","CANCELED","ALREADY_CLOSED").contains(result);
        boolean review = roles.isEmpty() || attempts.getFirst() >= 3 || List.of("MANUAL_REVIEW_REQUIRED","PROVIDER_STATE_MISMATCH").contains(result);
        jdbc.update("UPDATE admin_recovery_commands SET state = ?, result_code = ?, completed_at = ?, next_attempt_at = ?, lease_token = NULL, lease_until = NULL WHERE id = ?",
                completed ? "COMPLETED" : review ? "REVIEW_REQUIRED" : "RETRY", result, completed ? Timestamp.from(now) : null, Timestamp.from(now.plusSeconds(30)), lease.id());
        append(lease.actor(), completed ? "ADMIN_RECOVERY_COMPLETED" : review ? "ADMIN_RECOVERY_REVIEW" : "ADMIN_RECOVERY_PENDING", lease.order(), lease.reason());
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public View view(UUID actor, UUID id) {
        access.require(actor, false);
        var rows = jdbc.query("SELECT id,order_id,state,result_code,completed_at FROM admin_recovery_commands WHERE id = ?", (rs, row) ->
                new View(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4), rs.getTimestamp(5) == null ? null : rs.getTimestamp(5).toInstant()), id);
        if (rows.isEmpty()) throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        append(actor, "ADMIN_RECOVERY_VIEW", rows.getFirst().orderId(), "SUPPORT_VIEW");
        return rows.getFirst();
    }
    public List<UUID> due() {
        return jdbc.query("SELECT id FROM admin_recovery_commands WHERE state IN ('READY','RETRY','IN_FLIGHT') AND next_attempt_at <= ? ORDER BY next_attempt_at LIMIT 20",
                (rs, row) -> rs.getObject(1, UUID.class), Timestamp.from(clock.instant()));
    }
    private void append(UUID actor, String action, UUID order, String reason) { audit.append(new AuditLogPort.Entry(actor, action, "TOP_UP_ORDER", order, reason, TraceContext.currentId(), clock.instant())); }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException failure) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    public enum Reason { CUSTOMER_INQUIRY, SYSTEM_RECOVERY }
    @io.swagger.v3.oas.annotations.media.Schema(name = "AdminRecoveryResponse", requiredProperties = {"commandId", "orderId", "state", "resultCode", "completedAt"})
    public record View(UUID commandId, UUID orderId, String state,
            @io.swagger.v3.oas.annotations.media.Schema(types = {"string", "null"}) String resultCode,
            @io.swagger.v3.oas.annotations.media.Schema(types = {"string", "null"}, format = "date-time") Instant completedAt) {}
    public record Lease(UUID id, UUID order, UUID actor, UUID token, String reason) {}
    private record Attempt(UUID actor, UUID order, String state, int attempts, String reason, Instant next) {}
    private record Binding(UUID id, String hash) {}
}
