package com.sajuppugi.gift.application;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.gift.infrastructure.GiftPayloadCipher;
import com.sajuppugi.gift.infrastructure.JdbcGiftOrders;
import com.sajuppugi.gift.infrastructure.JdbcGiftPurchaseWork;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "app.gift.purchases-enabled", havingValue = "true")
@Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
public class GiftCommandTransactions {
    private final JdbcTemplate jdbc;
    private final JdbcGiftOrders orders;
    private final JdbcGiftPurchaseWork work;
    private final GiftPayloadCipher cipher;
    private final Clock clock;
    public GiftCommandTransactions(JdbcTemplate jdbc, JdbcGiftOrders orders, JdbcGiftPurchaseWork work,
                                   GiftPayloadCipher cipher, Clock clock) {
        this.jdbc = jdbc; this.orders = orders; this.work = work; this.cipher = cipher; this.clock = clock;
    }
    public void purchase(UUID user, UUID order, String key) {
        owned(user, order);
        work.insert(order, clock.instant());
        work.get(order, true).orElseThrow();
        bind(user, order, "PURCHASE", key);
    }
    public GiftOrderService.Order owned(UUID user, UUID order) {
        return orders.owned(user, order).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void bind(UUID user, UUID order, String action, String key) {
        if (key == null || key.isBlank() || key.length() > 512) throw new ApiException(ErrorCode.INVALID_REQUEST);
        String hash = cipher.fingerprint("gift-action|" + key);
        jdbc.update("""
                INSERT INTO gift_action_commands(user_id,key_hash,order_id,action,created_at)
                VALUES (?,?,?,?,?) ON CONFLICT DO NOTHING
                """, user, hash, order, action, Timestamp.from(clock.instant()));
        var matches = jdbc.query("SELECT order_id,action FROM gift_action_commands WHERE user_id = ? AND key_hash = ?",
                (rs, row) -> order.equals(rs.getObject(1, UUID.class)) && action.equals(rs.getString(2)), user, hash);
        if (matches.size() != 1 || !matches.getFirst()) throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
    }
}
