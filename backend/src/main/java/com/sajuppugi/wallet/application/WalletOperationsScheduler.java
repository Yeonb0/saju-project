package com.sajuppugi.wallet.application;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.wallet.operations-enabled", havingValue = "true")
public class WalletOperationsScheduler {
    private static final Logger log = LoggerFactory.getLogger(WalletOperationsScheduler.class);
    private final WalletOperationsService operations;
    private UUID cursor;
    public WalletOperationsScheduler(WalletOperationsService operations) { this.operations = operations; }
    @Scheduled(fixedDelayString = "${app.wallet.operations-delay:60000}")
    public void run() {
        var users = operations.batchAfter(cursor, 100);
        for (var user : users) {
            try { if (!operations.inspectAndExpire(user)) log.warn("Wallet integrity review required: userId={}", user); }
            catch (RuntimeException failure) { log.warn("Wallet operations deferred: userId={}", user); }
            cursor = user;
        }
        if (users.size() < 100) cursor = null;
    }
}
