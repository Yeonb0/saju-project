package com.sajuppugi.payment.application;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one-way handoff from a settled PG order to a fulfilment aggregate.
 * Callers must create their aggregate and this claim in the same short database
 * transaction; a PAID order is not itself permission to expose any content.
 */
@Service
public class DirectPaymentConsumptionService {
    private final JdbcTemplate jdbc; private final Clock clock;
    public DirectPaymentConsumptionService(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }

    @Transactional(timeout = 5)
    public void claimReading(UUID buyer, UUID batchId, UUID quoteId, UUID readingPurchaseId) {
        claim(buyer, batchId, quoteId, "READING", readingPurchaseId);
    }
    @Transactional(timeout = 5)
    public void claimTalismanAddon(UUID buyer, UUID batchId, UUID quoteId, UUID addonPurchaseId) {
        claim(buyer, batchId, quoteId, "TALISMAN_ADDON", addonPurchaseId);
    }
    @Transactional(timeout = 5)
    public void claimGiftBatch(UUID buyer, UUID batchId) { claim(buyer, batchId, null, "GIFT_BATCH", batchId); }
    private void claim(UUID buyer, UUID batchId, UUID quoteId, String consumerType, UUID consumerId) {
        if (buyer == null || batchId == null || consumerType == null || consumerId == null
                || ("GIFT_BATCH".equals(consumerType) != (quoteId == null)))
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        var batches = jdbc.query("SELECT quote_id,status FROM direct_payment_batches WHERE id=? AND buyer_user_id=? FOR UPDATE",
                (rs,n) -> new Batch(rs.getObject(1, UUID.class), rs.getString(2)), batchId, buyer);
        if (batches.isEmpty()) throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        Batch batch = batches.getFirst();
        if (!Objects.equals(batch.quoteId(), quoteId)) throw new ApiException(ErrorCode.FORBIDDEN);
        if (!"PAID".equals(batch.status())) throw new ApiException(ErrorCode.PAYMENT_ORDER_NOT_CONFIRMABLE);
        var claims = jdbc.query("SELECT consumer_type,consumer_id FROM direct_payment_consumptions WHERE batch_id=? FOR UPDATE",
                (rs,n) -> new Claim(rs.getString(1), rs.getObject(2, UUID.class)), batchId);
        if (!claims.isEmpty()) {
            Claim existing = claims.getFirst();
            if (consumerType.equals(existing.type()) && consumerId.equals(existing.id())) return;
            throw new ApiException(ErrorCode.PAYMENT_CONFIRMATION_CONFLICT);
        }
        // The batch row lock serializes all consumers for this order, including
        // concurrent HTTP retries and a future webhook reconciliation worker.
        jdbc.update("INSERT INTO direct_payment_consumptions(batch_id,consumer_type,consumer_id,consumed_at) VALUES (?,?,?,?)",
                batchId, consumerType, consumerId, Timestamp.from(clock.instant().truncatedTo(ChronoUnit.MICROS)));
    }
    private record Batch(UUID quoteId, String status) {} private record Claim(String type, UUID id) {}
}
