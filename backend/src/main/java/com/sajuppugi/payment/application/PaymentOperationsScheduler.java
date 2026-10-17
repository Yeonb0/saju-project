package com.sajuppugi.payment.application;

import com.sajuppugi.payment.infrastructure.JdbcCashRefunds;
import com.sajuppugi.payment.infrastructure.JdbcPaymentReconciliation;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.toss.operations-enabled", havingValue = "true")
public class PaymentOperationsScheduler {
    private static final Logger log = LoggerFactory.getLogger(PaymentOperationsScheduler.class);
    private final CashRefundService refunds;
    private final JdbcCashRefunds refundJobs;
    private final PaymentOperationsService operations;
    private final JdbcPaymentReconciliation jobs;
    private final Clock clock;
    private UUID cursor;
    public PaymentOperationsScheduler(CashRefundService refunds, JdbcCashRefunds refundJobs, PaymentOperationsService operations, JdbcPaymentReconciliation jobs, Clock clock) {
        this.refunds = refunds; this.refundJobs = refundJobs; this.operations = operations; this.jobs = jobs; this.clock = clock;
    }
    @Scheduled(scheduler = "paymentOperationsScheduler", fixedDelayString = "${app.toss.operations-delay:60000}")
    public void run() {
        for (var order : refundJobs.due(clock.instant())) {
            try { refunds.recover(order); } catch (RuntimeException failure) { log.warn("Cash refund deferred: orderId={}", order); }
        }
        var orders = jobs.scanAfter(cursor);
        for (var order : orders) { jobs.signal(order, clock.instant()); cursor = order; }
        if (orders.size() < 100) cursor = null;
        for (var order : jobs.due(clock.instant())) {
            try { operations.reconcile(order); } catch (RuntimeException failure) { log.warn("Payment reconciliation deferred: orderId={}", order); }
        }
    }
}
