package com.sajuppugi.payment.application;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.payment.infrastructure.JdbcCashRefunds;
import com.sajuppugi.payment.port.PaymentProviderPort;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class CashRefundService {
    private final JdbcCashRefunds refunds;
    private final ObjectProvider<PaymentProviderPort> providers;
    private final CashRefundPolicy policy;
    private final Clock clock;
    public CashRefundService(JdbcCashRefunds refunds, ObjectProvider<PaymentProviderPort> providers, CashRefundPolicy policy, Clock clock) {
        this.refunds = refunds; this.providers = providers; this.policy = policy; this.clock = clock;
    }
    public JdbcCashRefunds.View request(UUID user, UUID order, String key) {
        if (!policy.enabled() || providers.getIfAvailable() == null) throw new ApiException(ErrorCode.PAYMENT_FULFILLMENT_UNAVAILABLE);
        try { refunds.prepare(user, order, key, clock.instant()); }
        catch (org.springframework.dao.DataIntegrityViolationException collision) {
            // Separate transaction: PostgreSQL aborts the failed unique-key transaction.
            refunds.prepare(user, order, key, clock.instant());
        }
        recover(order);
        return refunds.owned(user, order);
    }
    public void recover(UUID order) {
        var provider = providers.getIfAvailable();
        if (!policy.enabled() || provider == null) return;
        var lease = refunds.claim(order, clock.instant());
        if (lease.isEmpty()) return;
        var attempt = lease.get();
        PaymentProviderPort.Result result;
        try {
            result = attempt.first() ? cancel(provider, attempt) : provider.lookup(order);
            // A bounded repeat of the same provider idempotency key, never a new refund command.
            if (!attempt.first() && result != null && result.approves(attempt.intent()))
                result = cancel(provider, attempt);
        } catch (RuntimeException failure) { result = null; }
        refunds.apply(attempt, result, clock.instant());
    }
    public JdbcCashRefunds.View get(UUID user, UUID order) { return refunds.owned(user, order); }
    private PaymentProviderPort.Result cancel(PaymentProviderPort provider, JdbcCashRefunds.Lease attempt) {
        return attempt.remaining() ? provider.cancelRemainingVerified(attempt.intent(), attempt.refund(), attempt.amount())
                : provider.cancelVerified(attempt.intent(), attempt.refund());
    }
}
