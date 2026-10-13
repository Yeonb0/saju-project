package com.sajuppugi.gift.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.gift.application.GiftDeliveryUseCase;
import com.sajuppugi.gift.domain.GiftDeliveryPolicy;
import com.sajuppugi.gift.port.GiftAlimtalkProvider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@ConditionalOnProperty(name = "app.gift.purchases-enabled", havingValue = "true")
@Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
public class JdbcGiftDeliveryWork {
    private final JdbcTemplate jdbc;
    private final JdbcGiftOrders orders;
    private final JdbcGiftPurchaseWork work;
    private final GiftPayloadCipher cipher;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    public JdbcGiftDeliveryWork(JdbcTemplate jdbc, JdbcGiftOrders orders, JdbcGiftPurchaseWork work,
                               GiftPayloadCipher cipher, ObjectMapper mapper, Clock clock) {
        this.jdbc = jdbc; this.orders = orders; this.work = work; this.cipher = cipher; this.mapper = mapper; this.clock = clock;
    }
    public UUID manual(UUID buyer, UUID order, String key, String action) {
        var owned = orders.owned(buyer, order).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        var grant = work.get(order, true).orElseThrow(() -> new ApiException(ErrorCode.GIFT_DELIVERY_NOT_ALLOWED));
        if (key == null || key.isBlank() || key.length() > 512 || !List.of("RESEND", "REISSUE").contains(action))
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        String keyHash = cipher.fingerprint("gift-delivery-action|" + key);
        var existing = jdbc.query("SELECT order_id,action,job_id FROM gift_delivery_commands WHERE user_id = ? AND key_hash = ?",
                (rs, row) -> new Command(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class)), buyer, keyHash);
        if (!existing.isEmpty()) {
            var command = existing.getFirst();
            if (!command.order().equals(order) || !command.action().equals(action)) throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
            return command.job();
        }
        Instant now = clock.instant();
        var outbox = current(order);
        if (!usable(order, now) || !"ACTIVE".equals(grant.status())) throw new ApiException(ErrorCode.GIFT_DELIVERY_NOT_ALLOWED);
        var available = availability(order, outbox.state(), now);
        if (!available.canResend()) throw new ApiException(ErrorCode.GIFT_DELIVERY_LIMIT_EXCEEDED);
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String original = work.payload(order);
        if (original == null || original.isEmpty()) throw new ApiException(ErrorCode.GIFT_DELIVERY_NOT_ALLOWED);
        UUID job = UUID.randomUUID();
        String encrypted;
        try {
            encrypted = cipher.encrypt(mapper.writeValueAsString(Map.of("giftId", grant.giftId(), "token", token,
                    "orderPayload", cipher.decrypt(original, buyer + "|" + order))), "gift-delivery|" + outbox.id());
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) { throw new IllegalStateException("Gift delivery serialization failed"); }
        int rotated = jdbc.update("UPDATE gift_purchase_tokens SET token_hash = ?, revoked = FALSE WHERE order_id = ? AND revoked = FALSE", hash(token), order);
        if (rotated != 1) throw new ApiException(ErrorCode.GIFT_DELIVERY_NOT_ALLOWED);
        jdbc.update("INSERT INTO gift_delivery_jobs(id,order_id,state,next_attempt_at,created_at) VALUES (?,?,'PENDING',?,?)", job, order, Timestamp.from(now), Timestamp.from(now));
        jdbc.update("UPDATE gift_delivery_outbox SET current_job_id = ?,private_payload = ?,status = 'PENDING' WHERE order_id = ?", job, encrypted, order);
        jdbc.update("INSERT INTO gift_delivery_commands(user_id,key_hash,order_id,action,job_id,created_at) VALUES (?,?,?,?,?,?) ON CONFLICT DO NOTHING",
                buyer, keyHash, order, action, job, Timestamp.from(now));
        UUID bound = jdbc.queryForObject("SELECT job_id FROM gift_delivery_commands WHERE user_id = ? AND key_hash = ?", UUID.class, buyer, keyHash);
        if (!job.equals(bound)) throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        audit(order, buyer, job, "MANUAL_" + action, now);
        return job;
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true, timeout = 5)
    public GiftDeliveryUseCase.DeliveryView view(UUID buyer, UUID order) {
        orders.owned(buyer, order).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        var box = current(order);
        Instant now = clock.instant();
        var available = availability(order, box.state(), now);
        boolean valid = usable(order, now);
        return new GiftDeliveryUseCase.DeliveryView(GiftDeliveryUseCase.Status.valueOf(box.state()),
                valid && available.canResend(), available.remainingResends(), available.nextResendAt());
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true, timeout = 5)
    public List<UUID> due() {
        return jdbc.query("""
                SELECT id FROM gift_delivery_jobs WHERE state IN ('PENDING','RETRY','UNKNOWN','IN_FLIGHT')
                AND next_attempt_at <= ? ORDER BY next_attempt_at,id LIMIT 20
                """, (rs, row) -> rs.getObject(1, UUID.class), Timestamp.from(clock.instant()));
    }
    public Optional<Lease> claim(UUID jobId) {
        var orderIds = jdbc.query("SELECT order_id FROM gift_delivery_jobs WHERE id = ?", (rs, row) -> rs.getObject(1, UUID.class), jobId);
        if (orderIds.isEmpty()) return Optional.empty();
        UUID order = orderIds.getFirst();
        var grant = work.get(order, true).orElseThrow();
        var rows = jobs(jobId, true);
        if (rows.isEmpty()) return Optional.empty();
        var job = rows.getFirst();
        Instant now = clock.instant();
        if (!List.of("PENDING", "RETRY", "UNKNOWN", "IN_FLIGHT").contains(job.state()) || job.next().isAfter(now)) return Optional.empty();
        var box = current(order);
        if (!box.job().equals(jobId) || !usable(order, now)) {
            finish(job, "CANCELED", now);
            return Optional.empty();
        }
        boolean lookup = job.started();
        if ((lookup && job.lookups() >= 5) || (!lookup && job.sends() >= 4)) {
            finish(job, "REVIEW_REQUIRED", now);
            return Optional.empty();
        }
        UUID lease = UUID.randomUUID(); Instant until = now.plusSeconds(180);
        jdbc.update("""
                UPDATE gift_delivery_jobs SET state = 'IN_FLIGHT',submission_started = TRUE,sends = ?,lookups = ?,
                lease_token = ?,lease_until = ?,next_attempt_at = ? WHERE id = ?
                """, job.sends() + (lookup ? 0 : 1), job.lookups() + (lookup ? 1 : 0), lease,
                Timestamp.from(until), Timestamp.from(until), jobId);
        audit(order, null, jobId, lookup ? "LOOKUP_CLAIMED" : "SEND_CLAIMED", now);
        String tokenHash = jdbc.queryForObject("SELECT token_hash FROM gift_purchase_tokens WHERE order_id = ? AND revoked = FALSE", String.class, order);
        return Optional.of(new Lease(jobId, order, lease, until, lookup, grant.giftId(), box.id(), box.payload(), tokenHash));
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true, timeout = 5)
    public boolean current(Lease lease) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM gift_delivery_jobs j JOIN gift_purchase_work w ON w.order_id = j.order_id
                JOIN gift_delivery_outbox o ON o.order_id = j.order_id
                WHERE j.id = ? AND j.state = 'IN_FLIGHT' AND j.lease_token = ? AND j.lease_until > ?
                AND w.status = 'ACTIVE' AND w.access_expires_at > ? AND o.current_job_id = j.id
                """, Integer.class, lease.jobId(), lease.token(), Timestamp.from(clock.instant()), Timestamp.from(clock.instant()));
        return count != null && count == 1;
    }
    /** Decode outside the DB transaction. Raw phone/token values never enter logs or persisted result fields. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public GiftAlimtalkProvider.Request request(Lease lease) {
        try {
            var delivery = mapper.readTree(cipher.decrypt(lease.payload(), "gift-delivery|" + lease.outboxId()));
            var payload = mapper.readTree(delivery.path("orderPayload").asText());
            String token = delivery.path("token").asText(); String phone = payload.path("recipientPhone").asText();
            String sender = payload.path("senderName").asText();
            if (!lease.giftId().toString().equals(delivery.path("giftId").asText()) || !token.matches("[A-Za-z0-9_-]{43}")
                    || !hash(token).equals(lease.tokenHash()) || !phone.matches("010[0-9]{8}") || sender.isBlank())
                throw new IllegalStateException("Gift delivery payload mismatch");
            return new GiftAlimtalkProvider.Request(lease.jobId(), lease.giftId(), phone, sender, token);
        } catch (Exception invalid) { throw new IllegalStateException("Gift delivery payload authentication failed"); }
    }
    public void apply(Lease lease, GiftAlimtalkProvider.Result result) {
        work.get(lease.orderId(), true).orElseThrow();
        var job = jobs(lease.jobId(), true).stream().findFirst().orElseThrow();
        Instant now = clock.instant();
        if (!"IN_FLIGHT".equals(job.state()) || !lease.token().equals(job.lease()) || job.until() == null
                || !job.until().isAfter(now) || !current(lease)) return;
        var outcome = result != null && result.matches(job.id()) ? result.outcome() : GiftAlimtalkProvider.Outcome.UNKNOWN;
        switch (outcome) {
            case DELIVERED -> finish(job, "SENT", now);
            case PERMANENT_REJECTION -> finish(job, "FAILED", now);
            case RETRYABLE_REJECTION, NOT_FOUND -> {
                if (job.accepted() || (outcome == GiftAlimtalkProvider.Outcome.NOT_FOUND && !lease.lookup())) {
                    finish(job, "REVIEW_REQUIRED", now);
                } else {
                    var next = GiftDeliveryPolicy.nextAutomaticAttempt(job.sends(), now);
                    if (next.isEmpty()) finish(job, "FAILED", now);
                    else {
                        jdbc.update("""
                                UPDATE gift_delivery_jobs SET state = 'RETRY',submission_started = FALSE,lookups = 0,
                                next_attempt_at = ?,lease_token = NULL,lease_until = NULL WHERE id = ?
                                """, Timestamp.from(next.get()), job.id());
                        audit(job.order(), null, job.id(), "RETRY_SCHEDULED", now);
                    }
                }
            }
            case ACCEPTED, UNKNOWN -> {
                if (job.lookups() >= 5) finish(job, "REVIEW_REQUIRED", now);
                else {
                    jdbc.update("""
                            UPDATE gift_delivery_jobs SET state = 'UNKNOWN',provider_accepted = ?,next_attempt_at = ?,
                            lease_token = NULL,lease_until = NULL WHERE id = ?
                            """, job.accepted() || outcome == GiftAlimtalkProvider.Outcome.ACCEPTED,
                            Timestamp.from(now.plusSeconds(30)), job.id());
                    audit(job.order(), null, job.id(), "LOOKUP_PENDING", now);
                }
            }
        }
    }
    private void finish(Job job, String state, Instant now) {
        jdbc.update("UPDATE gift_delivery_jobs SET state = ?,completed_at = ?,lease_token = NULL,lease_until = NULL WHERE id = ?",
                state, List.of("SENT", "FAILED", "CANCELED").contains(state) ? Timestamp.from(now) : null, job.id());
        String mirror = "REVIEW_REQUIRED".equals(state) ? "FAILED" : state;
        jdbc.update("UPDATE gift_delivery_outbox SET status = ? WHERE order_id = ? AND current_job_id = ?", mirror, job.order(), job.id());
        if (List.of("SENT", "FAILED", "CANCELED").contains(state))
            jdbc.update("UPDATE gift_delivery_outbox SET private_payload = '' WHERE order_id = ? AND current_job_id = ?", job.order(), job.id());
        audit(job.order(), null, job.id(), "DELIVERY_" + state, now);
    }
    private List<Job> jobs(UUID id, boolean lock) {
        return jdbc.query("SELECT * FROM gift_delivery_jobs WHERE id = ?" + (lock ? " FOR UPDATE" : ""),
                (rs, row) -> new Job(id, rs.getObject("order_id", UUID.class), rs.getString("state"), rs.getInt("sends"), rs.getInt("lookups"),
                        rs.getBoolean("submission_started"), rs.getBoolean("provider_accepted"), rs.getTimestamp("next_attempt_at").toInstant(),
                        rs.getObject("lease_token", UUID.class), rs.getTimestamp("lease_until") == null ? null : rs.getTimestamp("lease_until").toInstant()), id);
    }
    private Outbox current(UUID order) {
        var rows = jdbc.query("SELECT o.id,o.current_job_id,o.private_payload,j.state FROM gift_delivery_outbox o JOIN gift_delivery_jobs j ON j.id = o.current_job_id WHERE o.order_id = ?",
                (rs, row) -> new Outbox(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4)), order);
        return rows.stream().findFirst().orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
    }
    private boolean usable(UUID order, Instant now) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM gift_purchase_work WHERE order_id = ? AND status = 'ACTIVE' AND access_expires_at > ?", Integer.class, order, Timestamp.from(now)) == 1;
    }
    private GiftDeliveryPolicy.ManualAvailability availability(UUID order, String state, Instant now) {
        Instant day = now.atZone(GiftDeliveryPolicy.DAY_ZONE).toLocalDate().atStartOfDay(GiftDeliveryPolicy.DAY_ZONE).toInstant();
        int count = jdbc.queryForObject("SELECT COUNT(*) FROM gift_delivery_commands WHERE order_id = ? AND created_at >= ?", Integer.class, order, Timestamp.from(day));
        Timestamp last = jdbc.queryForObject("SELECT MAX(created_at) FROM gift_delivery_commands WHERE order_id = ?", Timestamp.class, order);
        return GiftDeliveryPolicy.manualAvailability(count, last == null ? null : last.toInstant(), !List.of("SENT", "FAILED").contains(state), now);
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void audit(UUID order, UUID actor, UUID job, String action, Instant now) {
        jdbc.update("INSERT INTO gift_delivery_events(id,order_id,actor_id,job_id,action,created_at) VALUES (?,?,?,?,?,?)", UUID.randomUUID(), order, actor, job, action, Timestamp.from(now));
    }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    private record Command(UUID order, String action, UUID job) {}
    private record Outbox(UUID id, UUID job, String payload, String state) {}
    private record Job(UUID id, UUID order, String state, int sends, int lookups, boolean started, boolean accepted, Instant next, UUID lease, Instant until) {}
    public record Lease(UUID jobId, UUID orderId, UUID token, Instant until, boolean lookup, UUID giftId, UUID outboxId, String payload, String tokenHash) {
        @Override public String toString() { return "GiftDeliveryLease[jobId=" + jobId + "]"; }
    }
}
