package com.sajuppugi.gift.infrastructure;

import com.sajuppugi.wallet.application.WalletPurchaseSettlementPort;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.gift.purchases-enabled", havingValue = "true")
public class JdbcGiftSettlement implements WalletPurchaseSettlementPort {
    private final JdbcTemplate jdbc;
    public JdbcGiftSettlement(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override @Transactional(readOnly = true, timeout = 5)
    public boolean fulfilled(UUID buyer, UUID quote, UUID transaction) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM gift_purchase_work w JOIN gift_order_drafts d ON d.id = w.order_id JOIN gift_recipient_receipts r ON r.order_id = w.order_id WHERE d.user_id = ? AND d.quote_id = ? AND w.wallet_transaction_id = ? AND w.status = 'ACTIVE' AND w.first_provided_at IS NOT NULL AND r.state = 'PROVIDED'",
                Integer.class, buyer, quote, transaction) == 1;
    }
}
