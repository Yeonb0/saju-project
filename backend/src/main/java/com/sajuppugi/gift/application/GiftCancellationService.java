package com.sajuppugi.gift.application;

import com.sajuppugi.common.idempotency.IdempotencyKey;
import com.sajuppugi.wallet.application.WalletPurchaseRecoveryPort;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "app.gift.purchases-enabled", havingValue = "true")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class GiftCancellationService {
    private static final Logger log = LoggerFactory.getLogger(GiftCancellationService.class);
    private final GiftCancellationTransactions transactions;
    private final WalletPurchaseRecoveryPort wallets;
    public GiftCancellationService(GiftCancellationTransactions transactions, WalletPurchaseRecoveryPort wallets) {
        this.transactions = transactions; this.wallets = wallets;
    }
    public void cancel(UUID user, UUID order, String key) {
        transactions.reserve(user, order, key);
        resume(user, order);
    }
    public void resume(UUID user, UUID order) {
        var target = transactions.ready(user, order);
        if (target == null) return;
        try {
            wallets.compensatePurchase(user, target.quoteId(), target.transactionId(), "GIFT_CUSTOMER_CANCELED",
                    new IdempotencyKey("gift-cancel:" + order));
            transactions.complete(target);
        } catch (RuntimeException interrupted) { transactions.failed(user, order); }
    }
    public void recoverDue() {
        for (var target : transactions.due()) {
            try { resume(target.userId(), target.orderId()); }
            catch (RuntimeException unavailable) {
                log.warn("Gift return recovery failed: orderId={}, type={}", target.orderId(), unavailable.getClass().getSimpleName());
            }
        }
    }
}
