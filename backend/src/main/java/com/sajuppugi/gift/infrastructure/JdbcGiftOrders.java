package com.sajuppugi.gift.infrastructure;

import com.sajuppugi.gift.application.GiftOrderService.Order;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class JdbcGiftOrders {
    private final JdbcTemplate jdbc;
    public JdbcGiftOrders(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<Order> byKey(UUID user, String keyHash) {
        return find("o.id IN (SELECT order_id FROM gift_order_commands WHERE user_id = ? AND key_hash = ?)", user, keyHash);
    }
    public Optional<Order> byQuote(UUID user, UUID quote) { return find("o.user_id = ? AND o.quote_id = ?", user, quote); }
    public Optional<Order> owned(UUID user, UUID id) { return find("o.user_id = ? AND o.id = ?", user, id); }
    private Optional<Order> find(String predicate, Object... arguments) {
        return jdbc.query("SELECT o.* FROM gift_order_drafts o WHERE " + predicate, (rs, row) -> new Order(
                rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class), rs.getObject("quote_id", UUID.class),
                rs.getString("request_hash"), rs.getString("product_code"), rs.getString("product_name"),
                rs.getInt("shell_amount"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("expires_at").toInstant()), arguments)
                .stream().findFirst();
    }
    public void insert(Order order, String ciphertext) {
        jdbc.update("""
                INSERT INTO gift_order_drafts(id, user_id, quote_id, request_hash, product_code, product_name,
                    shell_amount, recipient_phone_masked, private_payload, status, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?, 'CREATED', ?, ?) ON CONFLICT DO NOTHING
                """, order.orderId(), order.userId(), order.quoteId(), order.requestHash(), order.productCode(),
                order.productName(), order.amount(), ciphertext,
                Timestamp.from(order.createdAt()), Timestamp.from(order.expiresAt()));
    }
    public void bind(UUID user, String keyHash, UUID order) {
        jdbc.update("INSERT INTO gift_order_commands(user_id, key_hash, order_id) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                user, keyHash, order);
    }
}
