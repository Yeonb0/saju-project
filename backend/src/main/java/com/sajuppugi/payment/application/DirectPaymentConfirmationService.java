package com.sajuppugi.payment.application;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.payment.domain.DirectPaymentBatch;
import com.sajuppugi.payment.domain.DirectPaymentConfirmationIntent;
import com.sajuppugi.payment.port.PaymentProviderPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Coordinates a direct-payment confirmation.  Every JDBC method below is short;
 * provider.confirm/lookup is deliberately invoked after its transaction returns.
 */
@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class DirectPaymentConfirmationService {
    private static final int MAX_ATTEMPTS = 3;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final org.springframework.beans.factory.ObjectProvider<PaymentProviderPort> providers;
    private final TransactionTemplate transactions;

    public DirectPaymentConfirmationService(JdbcTemplate jdbc, Clock clock,
            org.springframework.beans.factory.ObjectProvider<PaymentProviderPort> providers, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc; this.clock = clock; this.providers = providers;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactions.setTimeout(5);
    }

    public DirectPaymentBatch confirm(UUID user, UUID batchId, String paymentKey, int amount, String idempotencyKey) {
        DirectPaymentConfirmationIntent intent = required(() -> bind(user, batchId, paymentKey, amount, idempotencyKey));
        Optional<Lease> lease = required(() -> claim(user, batchId, true));
        if (lease.isPresent()) {
            PaymentProviderPort.Result result;
            try {
                var provider = providers.getIfAvailable();
                if (provider == null) throw new ApiException(ErrorCode.PAYMENT_FULFILLMENT_UNAVAILABLE);
                result = lease.get().confirm()
                        ? provider.confirm(batchId, new TopUpUseCase.Confirmation(paymentKey, amount))
                        : provider.lookup(batchId);
            } catch (ApiException unavailable) { throw unavailable; }
            catch (RuntimeException failure) {
                result = new PaymentProviderPort.Result(PaymentProviderPort.Outcome.UNKNOWN, batchId, null, 0, null,
                        "PAYMENT_TRANSPORT_ERROR");
            }
            PaymentProviderPort.Result resolved = result;
            required(() -> { apply(lease.get(), resolved); return null; });
        }
        return required(() -> owned(user, batchId));
    }

    /** Worker/scheduler entry point: an already-bound payment is only looked up, never confirmed again. */
    public void reconcile(UUID batchId) {
        Optional<Lease> lease = required(() -> claim(null, batchId, false));
        if (lease.isEmpty()) return;
        PaymentProviderPort.Result result;
        try {
            var provider = providers.getIfAvailable();
            if (provider == null) return;
            result = provider.lookup(batchId);
        } catch (RuntimeException failure) {
            result = new PaymentProviderPort.Result(PaymentProviderPort.Outcome.UNKNOWN, batchId, null, 0, null,
                    "PAYMENT_TRANSPORT_ERROR");
        }
        PaymentProviderPort.Result resolved = result;
        required(() -> { apply(lease.get(), resolved); return null; });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    protected DirectPaymentConfirmationIntent bind(UUID user, UUID batchId, String paymentKey, int amount, String requestKey) {
        if (user == null || batchId == null || invalid(paymentKey, 200) || amount <= 0 || invalid(requestKey, 512))
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        Instant now = now(); String paymentHash = hash(paymentKey), requestHash = hash(requestKey);
        var batches = jdbc.query("SELECT total_amount_krw,status FROM direct_payment_batches WHERE id=? AND buyer_user_id=? FOR UPDATE",
                (rs, n) -> new BatchState(rs.getInt(1), rs.getString(2)), batchId, user);
        if (batches.isEmpty()) throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        BatchState batch = batches.getFirst();
        if (batch.amount() != amount) throw new ApiException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        var used = jdbc.query("SELECT batch_id FROM direct_payment_confirmation_requests WHERE buyer_user_id=? AND key_hash=?",
                (rs,n) -> rs.getObject(1, UUID.class), user, requestHash);
        if (!used.isEmpty() && !used.getFirst().equals(batchId)) throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        var intents = jdbc.query("SELECT payment_key_hash,amount_krw,created_at FROM direct_payment_confirmation_intents WHERE batch_id=?",
                (rs,n) -> new DirectPaymentConfirmationIntent(batchId, rs.getString(1), rs.getInt(2), rs.getTimestamp(3).toInstant()), batchId);
        DirectPaymentConfirmationIntent intent;
        if (!intents.isEmpty()) {
            intent = intents.getFirst();
            if (!intent.paymentKeyHash().equals(paymentHash) || intent.amountKrw() != amount)
                throw new ApiException(used.isEmpty() ? ErrorCode.PAYMENT_CONFIRMATION_CONFLICT : ErrorCode.IDEMPOTENCY_KEY_REUSED);
        } else {
            if (!"PAYMENT_PENDING".equals(batch.status())) throw new ApiException(ErrorCode.PAYMENT_ORDER_NOT_CONFIRMABLE);
            intent = new DirectPaymentConfirmationIntent(batchId, paymentHash, amount, now);
            jdbc.update("INSERT INTO direct_payment_confirmation_intents(batch_id,payment_key_hash,amount_krw,created_at) VALUES (?,?,?,?)",
                    batchId, paymentHash, amount, java.sql.Timestamp.from(now));
            jdbc.update("INSERT INTO direct_payment_executions(batch_id,state,next_attempt_at,updated_at) VALUES (?,'READY',?,?)",
                    batchId, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
        }
        if (used.isEmpty()) jdbc.update("INSERT INTO direct_payment_confirmation_requests(buyer_user_id,key_hash,batch_id,created_at) VALUES (?,?,?,?)",
                user, requestHash, batchId, java.sql.Timestamp.from(now));
        return intent;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    protected Optional<Lease> claim(UUID user, UUID batchId, boolean clientConfirmation) {
        Instant now = now();
        var batches = jdbc.query("SELECT buyer_user_id,status FROM direct_payment_batches WHERE id=? FOR UPDATE",
                (rs,n) -> new OwnerState(rs.getObject(1, UUID.class), rs.getString(2)), batchId);
        if (batches.isEmpty() || user != null && !batches.getFirst().owner().equals(user)) throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        if (!"PAYMENT_PENDING".equals(batches.getFirst().status())) return Optional.empty();
        var executions = jdbc.query("SELECT state,attempts,confirmation_started,next_attempt_at FROM direct_payment_executions WHERE batch_id=? FOR UPDATE",
                (rs,n) -> new Execution(rs.getString(1),rs.getInt(2),rs.getBoolean(3),rs.getTimestamp(4).toInstant()), batchId);
        if (executions.isEmpty()) return Optional.empty();
        Execution execution = executions.getFirst();
        if (List.of("COMPLETED", "REVIEW_REQUIRED").contains(execution.state()) || execution.next().isAfter(now)) return Optional.empty();
        if (execution.attempts() >= MAX_ATTEMPTS) { finish(batchId, "REVIEW_REQUIRED", "PAYMENT_REVIEW_REQUIRED", now); return Optional.empty(); }
        var intents = jdbc.query("SELECT payment_key_hash,amount_krw,created_at FROM direct_payment_confirmation_intents WHERE batch_id=?",
                (rs,n) -> new DirectPaymentConfirmationIntent(batchId,rs.getString(1),rs.getInt(2),rs.getTimestamp(3).toInstant()), batchId);
        UUID token = UUID.randomUUID(); Instant until = now.plusSeconds(30); boolean confirm = clientConfirmation && !execution.confirmationStarted();
        jdbc.update("UPDATE direct_payment_executions SET state='IN_FLIGHT',attempts=attempts+1,confirmation_started=?,lease_token=?,lease_until=?,next_attempt_at=?,updated_at=? WHERE batch_id=?",
                execution.confirmationStarted() || confirm, token, java.sql.Timestamp.from(until), java.sql.Timestamp.from(until), java.sql.Timestamp.from(now), batchId);
        return Optional.of(new Lease(intents.getFirst(), token, confirm));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    protected void apply(Lease lease, PaymentProviderPort.Result result) {
        Instant now = now(); UUID id = lease.intent().batchId();
        var attempts = jdbc.query("SELECT attempts FROM direct_payment_executions WHERE batch_id=? AND state='IN_FLIGHT' AND lease_token=? AND lease_until>? FOR UPDATE",
                (rs,n)->rs.getInt(1), id, lease.token(), java.sql.Timestamp.from(now));
        if (attempts.isEmpty()) return;
        jdbc.query("SELECT id FROM direct_payment_batches WHERE id=? FOR UPDATE", (rs,n)->rs.getObject(1, UUID.class), id);
        if (result != null && result.approves(new com.sajuppugi.payment.domain.TopUpConfirmationIntent(id, lease.intent().paymentKeyHash(), lease.intent().amountKrw(), lease.intent().createdAt()))) {
            jdbc.update("UPDATE direct_payment_batches SET status='PAID' WHERE id=? AND status='PAYMENT_PENDING'", id);
            finish(id, "COMPLETED", null, now);
        } else if (result != null && result.matches(new com.sajuppugi.payment.domain.TopUpConfirmationIntent(id, lease.intent().paymentKeyHash(), lease.intent().amountKrw(), lease.intent().createdAt()))
                && (result.outcome() == PaymentProviderPort.Outcome.DECLINED || result.outcome() == PaymentProviderPort.Outcome.CANCELED)) {
            jdbc.update("UPDATE direct_payment_batches SET status=? WHERE id=? AND status='PAYMENT_PENDING'", result.outcome() == PaymentProviderPort.Outcome.CANCELED ? "CANCELED" : "FAILED", id);
            finish(id, "COMPLETED", result.outcome() == PaymentProviderPort.Outcome.CANCELED ? "PAYMENT_CANCELED" : "PAYMENT_DECLINED", now);
        } else {
            boolean review = attempts.getFirst() >= MAX_ATTEMPTS;
            if (review) jdbc.update("UPDATE direct_payment_batches SET status='REVIEW_REQUIRED' WHERE id=? AND status='PAYMENT_PENDING'", id);
            finish(id, review ? "REVIEW_REQUIRED" : "RETRY", review ? "PAYMENT_REVIEW_REQUIRED" : null,
                    review ? now : now.plusSeconds(10));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true, timeout = 5)
    protected DirectPaymentBatch owned(UUID user, UUID id) {
        return jdbc.query("SELECT * FROM direct_payment_batches WHERE id=? AND buyer_user_id=?", (rs,n) -> new DirectPaymentBatch(rs.getObject("id", UUID.class), rs.getObject("buyer_user_id", UUID.class), rs.getObject("quote_id", UUID.class), rs.getString("product_code"), rs.getString("product_name"), rs.getInt("unit_amount_krw"), rs.getInt("quantity"), rs.getInt("total_amount_krw"), DirectPaymentBatch.Status.valueOf(rs.getString("status")), rs.getTimestamp("created_at").toInstant()), id, user).stream().findFirst().orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
    }
    private void finish(UUID id, String state, String failure, Instant next) { jdbc.update("UPDATE direct_payment_executions SET state=?,lease_token=NULL,lease_until=NULL,failure_code=?,next_attempt_at=?,updated_at=? WHERE batch_id=?", state,failure,java.sql.Timestamp.from(next),java.sql.Timestamp.from(now()),id); }
    private Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }
    private <T> T required(java.util.concurrent.Callable<T> work) {
        return transactions.execute(status -> { try { return work.call(); } catch (RuntimeException e) { throw e; } catch (Exception e) { throw new IllegalStateException(e); } });
    }
    private boolean invalid(String value, int max) { return value == null || value.isBlank() || value.length() > max; }
    private String hash(String raw) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }
    private record BatchState(int amount, String status) {} private record OwnerState(UUID owner, String status) {} private record Execution(String state,int attempts,boolean confirmationStarted,Instant next) {} private record Lease(DirectPaymentConfirmationIntent intent, UUID token, boolean confirm) {}
}
