package com.sajuppugi.payment.infrastructure;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.payment.application.TopUpExecutionProperties;
import com.sajuppugi.payment.application.TopUpUseCase.NextAction;
import com.sajuppugi.payment.application.TopUpUseCase.OrderView;
import com.sajuppugi.payment.domain.TopUpConfirmationIntent;
import com.sajuppugi.payment.domain.TopUpOrder;
import com.sajuppugi.payment.port.PaymentProviderPort;
import com.sajuppugi.wallet.application.WalletTopUpPort;
import com.sajuppugi.wallet.domain.WalletBalance;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Repository
public class JdbcTopUpExecutions {
    private final JdbcTemplate jdbc;
    private final JdbcTopUpOrders orders;
    private final WalletTopUpPort wallet;
    private final TopUpExecutionProperties properties;
    public JdbcTopUpExecutions(JdbcTemplate jdbc, JdbcTopUpOrders orders, WalletTopUpPort wallet,
            TopUpExecutionProperties properties) {
        this.jdbc = jdbc; this.orders = orders; this.wallet = wallet; this.properties = properties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
    public OrderView view(UUID user, UUID id) {
        var order = orders.findOwned(user, id).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        var receipts = jdbc.query("SELECT paid_balance, bonus_balance FROM wallet_top_up_receipts WHERE order_id = ? AND wallet_user_id = ?",
                (rs, row) -> new WalletBalance(rs.getInt(1), rs.getInt(2)), id, user);
        var refunds = jdbc.query("SELECT state FROM top_up_refunds WHERE order_id = ?", (rs, row) -> rs.getString(1), id);
        if (!refunds.isEmpty()) {
            String state = refunds.getFirst();
            if (!"REFUNDED".equals(state)) order = new TopUpOrder(order.id(), order.userId(), order.productCode(),
                    order.amountKrw(), order.paidShells(), order.bonusShells(), TopUpOrder.Status.REFUND_PENDING, order.createdAt());
            return new OrderView(order, !List.of("REFUNDED", "REVIEW_REQUIRED").contains(state), null,
                    "REFUNDED".equals(state) ? NextAction.NONE : "REVIEW_REQUIRED".equals(state) ? NextAction.CONTACT_SUPPORT : NextAction.POLL,
                    "REFUNDED".equals(state) ? null : "REFUND_PENDING");
        }
        if (order.status() == TopUpOrder.Status.CREDITED) {
            if (receipts.isEmpty()) throw new IllegalStateException("Credited order has no receipt");
            return new OrderView(order, false, receipts.getFirst(), NextAction.VIEW_WALLET, null);
        }
        var executions = jdbc.query("SELECT state, failure_code FROM top_up_executions WHERE order_id = ?",
                (rs, row) -> new PublicExecution(rs.getString(1), rs.getString(2)), id);
        if (!executions.isEmpty()) {
            var execution = executions.getFirst();
            if (execution.state().equals("REVIEW_REQUIRED")) {
                return new OrderView(order, false, null, NextAction.CONTACT_SUPPORT, "PAYMENT_REVIEW_REQUIRED");
            }
            if (!execution.state().equals("COMPLETED")) return new OrderView(order, true, null, NextAction.POLL, null);
            return new OrderView(order, false, null, NextAction.RETRY_PAYMENT, execution.failureCode());
        }
        return new OrderView(order, false, null, NextAction.NONE, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true, timeout = 5)
    public List<UUID> due(Instant now) {
        return jdbc.query("""
                SELECT order_id FROM top_up_executions WHERE state IN ('READY', 'RETRY', 'IN_FLIGHT')
                AND next_attempt_at <= ? ORDER BY next_attempt_at, order_id LIMIT ?
                """, (rs, row) -> rs.getObject(1, UUID.class), Timestamp.from(now), properties.batchSize());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public Optional<Lease> claim(UUID id, UUID user, boolean clientConfirmation, Instant now) {
        // All mutations acquire order, execution, then wallet/lot locks in that order.
        var orderRows = jdbc.query("SELECT * FROM top_up_orders WHERE id = ? FOR UPDATE",
                (rs, row) -> new OrderOwner(rs.getObject("user_id", UUID.class), rs.getString("status")), id);
        if (orderRows.isEmpty() || (user != null && !orderRows.getFirst().user().equals(user))) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        if (!List.of("PAYMENT_PENDING", "PAID").contains(orderRows.getFirst().status())) return Optional.empty();
        var states = jdbc.query("SELECT * FROM top_up_executions WHERE order_id = ? FOR UPDATE",
                (rs, row) -> new Execution(rs.getString("state"), rs.getInt("attempts"),
                        rs.getBoolean("confirmation_started"), rs.getTimestamp("next_attempt_at").toInstant()), id);
        if (states.isEmpty()) return Optional.empty();
        var state = states.getFirst();
        if (List.of("COMPLETED", "REVIEW_REQUIRED").contains(state.state()) || state.next().isAfter(now)) return Optional.empty();
        if (state.attempts() >= properties.maxAttempts()) {
            finish(id, "REVIEW_REQUIRED", "PAYMENT_REVIEW_REQUIRED", now, now);
            return Optional.empty();
        }
        boolean confirm = clientConfirmation && !state.confirmationStarted();
        var intent = jdbc.query("SELECT * FROM top_up_confirmation_intents WHERE order_id = ?",
                (rs, row) -> intent(rs), id).getFirst();
        UUID token = UUID.randomUUID();
        Instant until = now.plus(properties.leaseDuration());
        jdbc.update("""
                UPDATE top_up_executions SET state = 'IN_FLIGHT', attempts = attempts + 1,
                confirmation_started = ?, lease_token = ?, lease_until = ?, next_attempt_at = ?, updated_at = ?
                WHERE order_id = ?
                """, state.confirmationStarted() || confirm, token, Timestamp.from(until), Timestamp.from(until), Timestamp.from(now), id);
        return Optional.of(new Lease(intent, token, confirm));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public boolean apply(Lease lease, PaymentProviderPort.Result result, Instant now) {
        UUID id = lease.intent().orderId();
        var owner = jdbc.query("SELECT user_id, status, paid_shells, bonus_shells FROM top_up_orders WHERE id = ? FOR UPDATE",
                (rs, row) -> new GrantOrder(rs.getObject(1, UUID.class), rs.getString(2), rs.getInt(3), rs.getInt(4)), id).getFirst();
        if (!List.of("PAYMENT_PENDING", "PAID").contains(owner.status())) return false;
        var attempts = jdbc.query("""
                SELECT attempts FROM top_up_executions WHERE order_id = ? AND state = 'IN_FLIGHT'
                AND lease_token = ? AND lease_until > ? FOR UPDATE
                """, (rs, row) -> rs.getInt(1), id, lease.token(), Timestamp.from(now));
        if (attempts.isEmpty()) return false;
        if (result != null && result.approves(lease.intent())) {
            wallet.credit(owner.user(), id, owner.paid(), owner.bonus());
            jdbc.update("UPDATE top_up_orders SET status = 'CREDITED' WHERE id = ?", id);
            finish(id, "COMPLETED", null, now, now);
        } else if (result != null && result.matches(lease.intent())
                && (result.outcome() == PaymentProviderPort.Outcome.DECLINED || result.outcome() == PaymentProviderPort.Outcome.CANCELED)) {
            boolean canceled = result.outcome() == PaymentProviderPort.Outcome.CANCELED;
            jdbc.update("UPDATE top_up_orders SET status = ? WHERE id = ?", canceled ? "CANCELED" : "FAILED", id);
            finish(id, "COMPLETED", canceled ? "PAYMENT_CANCELED" : "PAYMENT_DECLINED", now, now);
        } else {
            boolean review = attempts.getFirst() >= properties.maxAttempts();
            finish(id, review ? "REVIEW_REQUIRED" : "RETRY", review ? "PAYMENT_REVIEW_REQUIRED" : null,
                    now.plus(properties.retryDelay()), now);
        }
        return true;
    }

    private void finish(UUID id, String state, String failure, Instant next, Instant now) {
        jdbc.update("""
                UPDATE top_up_executions SET state = ?, lease_token = NULL, lease_until = NULL,
                failure_code = ?, next_attempt_at = ?, updated_at = ? WHERE order_id = ?
                """, state, failure, Timestamp.from(next), Timestamp.from(now), id);
    }

    private TopUpConfirmationIntent intent(ResultSet rs) throws SQLException {
        return new TopUpConfirmationIntent(rs.getObject("order_id", UUID.class), rs.getString("payment_key_hash"),
                rs.getInt("amount_krw"), rs.getTimestamp("created_at").toInstant());
    }

    public record Lease(TopUpConfirmationIntent intent, UUID token, boolean confirm) {}
    private record Execution(String state, int attempts, boolean confirmationStarted, Instant next) {}
    private record OrderOwner(UUID user, String status) {}
    private record GrantOrder(UUID user, String status, int paid, int bonus) {}
    private record PublicExecution(String state, String failureCode) {}
}
