package com.sajuppugi.admin.application;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.common.logging.TraceContext;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ, timeout = 5)
public class AdminGiftQueries {
    private static final String SELECT = """
            SELECT d.id,d.user_id,d.quote_id,d.created_at,COALESCE(w.status,'CREATED') AS order_state,
                w.gift_id,w.wallet_transaction_id,w.charged_amount,w.first_provided_at,w.access_expires_at,
                w.failure_code,r.state AS recipient_state,r.submitted_at,r.review_at,j.state AS delivery_state,
                d.phone_purged_at,d.private_payload_purged_at,d.phone_cleanup_failures,d.private_cleanup_failures,
                c.state AS cipher_state,t.token_version,t.issued_at,t.reveal_until,t.revoked,
                (t.token_ciphertext IS NOT NULL) AS link_cipher_available,
                (d.recipient_phone_masked IS NOT NULL OR o.order_id IS NOT NULL) AS legacy_delivery
            FROM gift_order_drafts d LEFT JOIN gift_purchase_work w ON w.order_id = d.id
            LEFT JOIN gift_recipient_receipts r ON r.order_id = d.id
            LEFT JOIN gift_delivery_outbox o ON o.order_id = d.id
            LEFT JOIN gift_delivery_jobs j ON j.id = o.current_job_id
            LEFT JOIN gift_payload_reencryption_work c ON c.order_id = d.id
            LEFT JOIN gift_purchase_tokens t ON t.order_id = d.id
            """;
    private final JdbcTemplate jdbc;
    private final AdminAccessService access;
    private final AuditLogPort audit;
    private final Clock clock;
    public AdminGiftQueries(JdbcTemplate jdbc, AdminAccessService access, AuditLogPort audit, Clock clock) {
        this.jdbc = jdbc; this.access = access; this.audit = audit; this.clock = clock;
    }
    public AdminFinancialQueries.Page<Summary> list(UUID actor, UUID buyer, UUID after, int limit, boolean reviewOnly) {
        access.require(actor, false);
        if (limit < 1 || limit > 50) throw new ApiException(ErrorCode.INVALID_REQUEST);
        var rows = jdbc.query(SELECT + """
                WHERE (CAST(? AS UUID) IS NULL OR d.user_id = CAST(? AS UUID))
                AND (CAST(? AS UUID) IS NULL OR d.id > CAST(? AS UUID))
                AND (? = FALSE OR w.status = 'REVIEW_REQUIRED' OR j.state = 'REVIEW_REQUIRED'
                    OR r.state = 'REVIEW_REQUIRED' OR (r.state = 'ACCEPTED' AND r.review_at <= ?)
                    OR (d.recipient_phone_masked IS NOT NULL AND d.phone_purged_at IS NULL AND d.phone_cleanup_failures = 5)
                    OR (d.private_payload_purged_at IS NULL AND d.private_cleanup_failures = 5)
                    OR c.state = 'REVIEW_REQUIRED')
                ORDER BY d.id LIMIT ?
                """, (rs,row) -> summary(rs), buyer == null ? null : buyer.toString(), buyer == null ? null : buyer.toString(),
                after == null ? null : after.toString(), after == null ? null : after.toString(), reviewOnly, java.sql.Timestamp.from(clock.instant()), limit + 1);
        append(actor, "ADMIN_GIFT_LIST_VIEW", buyer == null ? actor : buyer);
        boolean hasNext = rows.size() > limit;
        var items = rows.stream().limit(limit).toList();
        return new AdminFinancialQueries.Page<>(items, hasNext, hasNext ? items.getLast().orderId() : null);
    }
    public Detail detail(UUID actor, UUID order) {
        access.require(actor, false);
        var rows = jdbc.query(SELECT + " WHERE d.id = ?", (rs,row) -> summary(rs), order);
        if (rows.isEmpty()) throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        var deliveries = jdbc.query("SELECT id,state,sends,lookups,submission_started,provider_accepted,next_attempt_at,lease_until,completed_at FROM gift_delivery_jobs WHERE order_id = ? ORDER BY created_at DESC,id DESC LIMIT 20",
                (rs,row) -> new Delivery(rs.getObject(1, UUID.class), rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getBoolean(5), rs.getBoolean(6),
                        instant(rs,"next_attempt_at"), instant(rs,"lease_until"), instant(rs,"completed_at")), order);
        var events = jdbc.query("SELECT id,actor_id,job_id,action,created_at FROM gift_delivery_events WHERE order_id = ? ORDER BY created_at DESC,id DESC LIMIT 50",
                (rs,row) -> new Event(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class), rs.getString(4), instant(rs,"created_at")), order);
        var maintenance = jdbc.query("SELECT kind,COUNT(*) AS cycles,MAX(created_at) AS last_requested_at FROM admin_gift_maintenance_requests WHERE order_id = ? GROUP BY kind ORDER BY kind",
                (rs,row) -> new Maintenance(rs.getString(1), rs.getInt(2), 3 - rs.getInt(2), instant(rs,"last_requested_at")), order);
        append(actor, "ADMIN_GIFT_DETAIL_VIEW", order);
        return new Detail(rows.getFirst(), deliveries, events, maintenance);
    }
    private Summary summary(ResultSet rs) throws SQLException {
        return new Summary(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class), rs.getObject("quote_id", UUID.class),
                rs.getObject("gift_id", UUID.class), rs.getString("order_state"), rs.getObject("wallet_transaction_id", UUID.class),
                rs.getObject("charged_amount", Integer.class), instant(rs,"created_at"), instant(rs,"first_provided_at"), instant(rs,"access_expires_at"),
                rs.getString("failure_code"), rs.getString("recipient_state"), instant(rs,"submitted_at"), instant(rs,"review_at"), rs.getString("delivery_state"),
                instant(rs,"phone_purged_at"), instant(rs,"private_payload_purged_at"), rs.getInt("phone_cleanup_failures"), rs.getInt("private_cleanup_failures"), rs.getString("cipher_state"),
                rs.getBoolean("legacy_delivery") ? "ALIMTALK_LEGACY" : "DIRECT_LINK",
                rs.getObject("token_version", Long.class), instant(rs,"issued_at"), instant(rs,"reveal_until"),
                rs.getBoolean("link_cipher_available") && !rs.getBoolean("revoked") && "ACTIVE".equals(rs.getString("order_state"))
                    && instant(rs,"reveal_until") != null && clock.instant().isBefore(instant(rs,"reveal_until"))
                    && instant(rs,"access_expires_at") != null && clock.instant().isBefore(instant(rs,"access_expires_at")));
    }
    private Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column); return value == null ? null : value.toInstant();
    }
    private void append(UUID actor, String action, UUID target) {
        audit.append(new AuditLogPort.Entry(actor, action, "GIFT_ORDER", target, "SUPPORT_VIEW", TraceContext.currentId(), clock.instant()));
    }
    public record Summary(UUID orderId, UUID buyerId, UUID quoteId, UUID giftId, String status, UUID walletTransactionId,
            Integer chargedShells, Instant createdAt, Instant firstProvidedAt, Instant accessExpiresAt, String failureCode,
            String recipientState, Instant submittedAt, Instant reviewAt, String deliveryState,
            Instant phonePurgedAt, Instant privatePayloadPurgedAt, int phoneCleanupFailures, int privateCleanupFailures, String cipherState,
            String deliveryMode, Long tokenVersion, Instant linkIssuedAt, Instant linkRevealUntil, boolean linkRetrievable) {}
    public record Delivery(UUID jobId, String state, int sends, int lookups, boolean submissionStarted, boolean providerAccepted,
            Instant nextAttemptAt, Instant leaseUntil, Instant completedAt) {}
    public record Event(UUID eventId, UUID actorId, UUID jobId, String action, Instant occurredAt) {}
    public record Maintenance(String kind, int requestedCycles, int remainingCycles, Instant lastRequestedAt) {}
    public record Detail(Summary order, List<Delivery> deliveries, List<Event> events, List<Maintenance> maintenance) {}
}
