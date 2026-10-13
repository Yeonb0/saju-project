package com.sajuppugi.gift.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.common.idempotency.IdempotencyKey;
import com.sajuppugi.gift.domain.GiftLifetimePolicy;
import com.sajuppugi.gift.infrastructure.GiftPayloadCipher;
import com.sajuppugi.gift.infrastructure.JdbcGiftOrders;
import com.sajuppugi.gift.infrastructure.JdbcGiftPurchaseWork;
import com.sajuppugi.gift.infrastructure.JdbcGiftPurchaseWork.Work;
import com.sajuppugi.wallet.application.WalletPurchasePort;
import com.sajuppugi.wallet.application.WalletPurchaseReceiptPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Internal activation only; no provider IO or public token exposure. */
@Service
@ConditionalOnProperty(name = "app.gift.purchases-enabled", havingValue = "true")
@Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
public class GiftPurchaseTransactions {
    private final JdbcGiftOrders orders;
    private final JdbcGiftPurchaseWork work;
    private final WalletPurchasePort wallet;
    private final WalletPurchaseReceiptPort receipts;
    private final GiftPayloadCipher cipher;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    public GiftPurchaseTransactions(JdbcGiftOrders orders, JdbcGiftPurchaseWork work, WalletPurchasePort wallet,
            WalletPurchaseReceiptPort receipts, GiftPayloadCipher cipher, ObjectMapper mapper, Clock clock) {
        this.orders = orders; this.work = work; this.wallet = wallet; this.receipts = receipts;
        this.cipher = cipher; this.mapper = mapper; this.clock = clock;
        // Cipher refuses non-local/test profiles and missing synthetic/local encryption configuration.
        cipher.fingerprint("gift-purchase-startup");
    }
    public Work begin(UUID user, UUID order) {
        owned(user, order);
        work.insert(order, clock.instant());
        return work.get(order, false).orElseThrow();
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true, timeout = 5)
    public Work get(UUID user, UUID order) {
        owned(user, order);
        return work.get(order, false).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true, timeout = 5)
    public List<JdbcGiftPurchaseWork.Target> due() { return work.due(clock.instant()); }
    public Work activate(UUID user, UUID id) {
        var order = owned(user, id);
        var pending = work.get(id, true).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!pending.status().equals("PENDING")) return pending;
        if (clock.instant().isBefore(pending.nextAttemptAt())) return pending;
        var evidence = receipts.findDebitEvidence(user, order.quoteId()).orElse(null);
        if (evidence == null) {
            try { wallet.debit(user, order.quoteId(), new IdempotencyKey("gift-purchase:" + id)); }
            catch (ApiException rejected) {
                // Only definite pre-debit business rejections become terminal failures.
                if (rejected.errorCode() == ErrorCode.INSUFFICIENT_BALANCE
                        || rejected.errorCode() == ErrorCode.QUOTE_EXPIRED
                        || rejected.errorCode() == ErrorCode.PRODUCT_NOT_FOUND
                        || rejected.errorCode() == ErrorCode.PRODUCT_NOT_AVAILABLE
                        || rejected.errorCode() == ErrorCode.RESOURCE_NOT_FOUND) {
                    work.stop(id, "FAILED", rejected.errorCode().name());
                    return work.get(id, false).orElseThrow();
                }
                throw rejected;
            }
            evidence = receipts.findDebitEvidence(user, order.quoteId()).orElseThrow();
        }
        var debit = evidence.receipt();
        if (debit.debitedAmount() != order.amount() || evidence.committedAt().isAfter(clock.instant())) {
            work.stop(id, "REVIEW_REQUIRED", "GIFT_RECEIPT_MISMATCH");
        } else if (receipts.findCompensation(user, order.quoteId(), debit.transactionId()).isPresent()) {
            work.stop(id, "REFUNDED", "GIFT_ALREADY_COMPENSATED");
        } else if (!clock.instant().isBefore(GiftLifetimePolicy.unusedExpiresAt(evidence.committedAt()))) {
            work.stop(id, "REVIEW_REQUIRED", "GIFT_RECOVERY_WINDOW_EXPIRED");
        } else {
            UUID gift = UUID.randomUUID(), outbox = UUID.randomUUID();
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            String original = cipher.decrypt(work.payload(id), user + "|" + id);
            String delivery = json(Map.of("giftId", gift, "token", token, "orderPayload", original));
            work.activate(id, debit.transactionId(), debit.debitedAmount(), evidence.committedAt(), gift,
                    GiftLifetimePolicy.unusedExpiresAt(evidence.committedAt()));
            work.token(id, hash(token));
            // Grant, hashed token and encrypted send intent commit together, after the independent wallet receipt.
            work.enqueue(outbox, id, cipher.encrypt(delivery, "gift-delivery|" + outbox), clock.instant());
        }
        return work.get(id, false).orElseThrow();
    }
    public Work failedAttempt(UUID user, UUID order) {
        owned(user, order);
        var current = work.get(order, true).orElseThrow();
        if (current.status().equals("PENDING") && !clock.instant().isBefore(current.nextAttemptAt()))
            work.failedAttempt(current, clock.instant());
        return work.get(order, false).orElseThrow();
    }
    private GiftOrderService.Order owned(UUID user, UUID id) {
        return orders.owned(user, id).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
    }
    private String json(Object payload) {
        try { return mapper.writeValueAsString(payload); }
        catch (JsonProcessingException failure) { throw new IllegalStateException("Gift delivery serialization failed"); }
    }
    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
