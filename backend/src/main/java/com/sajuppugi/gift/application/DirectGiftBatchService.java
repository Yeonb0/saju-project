package com.sajuppugi.gift.application;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.payment.domain.DirectPaymentBatch;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Server-priced mixed gift checkout; no link or recipient content is made here. */
@Service
public class DirectGiftBatchService {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public DirectGiftBatchService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(timeout = 5)
    public DirectPaymentBatch create(UUID user, List<Option> recipients, String idempotencyKey) {
        if (user == null || recipients == null || recipients.isEmpty() || idempotencyKey == null
                || idempotencyKey.isBlank() || idempotencyKey.length() > 512) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        List<Option> requested = List.copyOf(recipients);
        String keyHash = hash(idempotencyKey);
        DirectPaymentBatch existing = findByKey(user, keyHash);
        if (existing != null) return requireSameRequest(existing, requested);

        int total = requested.stream().mapToInt(Option::amount).sum();
        if (total >= 50_000) throw new ApiException(ErrorCode.PAYMENT_BATCH_LIMIT_EXCEEDED);
        Instant now = clock.instant();
        UUID id = UUID.randomUUID();
        DirectPaymentBatch batch = new DirectPaymentBatch(id, user, null, "GIFT_SUNEUNG_BATCH", "수능 선물",
                total, 1, total, DirectPaymentBatch.Status.PAYMENT_PENDING, now);
        int inserted = jdbc.update("""
                INSERT INTO direct_payment_batches
                    (id,buyer_user_id,quote_id,product_code,product_name,unit_amount_krw,quantity,
                     total_amount_krw,key_hash,status,created_at)
                VALUES (?,?,NULL,?,?,?,?,?,?,?,?)
                ON CONFLICT (buyer_user_id,key_hash) DO NOTHING
                """, id, user, batch.productCode(), batch.productName(), total, 1, total, keyHash,
                batch.status().name(), java.sql.Timestamp.from(now));
        if (inserted == 0) return requireSameRequest(requireExisting(user, keyHash), requested);

        jdbc.update("""
                INSERT INTO direct_gift_batches(payment_batch_id,buyer_user_id,status,recipient_count,created_at)
                VALUES (?,?,'PAYMENT_PENDING',?,?)
                """, id, user, requested.size(), java.sql.Timestamp.from(now));
        for (int index = 0; index < requested.size(); index++) {
            Option option = requested.get(index);
            jdbc.update("""
                    INSERT INTO direct_gift_batch_recipients
                        (id,payment_batch_id,recipient_index,option,amount_krw,state,created_at)
                    VALUES (?,?,?,?,?,'PENDING_PAYMENT',?)
                    """, UUID.randomUUID(), id, index, option.name(), option.amount(), java.sql.Timestamp.from(now));
        }
        return batch;
    }

    private DirectPaymentBatch requireSameRequest(DirectPaymentBatch batch, List<Option> requested) {
        List<Option> stored = jdbc.query("""
                SELECT r.option
                FROM direct_gift_batches b
                JOIN direct_gift_batch_recipients r ON r.payment_batch_id = b.payment_batch_id
                WHERE b.payment_batch_id = ?
                ORDER BY r.recipient_index
                """, (rs, row) -> Option.valueOf(rs.getString(1)), batch.id());
        if (!stored.equals(requested)) throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        return batch;
    }

    private DirectPaymentBatch requireExisting(UUID user, String keyHash) {
        DirectPaymentBatch existing = findByKey(user, keyHash);
        if (existing == null) throw new IllegalStateException("Conflicting direct gift batch was not visible");
        return existing;
    }

    private DirectPaymentBatch findByKey(UUID user, String keyHash) {
        return jdbc.query("SELECT * FROM direct_payment_batches WHERE buyer_user_id=? AND key_hash=?",
                (rs, row) -> new DirectPaymentBatch(rs.getObject("id", UUID.class),
                        rs.getObject("buyer_user_id", UUID.class), rs.getObject("quote_id", UUID.class),
                        rs.getString("product_code"), rs.getString("product_name"), rs.getInt("unit_amount_krw"),
                        rs.getInt("quantity"), rs.getInt("total_amount_krw"),
                        DirectPaymentBatch.Status.valueOf(rs.getString("status")),
                        rs.getTimestamp("created_at").toInstant()), user, keyHash).stream().findFirst().orElse(null);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    public enum Option {
        TALISMAN_ONLY(1000), READING_WITH_TALISMAN(3000);

        private final int amount;
        Option(int amount) { this.amount = amount; }
        public int amount() { return amount; }
    }
}
