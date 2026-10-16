package com.sajuppugi.payment.application;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Resumes direct-payment outcomes only by provider lookup after an interrupted confirmation. */
@Component
@ConditionalOnProperty(name = "app.toss.operations-enabled", havingValue = "true")
public class DirectPaymentReconciliationScheduler {
    private static final Logger log = LoggerFactory.getLogger(DirectPaymentReconciliationScheduler.class);
    private static final int BATCH_SIZE = 20;

    private final DirectPaymentConfirmationService confirmations;

    public DirectPaymentReconciliationScheduler(DirectPaymentConfirmationService confirmations) {
        this.confirmations = confirmations;
    }

    @Scheduled(scheduler = "paymentOperationsScheduler", fixedDelayString = "${app.toss.direct-payment-reconciliation-delay:30s}",
            initialDelayString = "${app.toss.direct-payment-reconciliation-delay:30s}")
    public void run() {
        for (UUID batchId : confirmations.dueForReconciliation(BATCH_SIZE)) {
            try {
                confirmations.reconcile(batchId);
            } catch (RuntimeException failure) {
                log.warn("Direct payment reconciliation deferred: batchId={}, type={}",
                        batchId, failure.getClass().getSimpleName());
            }
        }
    }
}
