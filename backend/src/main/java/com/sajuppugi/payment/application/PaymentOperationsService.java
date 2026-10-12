package com.sajuppugi.payment.application;

import com.sajuppugi.payment.infrastructure.JdbcPaymentReconciliation;
import com.sajuppugi.payment.port.PaymentProviderPort;
import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class PaymentOperationsService {
    private final JdbcPaymentReconciliation jobs;
    private final ObjectProvider<PaymentProviderPort> providers;
    private final TopUpPaymentService payments;
    private final Clock clock;
    public PaymentOperationsService(JdbcPaymentReconciliation jobs, ObjectProvider<PaymentProviderPort> providers, TopUpPaymentService payments, Clock clock) {
        this.jobs = jobs; this.providers = providers; this.payments = payments; this.clock = clock;
    }
    public void reconcile(UUID order) {
        var provider = providers.getIfAvailable();
        if (provider == null) return;
        var lease = jobs.claim(order, clock.instant());
        if (lease.isEmpty()) return;
        PaymentProviderPort.Result result;
        try { result = provider.lookup(order); }
        catch (RuntimeException failure) { result = null; }
        // The existing grant lease/evidence boundary remains authoritative for pending approvals/cancellations.
        if (result != null && result.matches(lease.get().intent())) payments.recover(order);
        jobs.apply(lease.get(), result, clock.instant());
    }
}
