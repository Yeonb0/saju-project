package com.sajuppugi.gift.application;

import com.sajuppugi.gift.infrastructure.JdbcGiftPurchaseWork.Work;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Intent commits before debit; exceptions never turn an unknown wallet outcome into a fresh charge. */
@Service
@ConditionalOnProperty(name = "app.gift.purchases-enabled", havingValue = "true")
public class GiftPurchaseService {
    private static final Logger log = LoggerFactory.getLogger(GiftPurchaseService.class);
    private final GiftPurchaseTransactions transactions;
    public GiftPurchaseService(GiftPurchaseTransactions transactions) { this.transactions = transactions; }
    public Work purchase(UUID user, UUID order) {
        transactions.begin(user, order);
        return resume(user, order);
    }
    public Work resume(UUID user, UUID order) {
        // Establish ownership outside the failure handler: invalid targets must not mutate retry state.
        transactions.get(user, order);
        try { return transactions.activate(user, order); }
        catch (RuntimeException interrupted) { return transactions.failedAttempt(user, order); }
    }
    public void recoverDue() {
        for (var target : transactions.due()) {
            try { resume(target.userId(), target.orderId()); }
            catch (RuntimeException unavailable) {
                log.warn("Gift recovery persistence failed: orderId={}, type={}",
                        target.orderId(), unavailable.getClass().getSimpleName());
            }
        }
    }
}
