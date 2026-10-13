package com.sajuppugi;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajuppugi.catalog.domain.Product;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.gift.application.*;
import com.sajuppugi.gift.infrastructure.*;
import com.sajuppugi.wallet.application.WalletPurchaseService;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"app.wallet.purchases-enabled=true", "app.gift.orders-enabled=true",
        "app.gift.purchases-enabled=true", "app.gift.payload-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = "jdbc:postgresql:.*")
class GiftLifecyclePostgresTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired GiftOrderService drafts;
    @Autowired GiftQuoteService quotes;
    @Autowired GiftCommandTransactions commands;
    @Autowired GiftPurchaseService purchases;
    @Autowired GiftPurchaseTransactions work;
    @Autowired GiftCancellationService cancellations;
    @Autowired GiftPayloadCipher cipher;
    @Autowired JdbcGiftAccess access;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoSpyBean GiftCancellationTransactions cancellationStore;
    @MockitoSpyBean WalletPurchaseService wallet;
    UUID owner;
    boolean wasActive;
    Instant now;

    @BeforeEach void setup() {
        owner = UUID.randomUUID(); now = Instant.now();
        jdbc.update("INSERT INTO users(id,kakao_subject,nickname,status,created_at,updated_at) VALUES (?,?,'gift-test','ACTIVE',?,?)",
                owner, "synthetic-gift-" + owner, Timestamp.from(now), Timestamp.from(now));
        wasActive = jdbc.queryForObject("SELECT active FROM products WHERE code = 'GIFT_SUNEUNG_TALISMAN_ONLY'", Boolean.class);
        jdbc.update("UPDATE products SET active = TRUE WHERE code = 'GIFT_SUNEUNG_TALISMAN_ONLY'");
        jdbc.update("INSERT INTO wallets(user_id,paid_balance,bonus_balance,updated_at) VALUES (?,17,3,?)", owner, Timestamp.from(now));
        UUID transaction = UUID.randomUUID();
        jdbc.update("INSERT INTO wallet_transactions(id,wallet_user_id,type,total_amount,reference_id,created_at) VALUES (?,?,'TOP_UP',20,?,?)",
                transaction, owner, UUID.randomUUID(), Timestamp.from(now));
        grant(transaction, "PAID", 17, now.plusSeconds(3600));
        grant(transaction, "BONUS", 3, now.plusSeconds(1800));
    }
    void grant(UUID transaction, String type, int count, Instant expiry) {
        UUID lot = UUID.randomUUID();
        jdbc.update("INSERT INTO wallet_lots(id,wallet_user_id,balance_type,granted_amount,remaining_amount,created_at,expires_at) VALUES (?,?,?,?,?,?,?)",
                lot, owner, type, count, count, Timestamp.from(now), Timestamp.from(expiry));
        jdbc.update("INSERT INTO wallet_transaction_lines(transaction_id,lot_id,wallet_user_id,amount) VALUES (?,?,?,?)",
                transaction, lot, owner, count);
    }
    @AfterEach void cleanup() {
        reset(AopTestUtils.<GiftCancellationTransactions>getUltimateTargetObject(cancellationStore));
        reset(AopTestUtils.<WalletPurchaseService>getUltimateTargetObject(wallet));
        jdbc.update("DELETE FROM gift_action_commands WHERE user_id = ?", owner);
        for (String table : List.of("gift_cancellations", "gift_delivery_outbox", "gift_purchase_tokens", "gift_purchase_work"))
            jdbc.update("DELETE FROM " + table + " WHERE order_id IN (SELECT id FROM gift_order_drafts WHERE user_id = ?)", owner);
        jdbc.update("DELETE FROM gift_order_commands WHERE user_id = ?", owner);
        jdbc.update("DELETE FROM gift_order_drafts WHERE user_id = ?", owner);
        for (String table : List.of("wallet_purchase_commands", "wallet_purchase_compensations", "wallet_purchase_debits", "wallet_transaction_lines"))
            jdbc.update("DELETE FROM " + table + " WHERE wallet_user_id = ?", owner);
        jdbc.update("DELETE FROM wallet_transactions WHERE wallet_user_id = ? AND reversal_of_id IS NOT NULL", owner);
        jdbc.update("DELETE FROM wallet_transactions WHERE wallet_user_id = ?", owner);
        jdbc.update("DELETE FROM wallet_lots WHERE wallet_user_id = ?", owner);
        jdbc.update("DELETE FROM wallets WHERE user_id = ?", owner);
        jdbc.update("DELETE FROM purchase_quotes WHERE requester_user_id = ?", owner);
        jdbc.update("DELETE FROM user_sessions WHERE user_id = ?", owner);
        jdbc.update("DELETE FROM users WHERE id = ?", owner);
        jdbc.update("UPDATE products SET active = ? WHERE code = 'GIFT_SUNEUNG_TALISMAN_ONLY'", wasActive);
    }
    GiftOrderService.Order draft() {
        var quote = quotes.issue(owner, Product.FortuneType.SUNEUNG, GiftQuoteService.Option.TALISMAN_ONLY).funding().quote();
        return drafts.create(owner, quote.id(), new GiftOrderService.Payload("recipient", "01012345678", "sender", "Good luck!"), UUID.randomUUID().toString());
    }
    GiftOrderService.Order active() {
        var order = draft();
        commands.purchase(owner, order.orderId(), "purchase:" + order.orderId());
        assertThat(purchases.resume(owner, order.orderId()).status()).isEqualTo("ACTIVE");
        return order;
    }
    int returns() { return jdbc.queryForObject("SELECT COUNT(*) FROM wallet_purchase_compensations WHERE wallet_user_id = ?", Integer.class, owner); }
    int balance() { return jdbc.queryForObject("SELECT paid_balance + bonus_balance FROM wallets WHERE user_id = ?", Integer.class, owner); }
    String token(UUID order) throws Exception {
        UUID outbox = jdbc.queryForObject("SELECT id FROM gift_delivery_outbox WHERE order_id = ?", UUID.class, order);
        String payload = jdbc.queryForObject("SELECT private_payload FROM gift_delivery_outbox WHERE id = ?", String.class, outbox);
        return mapper.readTree(cipher.decrypt(payload, "gift-delivery|" + outbox)).path("token").asText();
    }
    void due(UUID order) { jdbc.update("UPDATE gift_cancellations SET next_attempt_at = ? WHERE order_id = ?", Timestamp.from(Instant.now().minusSeconds(1)), order); }

    @Test void purchaseHttpReplaysAndOrderGetReportsRealFinancialState() throws Exception {
        var order = draft();
        for (int i = 0; i < 2; i++) mvc.perform(post("/api/v1/gift-orders/{id}/purchase", order.orderId())
                .with(user(owner.toString())).with(csrf()).header("Idempotency-Key", "buy"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.chargedShells").value(10));
        mvc.perform(get("/api/v1/gift-orders/{id}", order.orderId()).with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.charged.amount").value(10));
        assertThat(balance()).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wallet_purchase_debits WHERE wallet_user_id = ?", Integer.class, owner)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM gift_delivery_outbox WHERE order_id = ?", String.class, order.orderId())).isEqualTo("PENDING");
    }
    @Test void commandKeyCannotChangeOrderOrAction() {
        var first = active(); var second = draft();
        assertThatThrownBy(() -> commands.purchase(owner, second.orderId(), "purchase:" + first.orderId()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED));
        assertThatThrownBy(() -> cancellations.cancel(owner, first.orderId(), "purchase:" + first.orderId()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gift_cancellations", Integer.class)).isZero();
    }
    @Test void cancelReturnsOriginalPaidAndBonusLotsOnlyOnceWithoutExtendingExpiry() throws Exception {
        var order = active(); String token = token(order.orderId());
        var expiries = jdbc.queryForList("SELECT expires_at FROM wallet_lots WHERE wallet_user_id = ? ORDER BY id", owner);
        assertThat(access.verify(token).giftId()).isNotNull();
        cancellations.cancel(owner, order.orderId(), "cancel");
        cancellations.cancel(owner, order.orderId(), "cancel");
        cancellations.cancel(owner, order.orderId(), "another-key");
        assertThat(returns()).isEqualTo(1); assertThat(balance()).isEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT paid_balance FROM wallets WHERE user_id = ?", Integer.class, owner)).isEqualTo(17);
        assertThat(jdbc.queryForObject("SELECT bonus_balance FROM wallets WHERE user_id = ?", Integer.class, owner)).isEqualTo(3);
        assertThat(jdbc.queryForList("SELECT expires_at FROM wallet_lots WHERE wallet_user_id = ? ORDER BY id", owner)).isEqualTo(expiries);
        assertThat(work.get(owner, order.orderId()).status()).isEqualTo("CANCELED");
        assertThatThrownBy(() -> access.verify(token)).isInstanceOf(ApiException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM gift_delivery_outbox WHERE order_id = ?", String.class, order.orderId())).isEqualTo("CANCELED");
        assertThat(purchases.resume(owner, order.orderId()).status()).isEqualTo("CANCELED");
    }
    @Test void pendingReturnNeverLooksCompletedAndRecoversCommittedWalletReceipt() throws Exception {
        var order = active(); String token = token(order.orderId());
        doThrow(new IllegalStateException("synthetic-completion-loss"))
                .when(AopTestUtils.<GiftCancellationTransactions>getUltimateTargetObject(cancellationStore)).complete(any());
        mvc.perform(post("/api/v1/gift-orders/{id}/cancel", order.orderId()).with(user(owner.toString())).with(csrf())
                .header("Idempotency-Key", "cancel")).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("CANCEL_PENDING"))
                .andExpect(jsonPath("$.data.returnedShells").value(org.hamcrest.Matchers.nullValue()));
        assertThat(returns()).isEqualTo(1); assertThat(balance()).isEqualTo(20);
        assertThatThrownBy(() -> access.verify(token)).isInstanceOf(ApiException.class);
        reset(AopTestUtils.<GiftCancellationTransactions>getUltimateTargetObject(cancellationStore));
        due(order.orderId()); cancellations.recoverDue();
        assertThat(work.get(owner, order.orderId()).status()).isEqualTo("CANCELED");
        assertThat(returns()).isEqualTo(1);
    }
    @Test void reservationFailureRollsBackRevocationAndIntentBeforeAnyReturn() throws Exception {
        var order = active(); String token = token(order.orderId());
        jdbc.execute("ALTER TABLE gift_cancellations ADD CONSTRAINT synthetic_gift_cancel_failure CHECK (state <> 'PENDING')");
        try { assertThatThrownBy(() -> cancellations.cancel(owner, order.orderId(), "cancel")).isInstanceOf(RuntimeException.class); }
        finally { jdbc.execute("ALTER TABLE gift_cancellations DROP CONSTRAINT synthetic_gift_cancel_failure"); }
        assertThat(work.get(owner, order.orderId()).status()).isEqualTo("ACTIVE");
        assertThat(access.verify(token).giftId()).isNotNull(); assertThat(returns()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gift_action_commands WHERE user_id = ? AND action = 'CANCEL'", Integer.class, owner)).isZero();
    }
    @Test void alreadyProvidedOrOlderThanSevenDaysCannotCancel() {
        var order = active();
        jdbc.update("UPDATE gift_purchase_work SET first_provided_at = ? WHERE order_id = ?", Timestamp.from(Instant.now()), order.orderId());
        assertThatThrownBy(() -> cancellations.cancel(owner, order.orderId(), "provided")).isInstanceOf(ApiException.class);
        jdbc.update("UPDATE gift_purchase_work SET first_provided_at = NULL, approved_at = ? WHERE order_id = ?",
                Timestamp.from(Instant.now().minusSeconds(7 * 86400 + 1)), order.orderId());
        assertThatThrownBy(() -> cancellations.cancel(owner, order.orderId(), "late")).isInstanceOf(ApiException.class);
        assertThat(returns()).isZero(); assertThat(work.get(owner, order.orderId()).status()).isEqualTo("ACTIVE");
    }
    @Test void unchargedDraftCannotCancelAndForeignOwnerCannotMutate() {
        var order = draft();
        assertThatThrownBy(() -> cancellations.cancel(owner, order.orderId(), "draft")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> commands.purchase(UUID.randomUUID(), order.orderId(), "foreign")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> cancellations.cancel(UUID.randomUUID(), order.orderId(), "foreign")).isInstanceOf(ApiException.class);
        assertThat(balance()).isEqualTo(20); assertThat(returns()).isZero();
    }
    @Test void tokenExpiryMutationAndArbitraryTalismanAreRejected() throws Exception {
        var order = active(); String token = token(order.orderId());
        assertThatThrownBy(() -> access.verify("!" + token.substring(1))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> access.verify(null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> access.verifyTalismanAccess(token, UUID.randomUUID())).isInstanceOf(ApiException.class);
        jdbc.update("UPDATE gift_purchase_work SET approved_at = ?, access_expires_at = ? WHERE order_id = ?",
                Timestamp.from(Instant.now().minusSeconds(2)), Timestamp.from(Instant.now().minusSeconds(1)), order.orderId());
        assertThatThrownBy(() -> access.verify(token)).isInstanceOf(ApiException.class);
    }
    @Test void simultaneousCancelRequestsConvergeOnOneReturn() throws Exception {
        var order = active(); CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { start.await(); cancellations.cancel(owner, order.orderId(), "a"); return true; });
            var b = pool.submit(() -> { start.await(); cancellations.cancel(owner, order.orderId(), "b"); return true; });
            start.countDown(); assertThat(a.get(20, TimeUnit.SECONDS)).isTrue(); assertThat(b.get(20, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(returns()).isEqualTo(1); assertThat(balance()).isEqualTo(20);
        assertThat(work.get(owner, order.orderId()).status()).isEqualTo("CANCELED");
    }
    @Test void returnRetriesStopAtFiveAndKeepLinkRevoked() throws Exception {
        var order = active(); String token = token(order.orderId());
        doThrow(new IllegalStateException("synthetic-wallet-failure"))
                .when(AopTestUtils.<WalletPurchaseService>getUltimateTargetObject(wallet)).compensatePurchase(any(), any(), any(), anyString(), any());
        cancellations.cancel(owner, order.orderId(), "cancel");
        for (int attempt = 2; attempt <= 5; attempt++) { due(order.orderId()); cancellations.recoverDue(); }
        assertThat(work.get(owner, order.orderId()).status()).isEqualTo("REVIEW_REQUIRED");
        assertThat(jdbc.queryForObject("SELECT attempts FROM gift_cancellations WHERE order_id = ?", Integer.class, order.orderId())).isEqualTo(5);
        reset(AopTestUtils.<WalletPurchaseService>getUltimateTargetObject(wallet));
        due(order.orderId()); cancellations.recoverDue();
        assertThat(returns()).isZero(); assertThat(balance()).isEqualTo(10);
        assertThatThrownBy(() -> access.verify(token)).isInstanceOf(ApiException.class);
    }
    @Test void realSessionRequiresCsrfAndOwnership() throws Exception {
        var order = draft(); String raw = "synthetic-gift-session-" + UUID.randomUUID();
        jdbc.update("INSERT INTO user_sessions(id,user_id,token_hash,csrf_secret_hash,expires_at,last_seen_at) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(), owner,
                java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8))),
                "b".repeat(64),
                Timestamp.from(Instant.now().plusSeconds(3600)), Timestamp.from(Instant.now()));
        Cookie cookie = new Cookie("SAJUPPUGI_SESSION", raw);
        mvc.perform(post("/api/v1/gift-orders/{id}/purchase", order.orderId()).cookie(cookie).header("Idempotency-Key", "buy"))
                .andExpect(status().isForbidden());
        String session = mvc.perform(get("/api/v1/session").cookie(cookie)).andReturn().getResponse().getContentAsString();
        String csrf = mapper.readTree(session).path("data").path("csrfToken").asText();
        mvc.perform(post("/api/v1/gift-orders/{id}/purchase", order.orderId()).cookie(cookie)
                .header("X-CSRF-Token", csrf).header("Idempotency-Key", "buy")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/gift-orders/{id}/cancel", order.orderId()).cookie(cookie).header("Idempotency-Key", "cancel"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/gift-orders/{id}/cancel", UUID.randomUUID()).cookie(cookie)
                .header("X-CSRF-Token", csrf).header("Idempotency-Key", "cancel")).andExpect(status().isNotFound());
        assertThat(balance()).isEqualTo(10); assertThat(returns()).isZero();
    }
    @Test void migrationAddsCancellationWithoutChangingExistingGrantOrEncryptedDelivery() throws Exception {
        var order = active(); String originalToken = token(order.orderId());
        String schema = "gift_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        String url = System.getenv("TEST_DATABASE_URL"), user = System.getenv("TEST_DATABASE_USERNAME"), password = System.getenv("TEST_DATABASE_PASSWORD");
        try (var connection = java.sql.DriverManager.getConnection(url, user, password)) {
            try {
                org.flywaydb.core.Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema)
                        .target(org.flywaydb.core.api.MigrationVersion.fromVersion("202610131500")).load().migrate();
                connection.createStatement().execute("SET search_path TO " + schema);
                for (String sql : List.of(
                        "INSERT INTO purchase_quotes SELECT * FROM public.purchase_quotes WHERE id = '" + order.quoteId() + "'",
                        "INSERT INTO gift_order_drafts SELECT id,user_id,quote_id,request_hash,product_code,product_name,shell_amount,recipient_phone_masked,private_payload,status,created_at,expires_at FROM public.gift_order_drafts WHERE id = '" + order.orderId() + "'",
                        "INSERT INTO gift_purchase_work SELECT order_id,status,failures,failure_code,next_attempt_at,wallet_transaction_id,charged_amount,approved_at,gift_id,access_expires_at FROM public.gift_purchase_work WHERE order_id = '" + order.orderId() + "'",
                        "INSERT INTO gift_purchase_tokens SELECT * FROM public.gift_purchase_tokens WHERE order_id = '" + order.orderId() + "'",
                        "INSERT INTO gift_delivery_outbox SELECT id,order_id,private_payload,status,created_at FROM public.gift_delivery_outbox WHERE order_id = '" + order.orderId() + "'"))
                    connection.createStatement().execute(sql);
                assertThat(org.flywaydb.core.Flyway.configure().dataSource(url,user,password).schemas(schema).defaultSchema(schema)
                        .target(org.flywaydb.core.api.MigrationVersion.fromVersion("202610131600")).load().migrate().migrationsExecuted).isEqualTo(1);
                try (var rows = connection.createStatement().executeQuery("SELECT status,charged_amount,first_provided_at FROM gift_purchase_work")) {
                    assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo("ACTIVE");
                    assertThat(rows.getInt(2)).isEqualTo(10); assertThat(rows.getObject(3)).isNull();
                }
                try (var rows = connection.createStatement().executeQuery("SELECT id,private_payload FROM gift_delivery_outbox")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(mapper.readTree(cipher.decrypt(rows.getString(2), "gift-delivery|" + rows.getObject(1))).path("token").asText()).isEqualTo(originalToken);
                }
            } finally {
                assertThat(schema).matches("gift_upgrade_[a-f0-9]{32}");
                connection.createStatement().execute("SET search_path TO public");
                connection.createStatement().execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }
}
