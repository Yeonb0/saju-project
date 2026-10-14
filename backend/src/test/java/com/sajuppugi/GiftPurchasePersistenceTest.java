package com.sajuppugi;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sajuppugi.catalog.domain.Product;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.idempotency.IdempotencyKey;
import com.sajuppugi.gift.application.GiftOrderService;
import com.sajuppugi.gift.application.GiftPurchaseTransactions;
import com.sajuppugi.gift.application.GiftPurchaseService;
import com.sajuppugi.gift.application.GiftLinkService;
import com.sajuppugi.gift.application.GiftQuoteService;
import com.sajuppugi.gift.domain.GiftLifetimePolicy;
import com.sajuppugi.gift.infrastructure.GiftPayloadCipher;
import com.sajuppugi.gift.infrastructure.JdbcGiftPurchaseWork;
import com.sajuppugi.wallet.application.WalletPurchasePort;
import com.sajuppugi.wallet.application.WalletPurchaseReceiptPort;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;

@SpringBootTest(properties = {"app.wallet.purchases-enabled=true", "app.gift.orders-enabled=true",
        "app.gift.purchases-enabled=true", "app.gift.payload-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
@ActiveProfiles("test")
class GiftPurchasePersistenceTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired GiftOrderService drafts;
    @Autowired GiftQuoteService quotes;
    @Autowired GiftPurchaseTransactions purchases;
    @Autowired GiftPurchaseService service;
    @Autowired GiftLinkService linksService;
    @Autowired WalletPurchasePort wallet;
    @Autowired WalletPurchaseReceiptPort receipts;
    @Autowired GiftPayloadCipher cipher;
    @MockitoSpyBean JdbcGiftPurchaseWork work;
    private UUID owner;
    private boolean wasActive;
    @BeforeEach void prepare() {
        owner = UUID.randomUUID();
        wasActive = jdbc.queryForObject("SELECT active FROM products WHERE code = 'GIFT_SUNEUNG_TALISMAN_ONLY'", Boolean.class);
        jdbc.update("UPDATE products SET active = TRUE WHERE code = 'GIFT_SUNEUNG_TALISMAN_ONLY'");
    }
    @AfterEach void cleanup() {
        reset(AopTestUtils.<JdbcGiftPurchaseWork>getUltimateTargetObject(work));
        for (String table : List.of("gift_link_commands", "gift_delivery_outbox", "gift_purchase_tokens", "gift_purchase_work")) {
            jdbc.update("DELETE FROM " + table + " WHERE order_id IN (SELECT id FROM gift_order_drafts WHERE user_id = ?)", owner);
        }
        jdbc.update("DELETE FROM gift_order_commands WHERE user_id = ?", owner);
        jdbc.update("DELETE FROM gift_order_drafts WHERE user_id = ?", owner);
        for (String table : List.of("wallet_purchase_commands", "wallet_purchase_compensations", "wallet_purchase_debits", "wallet_transaction_lines")) {
            jdbc.update("DELETE FROM " + table + " WHERE wallet_user_id = ?", owner);
        }
        jdbc.update("DELETE FROM wallet_transactions WHERE wallet_user_id = ? AND reversal_of_id IS NOT NULL", owner);
        jdbc.update("DELETE FROM wallet_transactions WHERE wallet_user_id = ?", owner);
        jdbc.update("DELETE FROM wallet_lots WHERE wallet_user_id = ?", owner);
        jdbc.update("DELETE FROM wallets WHERE user_id = ?", owner);
        jdbc.update("DELETE FROM purchase_quotes WHERE requester_user_id = ?", owner);
        jdbc.update("UPDATE products SET active = ? WHERE code = 'GIFT_SUNEUNG_TALISMAN_ONLY'", wasActive);
    }
    private GiftOrderService.Order order() {
        UUID quote = quotes.issue(owner, Product.FortuneType.SUNEUNG, GiftQuoteService.Option.TALISMAN_ONLY).funding().quote().id();
        return drafts.create(owner, quote, new GiftOrderService.Payload("recipient", "sender", "message"), UUID.randomUUID().toString());
    }
    private void grant() {
        Instant now = Instant.now();
        UUID lot = UUID.randomUUID(), transaction = UUID.randomUUID();
        jdbc.update("INSERT INTO wallets(user_id, paid_balance, updated_at) VALUES (?, 20, ?)", owner, Timestamp.from(now));
        jdbc.update("INSERT INTO wallet_lots(id, wallet_user_id, balance_type, granted_amount, remaining_amount, created_at, expires_at) VALUES (?, ?, 'PAID', 20, 20, ?, ?)",
                lot, owner, Timestamp.from(now.minusSeconds(1)), Timestamp.from(now.plusSeconds(3600)));
        jdbc.update("INSERT INTO wallet_transactions(id, wallet_user_id, type, total_amount, reference_id, created_at) VALUES (?, ?, 'TOP_UP', 20, ?, ?)",
                transaction, owner, UUID.randomUUID(), Timestamp.from(now.minusSeconds(1)));
        jdbc.update("INSERT INTO wallet_transaction_lines(transaction_id, lot_id, wallet_user_id, amount) VALUES (?, ?, ?, 20)", transaction, lot, owner);
    }
    private int debits() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM wallet_transactions WHERE wallet_user_id = ? AND type = 'PURCHASE'", Integer.class, owner);
    }
    private int links(UUID order) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM gift_purchase_tokens WHERE order_id = ?", Integer.class, order);
    }
    private void due(UUID order) {
        jdbc.update("UPDATE gift_purchase_work SET next_attempt_at = ? WHERE order_id = ?", Timestamp.from(Instant.now().minusSeconds(1)), order);
    }
    @Test void activationCommitsOneDebitAndEncryptedDirectLink() {
        grant(); var order = order();
        purchases.begin(owner, order.orderId());
        var active = purchases.activate(owner, order.orderId());
        assertThat(active.status()).isEqualTo("ACTIVE");
        assertThat(active.chargedAmount()).isEqualTo(10);
        assertThat(purchases.activate(owner, order.orderId())).isEqualTo(active);
        assertThat(debits()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT paid_balance FROM wallets WHERE user_id = ?", Integer.class, owner)).isEqualTo(10);
        assertThat(links(order.orderId())).isEqualTo(1);
        String encrypted = jdbc.queryForObject("SELECT token_ciphertext FROM gift_purchase_tokens WHERE order_id = ?", String.class, order.orderId());
        assertThat(encrypted).startsWith("v1:").doesNotContain("recipient", "token");
        assertThat(cipher.decrypt(encrypted, "gift-link|" + order.orderId() + "|1")).hasSize(43);
        assertThat(jdbc.queryForObject("SELECT token_hash FROM gift_purchase_tokens WHERE order_id = ?", String.class, order.orderId())).matches("[a-f0-9]{64}");
        assertThat(jdbc.queryForObject("SELECT access_expires_at FROM gift_purchase_work WHERE order_id = ?", Timestamp.class, order.orderId()).toInstant())
                .isEqualTo(GiftLifetimePolicy.unusedExpiresAt(active.approvedAt()));
    }
    @Test void linkPersistenceFailureRollsBackGrantButRecoversCommittedDebitAfterQuoteExpiry() {
        grant(); var order = order(); purchases.begin(owner, order.orderId());
        doThrow(new IllegalStateException("synthetic-link-failure"))
                .when(AopTestUtils.<JdbcGiftPurchaseWork>getUltimateTargetObject(work)).token(any(), anyString(), anyString(), any(), any());
        assertThatThrownBy(() -> purchases.activate(owner, order.orderId())).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(debits()).isEqualTo(1);
        var evidence = receipts.findDebitEvidence(owner, order.quoteId()).orElseThrow();
        assertThat(purchases.get(owner, order.orderId()).status()).isEqualTo("PENDING");
        assertThat(links(order.orderId())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gift_purchase_tokens WHERE order_id = ?", Integer.class, order.orderId())).isZero();
        reset(AopTestUtils.<JdbcGiftPurchaseWork>getUltimateTargetObject(work));
        Instant expired = Instant.now().minusSeconds(1);
        jdbc.update("UPDATE purchase_quotes SET created_at = ?, expires_at = ? WHERE id = ?", Timestamp.from(expired.minusSeconds(1800)), Timestamp.from(expired), order.quoteId());
        jdbc.update("UPDATE products SET active = FALSE WHERE code = 'GIFT_SUNEUNG_TALISMAN_ONLY'");
        var active = purchases.activate(owner, order.orderId());
        assertThat(active.status()).isEqualTo("ACTIVE");
        assertThat(active.approvedAt()).isEqualTo(evidence.committedAt());
        assertThat(active.walletTransactionId()).isEqualTo(evidence.receipt().transactionId());
        assertThat(debits()).isEqualTo(1);
        assertThat(links(order.orderId())).isEqualTo(1);
    }
    @Test void simultaneousActivationConvergesOnOneGiftAndDebit() throws Exception {
        grant(); var order = order(); purchases.begin(owner, order.orderId());
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { start.await(); return purchases.activate(owner, order.orderId()); });
            var b = executor.submit(() -> { start.await(); return purchases.activate(owner, order.orderId()); });
            start.countDown();
            assertThat(a.get(20, TimeUnit.SECONDS).giftId()).isEqualTo(b.get(20, TimeUnit.SECONDS).giftId());
        }
        assertThat(debits()).isEqualTo(1); assertThat(links(order.orderId())).isEqualTo(1);
    }
    @Test void buyerCanReplayAndExplicitlyRotateLinkWithoutExtendingExpiry() {
        grant(); var order = order();
        var active = service.purchase(owner, order.orderId());
        var initial = linksService.get(owner, order.orderId());
        assertThat(initial.shareUrl()).startsWith("http://localhost:3000/g#token=");
        assertThat(initial.expiresAt()).isEqualTo(GiftLifetimePolicy.unusedExpiresAt(active.approvedAt()));
        assertThatThrownBy(() -> linksService.get(UUID.randomUUID(), order.orderId())).isInstanceOf(ApiException.class);

        var first = linksService.reissue(owner, order.orderId(), new IdempotencyKey("rotate-once"));
        var replay = linksService.reissue(owner, order.orderId(), new IdempotencyKey("rotate-once"));
        assertThat(first.tokenVersion()).isEqualTo(2);
        assertThat(replay.tokenVersion()).isEqualTo(2);
        assertThat(replay.shareUrl()).isEqualTo(first.shareUrl());
        assertThat(first.expiresAt()).isEqualTo(initial.expiresAt());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gift_link_commands WHERE order_id = ?", Integer.class, order.orderId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gift_purchase_tokens WHERE order_id = ?", Integer.class, order.orderId())).isEqualTo(1);
    }
    @Test void compensatedDebitDoesNotActivateOrEnqueue() {
        grant(); var order = order(); purchases.begin(owner, order.orderId());
        var debit = wallet.debit(owner, order.quoteId(), new IdempotencyKey("preexisting"));
        wallet.compensate(debit.transactionId(), "GIFT_GENERATION_FAILED", new IdempotencyKey("refund"));
        assertThat(purchases.activate(owner, order.orderId()).status()).isEqualTo("REFUNDED");
        assertThat(links(order.orderId())).isZero(); assertThat(debits()).isEqualTo(1);
    }
    @Test void saleClosureWithoutDebitFailsWithoutAutomaticRetry() {
        grant(); var order = order();
        jdbc.update("UPDATE products SET active = FALSE WHERE code = 'GIFT_SUNEUNG_TALISMAN_ONLY'");
        assertThat(service.purchase(owner, order.orderId()).status()).isEqualTo("FAILED");
        due(order.orderId()); service.recoverDue();
        assertThat(debits()).isZero(); assertThat(links(order.orderId())).isZero();
    }
    @Test void inconsistentReceiptAmountRequiresReviewWithoutGrant() {
        grant(); var order = order(); purchases.begin(owner, order.orderId());
        wallet.debit(owner, order.quoteId(), new IdempotencyKey("preexisting"));
        jdbc.update("UPDATE gift_order_drafts SET shell_amount = 15 WHERE id = ?", order.orderId());
        assertThat(purchases.activate(owner, order.orderId()).status()).isEqualTo("REVIEW_REQUIRED");
        assertThat(links(order.orderId())).isZero(); assertThat(debits()).isEqualTo(1);
    }
    @Test void insufficientBalanceFailsWithoutDebitOrDeliveryAndOwnershipIsEnforced() {
        var order = order(); purchases.begin(owner, order.orderId());
        assertThat(purchases.activate(owner, order.orderId()).status()).isEqualTo("FAILED");
        assertThat(debits()).isZero(); assertThat(links(order.orderId())).isZero();
        assertThatThrownBy(() -> purchases.begin(UUID.randomUUID(), order.orderId())).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> purchases.activate(UUID.randomUUID(), order.orderId())).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> purchases.get(UUID.randomUUID(), order.orderId())).isInstanceOf(ApiException.class);
    }
    @Test void failedAttemptsAreBoundedAndDraftsNeverChargeWithoutExplicitIntent() {
        grant(); var order = order();
        assertThatThrownBy(() -> purchases.activate(owner, order.orderId())).isInstanceOf(ApiException.class);
        assertThat(debits()).isZero();
        purchases.begin(owner, order.orderId());
        for (int attempt = 1; attempt <= 5; attempt++) {
            due(order.orderId());
            var result = purchases.failedAttempt(owner, order.orderId());
            assertThat(result.failures()).isEqualTo(attempt);
            assertThat(result.status()).isEqualTo(attempt == 5 ? "REVIEW_REQUIRED" : "PENDING");
            assertThat(result.nextAttemptAt()).isAfter(Instant.now());
        }
        due(order.orderId());
        assertThat(purchases.activate(owner, order.orderId()).status()).isEqualTo("REVIEW_REQUIRED");
        assertThat(purchases.begin(owner, order.orderId()).failures()).isEqualTo(5);
        assertThat(debits()).isZero();
    }
    @Test void durableRecoveryOnlyProcessesExplicitIntentAndRespectsBackoff() {
        grant(); var order = order(); var draftOnly = order();
        doThrow(new IllegalStateException("synthetic-link-failure"))
                .when(AopTestUtils.<JdbcGiftPurchaseWork>getUltimateTargetObject(work)).token(any(), anyString(), anyString(), any(), any());
        var pending = service.purchase(owner, order.orderId());
        assertThat(pending.status()).isEqualTo("PENDING");
        assertThat(pending.failures()).isEqualTo(1);
        service.resume(owner, order.orderId());
        assertThat(purchases.get(owner, order.orderId()).failures()).isEqualTo(1);
        reset(AopTestUtils.<JdbcGiftPurchaseWork>getUltimateTargetObject(work));
        service.recoverDue();
        assertThat(purchases.get(owner, order.orderId()).status()).isEqualTo("PENDING");
        due(order.orderId());
        service.recoverDue();
        assertThat(purchases.get(owner, order.orderId()).status()).isEqualTo("ACTIVE");
        assertThat(debits()).isEqualTo(1);
        assertThat(receipts.findDebit(owner, draftOnly.quoteId())).isEmpty();
        assertThat(links(order.orderId())).isEqualTo(1);
    }
}
