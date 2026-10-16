package com.sajuppugi.gift.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.gift.infrastructure.GiftPayloadCipher;
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
    private final GiftPayloadCipher cipher;
    private final ObjectMapper mapper;

    public DirectGiftBatchService(JdbcTemplate jdbc, Clock clock, GiftPayloadCipher cipher, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.cipher = cipher;
        this.mapper = mapper;
    }

    @Transactional(timeout = 5)
    public DirectPaymentBatch create(UUID user, List<Option> recipients, String idempotencyKey) {
        return createInternal(user, recipients == null ? null : recipients.stream()
                .map(option -> new Payload("수능 선물", "선물 보낸 사람", "legacy", option)).toList(), idempotencyKey);
    }

    @Transactional(timeout = 5)
    public DirectPaymentBatch create(UUID user, Command command, String idempotencyKey) {
        if (command == null || command.recipients() == null) throw new ApiException(ErrorCode.INVALID_REQUEST);
        return createInternal(user, command.recipients().stream()
                .map(recipient -> new Payload(recipient.recipientName(), command.senderName(), command.message(), recipient.option())).toList(), idempotencyKey);
    }

    private DirectPaymentBatch createInternal(UUID user, List<Payload> requested, String idempotencyKey) {
        if (user == null || requested == null || requested.isEmpty() || idempotencyKey == null
                || idempotencyKey.isBlank() || idempotencyKey.length() > 512) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        if (requested.size() > 16 || requested.stream().anyMatch(value -> !valid(value))) throw new ApiException(ErrorCode.INVALID_REQUEST);
        requested = List.copyOf(requested);
        String requestHash = fingerprint(requested);
        String keyHash = hash(idempotencyKey);
        DirectPaymentBatch existing = findByKey(user, keyHash);
        if (existing != null) return requireSameRequest(existing, requestHash);

        int total = requested.stream().mapToInt(value -> value.option().amount()).sum();
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
        if (inserted == 0) return requireSameRequest(requireExisting(user, keyHash), requestHash);

        jdbc.update("""
                INSERT INTO direct_gift_batches(payment_batch_id,buyer_user_id,status,recipient_count,created_at,request_hash)
                VALUES (?,?,'PAYMENT_PENDING',?,?,?)
                """, id, user, requested.size(), java.sql.Timestamp.from(now), requestHash);
        for (int index = 0; index < requested.size(); index++) {
            Payload payload = requested.get(index);
            UUID recipientId = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO direct_gift_batch_recipients
                        (id,payment_batch_id,recipient_index,option,amount_krw,state,created_at,private_payload)
                    VALUES (?,?,?,?,?,'PENDING_PAYMENT',?,?)
                    """, recipientId, id, index, payload.option().name(), payload.option().amount(), java.sql.Timestamp.from(now),
                    cipher.encrypt(json(payload), context(id, recipientId)));
        }
        return batch;
    }

    private DirectPaymentBatch requireSameRequest(DirectPaymentBatch batch, String requestHash) {
        String stored = jdbc.query("SELECT request_hash FROM direct_gift_batches WHERE payment_batch_id=?",
                (rs, row) -> rs.getString(1), batch.id()).stream().findFirst().orElse(null);
        if (stored == null || !stored.equals(requestHash)) throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        return batch;
    }

    private boolean valid(Payload payload) {
        return payload != null && payload.option() != null && payload.recipientName() != null && !payload.recipientName().isBlank()
                && payload.recipientName().length() <= 50 && payload.senderName() != null && !payload.senderName().isBlank()
                && payload.senderName().length() <= 50 && payload.message() != null && !payload.message().isBlank()
                && payload.message().length() <= 100;
    }
    private String fingerprint(List<Payload> payloads) { return cipher.fingerprint(json(payloads)); }
    private String json(Object value) { try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException failure) { throw new IllegalStateException("Direct gift payload serialization failed", failure); } }
    private String context(UUID batchId, UUID recipientId) { return "direct-gift|" + batchId + "|" + recipientId; }

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
    public record Recipient(String recipientName, Option option) {}
    public record Command(String senderName, String message, List<Recipient> recipients) {}
    public record Payload(String recipientName, String senderName, String message, Option option) {}
}
