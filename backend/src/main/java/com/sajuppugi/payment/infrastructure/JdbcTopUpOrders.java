package com.sajuppugi.payment.infrastructure;

import com.sajuppugi.payment.domain.TopUpOrder;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcTopUpOrders {
    private final JdbcTemplate jdbc;

    public JdbcTopUpOrders(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public void insert(TopUpOrder order, String keyHash) {
        jdbc.update("""
                INSERT INTO top_up_orders(id, user_id, key_hash, product_code, amount_krw,
                paid_shells, bonus_shells, status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, order.id(), order.userId(), keyHash, order.productCode(), order.amountKrw(),
                order.paidShells(), order.bonusShells(), order.status().name(), Timestamp.from(order.createdAt()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true, timeout = 5)
    public Optional<TopUpOrder> findByKey(UUID user, String hash) {
        return jdbc.query("SELECT * FROM top_up_orders WHERE user_id = ? AND key_hash = ?",
                (rs, row) -> map(rs), user, hash).stream().findFirst();
    }

    @Transactional(readOnly = true, timeout = 5)
    public Optional<TopUpOrder> findOwned(UUID user, UUID id) {
        return jdbc.query("SELECT * FROM top_up_orders WHERE user_id = ? AND id = ?",
                (rs, row) -> map(rs), user, id).stream().findFirst();
    }

    private TopUpOrder map(ResultSet rs) throws SQLException {
        return new TopUpOrder(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class),
                rs.getString("product_code"), rs.getInt("amount_krw"), rs.getInt("paid_shells"),
                rs.getInt("bonus_shells"), TopUpOrder.Status.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant());
    }
}
