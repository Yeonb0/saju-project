package com.sajuppugi.gift.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.gift.fulfillment-review-enabled", havingValue = "true")
public class GiftFulfillmentReviewWorker {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GiftFulfillmentReviewWorker.class);
    private final JdbcGiftFulfillment fulfillment;
    public GiftFulfillmentReviewWorker(JdbcGiftFulfillment fulfillment) { this.fulfillment = fulfillment; }
    @Scheduled(fixedDelayString = "${app.gift.fulfillment-review-delay:60000}", initialDelayString = "${app.gift.fulfillment-review-delay:60000}")
    public void review() {
        for (var order : fulfillment.reviewCandidates()) {
            try { fulfillment.review(order); }
            catch (RuntimeException failure) { log.warn("Gift fulfillment review failed: orderId={}, type={}", order, failure.getClass().getSimpleName()); }
        }
    }
}
