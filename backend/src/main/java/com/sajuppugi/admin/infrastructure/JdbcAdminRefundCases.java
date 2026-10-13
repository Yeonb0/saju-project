package com.sajuppugi.admin.infrastructure;

import com.sajuppugi.admin.application.AdminAccessService;
import com.sajuppugi.admin.application.AuditLogPort;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.common.logging.TraceContext;
import com.sajuppugi.payment.infrastructure.JdbcCashRefunds;
import com.sajuppugi.wallet.application.RemainingTopUpRefund;
import com.sajuppugi.wallet.application.WalletOperationsService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
public class JdbcAdminRefundCases {
    private final JdbcTemplate jdbc;
    private final AdminAccessService access;
    private final AuditLogPort audit;
    private final WalletOperationsService wallets;
    private final JdbcCashRefunds refunds;
    private final Clock clock;

    public JdbcAdminRefundCases(JdbcTemplate jdbc, AdminAccessService access, AuditLogPort audit,
                               WalletOperationsService wallets, JdbcCashRefunds refunds, Clock clock) {
        this.jdbc = jdbc; this.access = access; this.audit = audit; this.wallets = wallets; this.refunds = refunds; this.clock = clock;
    }

    public CaseView open(UUID actor, UUID order, UUID customer, String reference, Instant received, String key) {
        access.require(actor, true);
        validateKey(key);
        Instant now = clock.instant();
        var snapshot = lockOrder(order);
        lockOperator(actor);
        if (customer == null || !snapshot.customer().equals(customer)) throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        if (reference == null || !reference.matches("[A-Za-z0-9_-]{1,80}") || received == null
                || received.isBefore(snapshot.created()) || received.isAfter(now)) throw new ApiException(ErrorCode.INVALID_REQUEST);
        received = received.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        String request = hash(order + ":" + customer + ":" + reference + ":" + received);
        var keys = jdbc.query("SELECT id,request_hash FROM admin_refund_cases WHERE operator_id = ? AND key_hash = ?",
                (rs, row) -> new Binding(rs.getObject(1, UUID.class), rs.getString(2)), actor, hash(key));
        if (!keys.isEmpty()) {
            if (!request.equals(keys.getFirst().hash())) throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
            return read(keys.getFirst().id());
        }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM admin_refund_cases WHERE order_id = ?", Integer.class, order) != 0)
            throw new ApiException(ErrorCode.REFUND_NOT_ELIGIBLE);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO admin_refund_cases(id,order_id,customer_user_id,operator_id,support_reference,received_at,
                    registered_at,reason_code,key_hash,request_hash) VALUES (?,?,?,?,?,?,?,'CUSTOMER_INQUIRY',?,?)
                """, id, order, customer, actor, reference, Timestamp.from(received), Timestamp.from(now), hash(key), request);
        append(actor, "ADMIN_REFUND_CASE_OPENED", id);
        return read(id);
    }

    public CaseView view(UUID actor, UUID id) {
        access.require(actor, false);
        var result = read(id);
        append(actor, "ADMIN_REFUND_CASE_VIEW", id);
        return result;
    }

    public Quote quote(UUID actor, UUID id) {
        access.require(actor, false);
        var c = read(id);
        var order = lockOrder(c.orderId());
        if (!"CREDITED".equals(order.status()) || c.refundId() != null) throw new ApiException(ErrorCode.REFUND_NOT_ELIGIBLE);
        var calculated = wallets.remainingTopUp(order.customer(), c.orderId(), order.amount(), order.paid(), order.bonus());
        append(actor, "ADMIN_REFUND_QUOTE_VIEW", id);
        return new Quote(id, c.orderId(), order.amount(), calculated.amount(), calculated.paidShells(), calculated.bonusShells(), 0);
    }

    public UUID reserve(UUID actor, UUID id, String key, RemainingTopUpRefund expected) {
        access.require(actor, true);
        validateKey(key);
        if (expected == null || expected.amount() <= 0 || expected.paidShells() <= 0 || expected.bonusShells() < 0)
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        var c = read(id);
        lockOrder(c.orderId());
        lockOperator(actor);
        jdbc.query("SELECT id FROM admin_refund_cases WHERE id = ? FOR UPDATE", (rs, row) -> rs.getObject(1, UUID.class), id);
        // Persist the operator's exact preview, so changing a replay body cannot change its meaning.
        var bindings = jdbc.query("SELECT case_id,expected_amount,expected_paid,expected_bonus FROM admin_refund_execution_keys WHERE operator_id = ? AND key_hash = ?",
                (rs, row) -> new ExecutionBinding(rs.getObject(1, UUID.class), new RemainingTopUpRefund(rs.getInt(2), rs.getInt(3), rs.getInt(4))), actor, hash(key));
        if (!bindings.isEmpty() && (!id.equals(bindings.getFirst().caseId()) || !expected.equals(bindings.getFirst().quote())))
            throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        var existing = jdbc.query("SELECT amount_krw,reclaimed_paid,reclaimed_bonus FROM top_up_refunds WHERE admin_case_id = ?",
                (rs, row) -> new RemainingTopUpRefund(rs.getInt(1), rs.getInt(2), rs.getInt(3)), id);
        if (!existing.isEmpty() && !expected.equals(existing.getFirst())) throw new ApiException(ErrorCode.REFUND_QUOTE_CHANGED);
        if (bindings.isEmpty()) jdbc.update("INSERT INTO admin_refund_execution_keys(operator_id,key_hash,case_id,expected_amount,expected_paid,expected_bonus) VALUES (?,?,?,?,?,?)",
                actor, hash(key), id, expected.amount(), expected.paidShells(), expected.bonusShells());
        refunds.prepareRemaining(c.customerUserId(), c.orderId(), id, expected, clock.instant());
        if (existing.isEmpty()) {
            jdbc.update("UPDATE admin_refund_cases SET executed_by = ? WHERE id = ?", actor, id);
            append(actor, "ADMIN_REFUND_INTENT", id);
        }
        return c.orderId();
    }

    private CaseView read(UUID id) {
        var rows = jdbc.query("""
                SELECT c.*,r.id AS refund_id,r.state,r.resolution,r.amount_krw,r.completed_at,r.reclaimed_paid,r.reclaimed_bonus
                FROM admin_refund_cases c LEFT JOIN top_up_refunds r ON r.admin_case_id = c.id WHERE c.id = ?
                """, (rs, row) -> new CaseView(rs.getObject("id", UUID.class), rs.getObject("order_id", UUID.class),
                rs.getObject("customer_user_id", UUID.class), rs.getString("support_reference"), rs.getTimestamp("received_at").toInstant(),
                rs.getTimestamp("registered_at").toInstant(), rs.getObject("refund_id", UUID.class),
                rs.getString("state") == null ? "RECEIVED" : "CANCEL_REJECTED".equals(rs.getString("resolution")) ? "REJECTED" : rs.getString("state"),
                rs.getObject("amount_krw", Integer.class), rs.getObject("reclaimed_paid", Integer.class), rs.getObject("reclaimed_bonus", Integer.class),
                rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toInstant()), id);
        if (rows.isEmpty()) throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        return rows.getFirst();
    }

    private Order lockOrder(UUID id) {
        var rows = jdbc.query("SELECT user_id,status,amount_krw,paid_shells,bonus_shells,created_at FROM top_up_orders WHERE id = ? FOR UPDATE",
                (rs, row) -> new Order(rs.getObject(1, UUID.class), rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getTimestamp(6).toInstant()), id);
        if (rows.isEmpty()) throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        return rows.getFirst();
    }

    private void lockOperator(UUID actor) {
        var roles = jdbc.query("""
                SELECT g.user_id FROM admin_operator_grants g JOIN users u ON u.id = g.user_id
                WHERE g.user_id = ? AND g.active = TRUE AND g.can_recover = TRUE AND u.status = 'ACTIVE'
                FOR NO KEY UPDATE OF g,u
                """, (rs, row) -> rs.getObject(1, UUID.class), actor);
        if (roles.isEmpty()) throw new ApiException(ErrorCode.FORBIDDEN);
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > 512) throw new ApiException(ErrorCode.INVALID_REQUEST);
    }
    private void append(UUID actor, String action, UUID id) {
        audit.append(new AuditLogPort.Entry(actor, action, "REFUND_CASE", id, "CUSTOMER_INQUIRY", TraceContext.currentId(), clock.instant()));
    }
    private String hash(String text) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException failure) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    private record Binding(UUID id, String hash) {}
    private record ExecutionBinding(UUID caseId, RemainingTopUpRefund quote) {}
    private record Order(UUID customer, String status, int amount, int paid, int bonus, Instant created) {}
    @io.swagger.v3.oas.annotations.media.Schema(name = "AdminRefundQuote", requiredProperties = {"caseId","orderId","originalAmount","amount","paidShells","bonusShells","feeAmount"})
    public record Quote(UUID caseId, UUID orderId, int originalAmount, int amount, int paidShells, int bonusShells, int feeAmount) {}
    @io.swagger.v3.oas.annotations.media.Schema(name = "AdminRefundCase", requiredProperties = {"caseId","orderId","customerUserId","supportReference","receivedAt","registeredAt","refundId","state","amount","paidShells","bonusShells","completedAt"})
    public record CaseView(UUID caseId, UUID orderId, UUID customerUserId, String supportReference, Instant receivedAt,
                           Instant registeredAt,
                           @io.swagger.v3.oas.annotations.media.Schema(types = {"string","null"}, format = "uuid") UUID refundId,
                           String state,
                           @io.swagger.v3.oas.annotations.media.Schema(types = {"integer","null"}) Integer amount,
                           @io.swagger.v3.oas.annotations.media.Schema(types = {"integer","null"}) Integer paidShells,
                           @io.swagger.v3.oas.annotations.media.Schema(types = {"integer","null"}) Integer bonusShells,
                           @io.swagger.v3.oas.annotations.media.Schema(types = {"string","null"}, format = "date-time") Instant completedAt) {}
}
