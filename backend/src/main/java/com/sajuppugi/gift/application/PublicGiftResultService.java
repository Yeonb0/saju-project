package com.sajuppugi.gift.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajuppugi.gift.infrastructure.JdbcGiftResultSnapshots;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** Public bearer-token projection. It never accepts a gift ID or exposes internal worker identifiers. */
@Service
@ConditionalOnProperty(name = "app.gift.purchases-enabled", havingValue = "true")
public class PublicGiftResultService {
    private final GiftAccessPort access;
    private final GiftFulfillmentPort fulfillment;
    private final JdbcGiftResultSnapshots snapshots;
    private final ObjectMapper mapper;

    public PublicGiftResultService(GiftAccessPort access, GiftFulfillmentPort fulfillment,
            JdbcGiftResultSnapshots snapshots, ObjectMapper mapper) {
        this.access = access; this.fulfillment = fulfillment; this.snapshots = snapshots; this.mapper = mapper;
    }

    public Result get(String rawToken) {
        var gift = access.verify(rawToken);
        var snapshot = snapshots.find(gift.giftId()).orElse(null);
        if (snapshot == null) return Result.processing();
        var receipt = fulfillment.provide(snapshot.receiptId(), snapshot.contentId(), () -> { });
        return new Result(State.READY, option(snapshot.productCode()), snapshot.contentVersion(),
                snapshot.generationVersion(), json(snapshot.resultJson()), receipt.accessExpiresAt());
    }

    private JsonNode json(String resultJson) {
        try {
            JsonNode result = mapper.readTree(resultJson);
            if (result == null || !result.isObject()) throw new IllegalStateException("Invalid gift result snapshot");
            return result;
        } catch (java.io.IOException invalid) { throw new IllegalStateException("Invalid gift result snapshot", invalid); }
    }

    private Option option(String productCode) {
        return switch (productCode) {
            case "GIFT_SUNEUNG_TALISMAN_ONLY" -> Option.TALISMAN_ONLY;
            case "GIFT_SUNEUNG_READING_WITH_TALISMAN" -> Option.READING_WITH_TALISMAN;
            default -> throw new IllegalStateException("Unsupported gift result product");
        };
    }

    public enum State { PROCESSING, READY }
    public enum Option { TALISMAN_ONLY, READING_WITH_TALISMAN }
    public record Result(State state, Option option, String contentVersion, String generationVersion,
                         JsonNode result, Instant accessExpiresAt) {
        static Result processing() { return new Result(State.PROCESSING, null, null, null, null, null); }
    }
}
