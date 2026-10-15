package com.sajuppugi.payment.application;

import com.sajuppugi.catalog.application.CatalogUseCase;
import com.sajuppugi.catalog.domain.Price;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.payment.domain.DirectPaymentBatch;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists a PG intent before the browser opens the payment window; it never trusts a client price. */
@Service
public class DirectPaymentBatchService {
    private final JdbcTemplate jdbc; private final CatalogUseCase catalog; private final Clock clock;
    public DirectPaymentBatchService(JdbcTemplate jdbc, CatalogUseCase catalog, Clock clock) {
        this.jdbc = jdbc; this.catalog = catalog; this.clock = clock;
    }
    @Transactional(timeout = 5)
    public DirectPaymentBatch create(UUID user, UUID quoteId, int quantity, String idempotencyKey) {
        if (user == null || quoteId == null || quantity < 1 || idempotencyKey == null || idempotencyKey.isBlank()
                || idempotencyKey.length() > 512) throw new ApiException(ErrorCode.INVALID_REQUEST);
        String key = hash(idempotencyKey);
        // The quote is server issued and one checkout may consume it.  Lock it before
        // looking up the batch so concurrent different idempotency keys converge too.
        var ownedQuote = jdbc.query("SELECT id FROM purchase_quotes WHERE id=? AND requester_user_id=? FOR UPDATE",
                (rs, n) -> rs.getObject(1, UUID.class), quoteId, user);
        if (ownedQuote.isEmpty()) throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
        var old = jdbc.query("SELECT * FROM direct_payment_batches WHERE buyer_user_id=? AND key_hash=?", (rs, n) -> map(rs), user, key);
        if (!old.isEmpty()) {
            if (!old.getFirst().quoteId().equals(quoteId) || old.getFirst().quantity() != quantity)
                throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
            return old.getFirst();
        }
        var quote = catalog.getPayableQuote(user, quoteId);
        var product = quote.productSnapshot();
        if (product.price().currency() != Price.Currency.KRW) throw new ApiException(ErrorCode.PRODUCT_NOT_AVAILABLE);
        int total;
        try { total = Math.multiplyExact(product.price().amount(), quantity); }
        catch (ArithmeticException bad) { throw new ApiException(ErrorCode.INVALID_REQUEST); }
        if (total >= 50_000) throw new ApiException(ErrorCode.PAYMENT_BATCH_LIMIT_EXCEEDED);
        Instant now = clock.instant();
        var batch = new DirectPaymentBatch(UUID.randomUUID(), user, quoteId, product.code(), product.productName(),
                product.price().amount(), quantity, total, DirectPaymentBatch.Status.PAYMENT_PENDING, now);
        jdbc.update("""
                INSERT INTO direct_payment_batches
                (id,buyer_user_id,quote_id,product_code,product_name,unit_amount_krw,quantity,total_amount_krw,key_hash,status,created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)""", batch.id(), user, quoteId, batch.productCode(), batch.productName(),
                batch.unitAmountKrw(), quantity, total, key, batch.status().name(), java.sql.Timestamp.from(now));
        return batch;
    }
    @Transactional(readOnly = true, timeout = 5)
    public DirectPaymentBatch get(UUID user, UUID batchId) {
        return jdbc.query("SELECT * FROM direct_payment_batches WHERE id=? AND buyer_user_id=?", (rs,n)->map(rs), batchId,user)
                .stream().findFirst().orElseThrow(()->new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
    }
    private DirectPaymentBatch map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new DirectPaymentBatch(rs.getObject("id", UUID.class), rs.getObject("buyer_user_id", UUID.class),
                rs.getObject("quote_id", UUID.class), rs.getString("product_code"), rs.getString("product_name"),
                rs.getInt("unit_amount_krw"), rs.getInt("quantity"), rs.getInt("total_amount_krw"),
                DirectPaymentBatch.Status.valueOf(rs.getString("status")), rs.getTimestamp("created_at").toInstant());
    }
    private String hash(String raw) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); } }
}
