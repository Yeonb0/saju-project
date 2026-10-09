package com.sajuppugi.payment.infrastructure;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.payment.domain.TopUpConfirmationIntent;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcTopUpConfirmations {
    private final JdbcTemplate jdbc;

    public JdbcTopUpConfirmations(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true, timeout = 5)
    public Optional<UUID> findRequestOrder(UUID user, String keyHash) {
        return jdbc.query("SELECT order_id FROM top_up_confirmation_requests WHERE user_id = ? AND key_hash = ?",
                (rs, row) -> rs.getObject("order_id", UUID.class), user, keyHash).stream().findFirst();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public TopUpConfirmationIntent bind(UUID user, UUID orderId, String paymentHash,
            int amount, String keyHash, Instant now) {
        // Serialize one order before inspecting or creating its immutable payment identity.
        var orders = jdbc.query("SELECT amount_krw, status FROM top_up_orders WHERE id = ? AND user_id = ? FOR UPDATE",
                (rs, row) -> new OrderState(rs.getInt("amount_krw"), rs.getString("status")), orderId, user);
        if (orders.isEmpty()) throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        var order = orders.getFirst();
        if (order.amount() != amount) throw new ApiException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);

        var requests = jdbc.query("SELECT order_id FROM top_up_confirmation_requests WHERE user_id = ? AND key_hash = ?",
                (rs, row) -> rs.getObject("order_id", UUID.class), user, keyHash);
        if (!requests.isEmpty() && !requests.getFirst().equals(orderId)) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
        var intents = jdbc.query("SELECT * FROM top_up_confirmation_intents WHERE order_id = ?",
                (rs, row) -> new TopUpConfirmationIntent(rs.getObject("order_id", UUID.class),
                        rs.getString("payment_key_hash"), rs.getInt("amount_krw"),
                        rs.getTimestamp("created_at").toInstant()), orderId);
        TopUpConfirmationIntent intent;
        if (!intents.isEmpty()) {
            intent = intents.getFirst();
            if (!intent.paymentKeyHash().equals(paymentHash) || intent.amountKrw() != amount) {
                throw new ApiException(requests.isEmpty() ? ErrorCode.PAYMENT_CONFIRMATION_CONFLICT
                        : ErrorCode.IDEMPOTENCY_KEY_REUSED);
            }
        } else {
            if (!order.status().equals("PAYMENT_PENDING")) {
                throw new ApiException(ErrorCode.PAYMENT_ORDER_NOT_CONFIRMABLE);
            }
            intent = new TopUpConfirmationIntent(orderId, paymentHash, amount, now);
            jdbc.update("""
                    INSERT INTO top_up_confirmation_intents(order_id, payment_key_hash, amount_krw, created_at)
                    VALUES (?, ?, ?, ?)
                    """, orderId, paymentHash, amount, Timestamp.from(now));
        }
        if (requests.isEmpty()) {
            jdbc.update("""
                    INSERT INTO top_up_confirmation_requests(user_id, key_hash, order_id, created_at)
                    VALUES (?, ?, ?, ?)
                    """, user, keyHash, orderId, Timestamp.from(now));
        }
        return intent;
    }

    private record OrderState(int amount, String status) {}
}
