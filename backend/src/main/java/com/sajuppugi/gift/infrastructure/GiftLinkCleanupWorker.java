package com.sajuppugi.gift.infrastructure;

import com.sajuppugi.gift.application.GiftLinkService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.gift.purchases-enabled", havingValue = "true")
public class GiftLinkCleanupWorker {
    private final GiftLinkService links;
    public GiftLinkCleanupWorker(GiftLinkService links) { this.links = links; }
    @Scheduled(fixedDelayString = "${app.gift.link-cleanup-poll-delay:60000}",
            initialDelayString = "${app.gift.link-cleanup-poll-delay:60000}")
    public void run() { links.purgeExpired(); }
}
