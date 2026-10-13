package com.sajuppugi.gift.infrastructure;

import com.sajuppugi.gift.application.GiftDeliveryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.gift.delivery-worker-enabled", havingValue = "true")
public class GiftDeliveryWorker {
    private final GiftDeliveryService deliveries;
    public GiftDeliveryWorker(GiftDeliveryService deliveries) { this.deliveries = deliveries; deliveries.requireAvailable(); }
    @Scheduled(fixedDelayString = "${app.gift.delivery-poll-delay:30000}", initialDelayString = "${app.gift.delivery-poll-delay:30000}")
    public void recover() { deliveries.recoverDue(); }
}
