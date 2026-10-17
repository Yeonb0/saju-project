package com.sajuppugi.payment.application;

import com.sajuppugi.payment.infrastructure.JdbcTopUpExecutions;
import com.sajuppugi.payment.port.PaymentProviderPort;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "app.toss.recovery.enabled", havingValue = "true")
public class TopUpRecoveryScheduler {
    private static final Logger log = LoggerFactory.getLogger(TopUpRecoveryScheduler.class);
    private final JdbcTopUpExecutions executions;
    private final TopUpPaymentService payments;
    private final Clock clock;
    public TopUpRecoveryScheduler(JdbcTopUpExecutions executions, TopUpPaymentService payments, Clock clock,
            PaymentProviderPort requiredProvider) {
        this.executions = executions; this.payments = payments; this.clock = clock;
    }
    @Scheduled(scheduler = "paymentOperationsScheduler", fixedDelayString = "${app.toss.recovery.poll-delay:30s}", initialDelayString = "${app.toss.recovery.poll-delay:30s}")
    public void poll() {
        for (var order : executions.due(clock.instant())) {
            try { payments.recover(order); }
            catch (RuntimeException failure) {
                log.warn("Payment recovery failed: orderId={}, type={}", order, failure.getClass().getSimpleName());
            }
        }
    }
}
