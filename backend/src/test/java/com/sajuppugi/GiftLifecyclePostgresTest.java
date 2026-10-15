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
    @Autowired GiftFulfillmentPort fulfillment;
    @Autowired GiftRecipientService recipientService;
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
        jdbc.update("DELETE FROM gift_fulfillment_jobs WHERE gift_id IN (SELECT w.gift_id FROM gift_purchase_work w JOIN gift_order_drafts d ON d.id=w.order_id WHERE d.user_id=?)", owner);
        jdbc.update("DELETE FROM gift_recipient_profiles WHERE gift_id IN (SELECT w.gift_id FROM gift_purchase_work w JOIN gift_order_drafts d ON d.id=w.order_id WHERE d.user_id=?)", owner);
        jdbc.update("DELETE FROM gift_action_commands WHERE user_id = ?", owner);
        for (String table : List.of("gift_link_commands", "gift_cancellations", "gift_delivery_outbox", "gift_purchase_tokens", "gift_purchase_work"))
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
        return drafts.create(owner, quote.id(), new GiftOrderService.Payload("recipient", "sender", "Good luck!"), UUID.randomUUID().toString());
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
        String payload = jdbc.queryForObject("SELECT token_ciphertext FROM gift_purchase_tokens WHERE order_id = ?", String.class, order);
        long version = jdbc.queryForObject("SELECT token_version FROM gift_purchase_tokens WHERE order_id = ?", Long.class, order);
        return cipher.decrypt(payload, "gift-link|" + order + "|" + version);
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
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gift_purchase_tokens WHERE order_id = ?", Integer.class, order.orderId())).isEqualTo(1);
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
        assertThat(jdbc.queryForObject("SELECT revoked FROM gift_purchase_tokens WHERE order_id = ?", Boolean.class, order.orderId())).isTrue();
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
    @Test void publicAccessReturnsOnlyMinimalTextWithoutLoginOrCsrfAndDoesNotStartProvision() throws Exception {
        var order = active(); String token = token(order.orderId());
        String body = mapper.writeValueAsString(java.util.Map.of("token", token));
        String response = mvc.perform(post("/api/v1/public/gifts/access")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recipientName").value("recipient"))
                .andExpect(jsonPath("$.data.senderName").value("sender"))
                .andExpect(jsonPath("$.data.message").value("Good luck!"))
                .andExpect(jsonPath("$.data.option").value("TALISMAN_ONLY"))
                .andExpect(jsonPath("$.data.state").value("INPUT_REQUIRED"))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain(owner.toString(), order.orderId().toString(), token,
                "tokenHash", "privatePayload", "objectKey");
        assertThat(jdbc.queryForObject("SELECT first_provided_at FROM gift_purchase_work WHERE order_id = ?",
                Timestamp.class, order.orderId())).isNull();

        fulfillment.accept(token, UUID.randomUUID(), "a".repeat(64), () -> {});
        mvc.perform(post("/api/v1/public/gifts/access")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.state").value("PROCESSING"));
    }
    @Test void mutatedAndUnknownPublicTokensHaveTheSameSafeResponse() throws Exception {
        var order = active(); String token = token(order.orderId());
        String mutated = (token.charAt(0) == 'A' ? "B" : "A") + token.substring(1);
        String unknown = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        for (String candidate : java.util.List.of(mutated, unknown)) {
            mvc.perform(post("/api/v1/public/gifts/access")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(java.util.Map.of("token", candidate))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                    .andExpect(jsonPath("$.details").isEmpty());
        }
    }
    @Test void recipientSubmissionEncryptsOneProfileAndOneIntentAndReplaysWithoutBirthData() throws Exception {
        var order = active(); String token = token(order.orderId());
        UUID giftId = access.verify(token).giftId();
        String body = mapper.writeValueAsString(java.util.Map.of(
                "token", token, "displayName", " 수험생 ", "birthDate", "2007-03-14",
                "birthTime", "09:30", "birthTimeUnknown", false, "calendarType", "SOLAR",
                "leapMonth", false, "gender", "UNSPECIFIED"));
        String first = mvc.perform(post("/api/v1/public/gifts/recipient-profile")
                        .header("Idempotency-Key", "recipient-once")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.state").value("ACCEPTED"))
                .andReturn().getResponse().getContentAsString();
        String replay = mvc.perform(post("/api/v1/public/gifts/recipient-profile")
                        .header("Idempotency-Key", "recipient-once")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(replay).path("data")).isEqualTo(mapper.readTree(first).path("data"));
        assertThat(first).doesNotContain("2007-03-14", "09:30", "수험생", token, "snapshotHash");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gift_recipient_profiles WHERE gift_id=?", Integer.class, giftId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gift_fulfillment_jobs WHERE gift_id=?", Integer.class, giftId)).isEqualTo(1);
        String encrypted = jdbc.queryForObject("SELECT profile_ciphertext FROM gift_recipient_profiles WHERE gift_id=?", String.class, giftId);
        assertThat(encrypted).matches("v(?:1|2:[^:]+):.+").doesNotContain("2007-03-14", "수험생");
        assertThat(jdbc.queryForObject("SELECT state FROM gift_fulfillment_jobs WHERE gift_id=?", String.class, giftId)).isEqualTo("PENDING");

        mvc.perform(post("/api/v1/public/gifts/recipient-profile")
                        .header("Idempotency-Key", "different-request")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"))
                .andExpect(jsonPath("$.data").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gift_recipient_profiles WHERE gift_id=?", Integer.class, giftId)).isEqualTo(1);
    }
    @Test void invalidRecipientInputCreatesNeitherReceiptProfileNorIntent() throws Exception {
        var order = active(); String token = token(order.orderId());
        UUID giftId = access.verify(token).giftId();
        String body = mapper.writeValueAsString(java.util.Map.of(
                "token", token, "displayName", "recipient", "birthDate", "2099-01-01",
                "birthTimeUnknown", true, "calendarType", "SOLAR", "leapMonth", false, "gender", "FEMALE"));
        mvc.perform(post("/api/v1/public/gifts/recipient-profile")
                        .header("Idempotency-Key", "invalid-future")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gift_recipient_receipts WHERE order_id=?", Integer.class,
                order.orderId())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gift_recipient_profiles WHERE gift_id=?", Integer.class, giftId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gift_fulfillment_jobs WHERE gift_id=?", Integer.class, giftId)).isZero();
    }
    @Test void recipientProfileAndReceiptRollBackTogetherWhenIntentSaveFails() throws Exception {
        var order = active(); String token = token(order.orderId()); UUID giftId = access.verify(token).giftId();
        var command = new GiftRecipientService.Command("recipient", "2007-03-14", null, true,
                com.sajuppugi.fortune.calculation.domain.BirthInput.CalendarType.SOLAR, false,
                com.sajuppugi.fortune.calculation.domain.BirthInput.Gender.FEMALE);
        jdbc.execute("ALTER TABLE gift_fulfillment_jobs ADD CONSTRAINT synthetic_gift_intent_failure CHECK (state <> 'PENDING')");
        try { assertThatThrownBy(() -> recipientService.submit(token, "atomic", command)).isInstanceOf(RuntimeException.class); }
        finally { jdbc.execute("ALTER TABLE gift_fulfillment_jobs DROP CONSTRAINT synthetic_gift_intent_failure"); }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gift_recipient_receipts WHERE order_id=?", Integer.class,
                order.orderId())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gift_recipient_profiles WHERE gift_id=?", Integer.class, giftId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gift_fulfillment_jobs WHERE gift_id=?", Integer.class, giftId)).isZero();
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
}
