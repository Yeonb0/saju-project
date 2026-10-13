package com.sajuppugi.payment.application;

import com.sajuppugi.payment.domain.TopUpConfirmationIntent;
import com.sajuppugi.payment.infrastructure.JdbcCashRefunds;
import com.sajuppugi.payment.port.PaymentProviderPort;
import com.sajuppugi.wallet.application.WalletTopUpPort;
import com.sajuppugi.wallet.application.WalletOperationsService;
import java.time.Clock;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.MANDATORY, timeout = 10)
public class VerifiedPaymentRecoveryService {
    private final JdbcTemplate jdbc;
    private final JdbcCashRefunds refunds;
    private final WalletTopUpPort wallet;
    private final Clock clock;
    private final WalletOperationsService operations;
    public VerifiedPaymentRecoveryService(JdbcTemplate jdbc, JdbcCashRefunds refunds, WalletTopUpPort wallet, Clock clock, WalletOperationsService operations) { this.jdbc = jdbc; this.refunds = refunds; this.wallet = wallet; this.clock = clock; this.operations = operations; }
    public void lockOrder(UUID id) { jdbc.query("SELECT id FROM top_up_orders WHERE id = ? FOR UPDATE", (rs, row) -> rs.getObject(1, UUID.class), id); }
    public String apply(UUID id, PaymentProviderPort.Result evidence) {
        var orders = jdbc.query("SELECT user_id,status,amount_krw,paid_shells,bonus_shells FROM top_up_orders WHERE id = ? FOR UPDATE",
                (rs, row) -> new Order(rs.getObject(1, UUID.class), rs.getString(2), rs.getInt(3), rs.getInt(4), rs.getInt(5)), id);
        if (orders.isEmpty()) return "ORDER_NOT_FOUND";
        var order = orders.getFirst();
        var intents = jdbc.query("SELECT * FROM top_up_confirmation_intents WHERE order_id = ?", (rs, row) ->
                new TopUpConfirmationIntent(id, rs.getString("payment_key_hash"), rs.getInt("amount_krw"), rs.getTimestamp("created_at").toInstant()), id);
        if (intents.isEmpty() || evidence == null || !evidence.matches(intents.getFirst()) || evidence.amountKrw() != order.amount()) return "EVIDENCE_UNVERIFIED";
        if (refunds.resolveRemainingCancellation(id, evidence, clock.instant())) return "REFUNDED";
        if ("PAYMENT_PARTIAL_CANCEL_REVIEW".equals(evidence.safeFailureCode())) {
            refunds.flagPartialCancellation(id, clock.instant());
            return "MANUAL_REVIEW_REQUIRED";
        }
        if (evidence.approves(intents.getFirst())) {
            if ("CREDITED".equals(order.status())) {
                Integer pendingRefund = jdbc.queryForObject("SELECT COUNT(*) FROM top_up_refunds WHERE order_id = ? AND resolution IS NULL", Integer.class, id);
                return pendingRefund != null && pendingRefund > 0 ? "MANUAL_REVIEW_REQUIRED" : "ALREADY_CREDITED";
            }
            if (!List.of("PAYMENT_PENDING", "PAID").contains(order.status())) return "PROVIDER_STATE_MISMATCH";
            if (!operations.inspectForRecovery(order.user())) return "MANUAL_REVIEW_REQUIRED";
            wallet.credit(order.user(), id, order.paid(), order.bonus());
            jdbc.update("UPDATE top_up_orders SET status = 'CREDITED' WHERE id = ?", id);
            fenceExecution(id);
            return "CREDITED";
        }
        if (evidence.outcome() == PaymentProviderPort.Outcome.CANCELED) {
            if (List.of("CANCELED", "REFUNDED").contains(order.status())) return "ALREADY_CLOSED";
            if (List.of("PAYMENT_PENDING", "PAID").contains(order.status())) {
                if (jdbc.queryForObject("SELECT COUNT(*) FROM wallet_top_up_receipts WHERE order_id = ?", Integer.class, id) != 0) return "PROVIDER_STATE_MISMATCH";
                jdbc.update("UPDATE top_up_orders SET status = 'CANCELED' WHERE id = ?", id);
                fenceExecution(id);
                return "CANCELED";
            }
            if ("CREDITED".equals(order.status())) {
                if (!operations.inspectForRecovery(order.user())) return "MANUAL_REVIEW_REQUIRED";
                if (refunds.resolveReservedCancellation(id, evidence, clock.instant())) return "REFUNDED";
                return refunds.reconcileCanceled(id, evidence, clock.instant()) ? "REFUNDED" : "MANUAL_REVIEW_REQUIRED";
            }
        }
        return "EVIDENCE_UNVERIFIED";
    }
    private void fenceExecution(UUID order) {
        jdbc.update("UPDATE top_up_executions SET state = 'COMPLETED', lease_token = NULL, lease_until = NULL, failure_code = NULL, confirmation_started = TRUE, updated_at = ? WHERE order_id = ?",
                Timestamp.from(clock.instant()), order);
    }
    private record Order(UUID user, String status, int amount, int paid, int bonus) {}
}
