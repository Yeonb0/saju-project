package com.sajuppugi.payment.application;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.common.idempotency.IdempotencyKey;
import com.sajuppugi.payment.domain.TopUpOrder;
import com.sajuppugi.payment.infrastructure.JdbcTopUpExecutions;
import com.sajuppugi.payment.port.PaymentProviderPort;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class TopUpPaymentService implements TopUpUseCase {
    private final TopUpOrderService orders;
    private final TopUpConfirmationService bindings;
    private final JdbcTopUpExecutions executions;
    private final ObjectProvider<PaymentProviderPort> providers;
    private final Clock clock;
    public TopUpPaymentService(TopUpOrderService orders, TopUpConfirmationService bindings,
            JdbcTopUpExecutions executions, ObjectProvider<PaymentProviderPort> providers, Clock clock) {
        this.orders = orders; this.bindings = bindings; this.executions = executions;
        this.providers = providers; this.clock = clock;
    }

    @Override
    public TopUpOrder create(UUID user, String product, IdempotencyKey key) {
        return orders.create(user, product, key == null ? null : key.value());
    }

    @Override
    public OrderView get(UUID user, UUID order) { return executions.view(user, order); }

    @Override
    public OrderView confirm(UUID user, UUID order, Confirmation confirmation, IdempotencyKey key) {
        return confirm(user, order, confirmation, key == null ? null : key.value());
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public OrderView confirm(UUID user, UUID order, Confirmation confirmation, String key) {
        var provider = provider();
        bindings.prepare(user, order, confirmation.paymentKey(), confirmation.amountKrw(), key);
        var lease = executions.claim(order, user, true, now());
        if (lease.isPresent()) execute(lease.get(), confirmation, provider);
        return get(user, order);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void recover(UUID order) {
        var provider = provider();
        var lease = executions.claim(order, null, false, now());
        if (lease.isPresent()) execute(lease.get(), null, provider);
    }

    private void execute(JdbcTopUpExecutions.Lease lease, Confirmation confirmation, PaymentProviderPort provider) {
        PaymentProviderPort.Result result;
        try {
            result = lease.confirm() ? provider.confirm(lease.intent().orderId(), confirmation)
                    : provider.lookup(lease.intent().orderId());
        } catch (RuntimeException failure) {
            result = new PaymentProviderPort.Result(PaymentProviderPort.Outcome.UNKNOWN,
                    lease.intent().orderId(), null, 0, null, "PAYMENT_TRANSPORT_ERROR");
        }
        // DB failures must preserve the leased attempt for lookup after restart, never mark payment failed.
        executions.apply(lease, result, now());
    }

    private java.time.Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }
    private PaymentProviderPort provider() {
        var provider = providers.getIfAvailable();
        if (provider == null) throw new ApiException(ErrorCode.PAYMENT_FULFILLMENT_UNAVAILABLE);
        return provider;
    }
}
