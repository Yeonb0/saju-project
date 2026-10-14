package com.sajuppugi.gift.application;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.common.idempotency.IdempotencyKey;
import com.sajuppugi.gift.infrastructure.JdbcGiftDeliveryWork;
import com.sajuppugi.gift.port.GiftAlimtalkProvider;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "app.gift.purchases-enabled", havingValue = "true")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class GiftDeliveryService implements GiftDeliveryUseCase {
    private static final Logger log = LoggerFactory.getLogger(GiftDeliveryService.class);
    private final JdbcGiftDeliveryWork jobs;
    private final ObjectProvider<GiftAlimtalkProvider> providers;
    private final boolean enabled;
    public GiftDeliveryService(JdbcGiftDeliveryWork jobs, ObjectProvider<GiftAlimtalkProvider> providers,
                               @Value("${app.gift.delivery-enabled:false}") boolean enabled) {
        this.jobs = jobs; this.providers = providers; this.enabled = enabled;
    }
    @Override public DeliveryView get(UUID buyer, UUID order) {
        var view = jobs.view(buyer, order);
        return new DeliveryView(view.status(), enabled && providers.getIfAvailable() != null && view.canResend(), view.remainingResends(), view.nextResendAt());
    }
    @Override public DeliveryView resend(UUID buyer, UUID order, IdempotencyKey key) { return manual(buyer, order, key, "RESEND"); }
    @Override public DeliveryView reissueLink(UUID buyer, UUID order, IdempotencyKey key) { return manual(buyer, order, key, "REISSUE"); }
    private DeliveryView manual(UUID buyer, UUID order, IdempotencyKey key, String action) {
        requireAvailable();
        if (key == null) throw new ApiException(ErrorCode.INVALID_REQUEST);
        jobs.manual(buyer, order, key.value(), action);
        return get(buyer, order);
    }
    public void requireAvailable() {
        if (!enabled || providers.getIfAvailable() == null) throw new ApiException(ErrorCode.GIFT_DELIVERY_UNAVAILABLE);
    }
    public void process(UUID jobId) {
        var provider = providers.getIfAvailable();
        if (!enabled || provider == null) return;
        var lease = jobs.claim(jobId).orElse(null);
        if (lease == null || !jobs.current(lease)) return;
        GiftAlimtalkProvider.Result result;
        if (lease.lookup()) {
            try { result = provider.lookup(jobId); }
            catch (RuntimeException unknown) { result = new GiftAlimtalkProvider.Result(jobId, GiftAlimtalkProvider.Outcome.UNKNOWN); }
        } else {
            GiftAlimtalkProvider.Request request;
            try { request = jobs.request(lease); }
            catch (RuntimeException invalid) {
                jobs.apply(lease, new GiftAlimtalkProvider.Result(jobId, GiftAlimtalkProvider.Outcome.PERMANENT_REJECTION)); return;
            }
            if (!jobs.current(lease)) return;
            try { result = provider.send(request); }
            catch (RuntimeException unknown) { result = new GiftAlimtalkProvider.Result(jobId, GiftAlimtalkProvider.Outcome.UNKNOWN); }
        }
        jobs.apply(lease, result);
    }
    public void recoverDue() {
        if (!enabled || providers.getIfAvailable() == null) return;
        for (var job : jobs.due()) {
            try { process(job); }
            catch (RuntimeException persistence) { log.warn("Gift delivery recovery failed: jobId={}, type={}", job, persistence.getClass().getSimpleName()); }
        }
    }
}
