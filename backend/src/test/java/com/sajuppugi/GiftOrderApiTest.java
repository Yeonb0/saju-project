package com.sajuppugi;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.jayway.jsonpath.JsonPath;
import com.sajuppugi.catalog.domain.Product;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.gift.application.GiftOrderService;
import com.sajuppugi.gift.application.GiftQuoteService;
import com.sajuppugi.gift.infrastructure.GiftPayloadCipher;
import com.sajuppugi.gift.infrastructure.JdbcGiftOrders;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.AopTestUtils;

/** Synthetic key and users only; independent committed transactions exercise unique-key races. */
@SpringBootTest(properties = {"app.gift.orders-enabled=true",
        "app.gift.payload-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GiftOrderApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired GiftQuoteService quotes;
    @Autowired GiftOrderService orders;
    @Autowired GiftPayloadCipher cipher;
    @MockitoSpyBean JdbcGiftOrders repository;
    private UUID owner;
    private boolean wasActive;
    private final GiftOrderService.Payload payload = new GiftOrderService.Payload("recipient", "01012345678", "sender", "Good luck!\nGo!");

    @BeforeEach void setUp() {
        owner = UUID.randomUUID();
        wasActive = jdbc.queryForObject("SELECT active FROM products WHERE code = 'GIFT_SUNEUNG_TALISMAN_ONLY'", Boolean.class);
        jdbc.update("UPDATE products SET active = TRUE WHERE code = 'GIFT_SUNEUNG_TALISMAN_ONLY'");
    }
    @AfterEach void cleanUp() {
        reset(AopTestUtils.<JdbcGiftOrders>getUltimateTargetObject(repository));
        jdbc.update("DELETE FROM gift_order_commands WHERE user_id = ?", owner);
        jdbc.update("DELETE FROM gift_order_drafts WHERE user_id = ?", owner);
        jdbc.update("DELETE FROM purchase_quotes WHERE requester_user_id = ?", owner);
        jdbc.update("UPDATE products SET active = ? WHERE code = 'GIFT_SUNEUNG_TALISMAN_ONLY'", wasActive);
    }
    private UUID quote() {
        return quotes.issue(owner, Product.FortuneType.SUNEUNG, GiftQuoteService.Option.TALISMAN_ONLY).funding().quote().id();
    }
    private String body(UUID quote) {
        return "{\"quoteId\":\"" + quote + "\",\"recipientName\":\"recipient\",\"recipientPhone\":\"01012345678\","
                + "\"senderName\":\"sender\",\"message\":\"Good luck!\\nGo!\"}";
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(UUID quote) {
        return post("/api/v1/gift-orders").with(user(owner.toString())).with(csrf())
                .header("Idempotency-Key", "gift-test").contentType(MediaType.APPLICATION_JSON).content(body(quote));
    }
    private void noMoneyWrites() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wallet_transactions WHERE wallet_user_id = ?", Integer.class, owner)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wallets WHERE user_id = ?", Integer.class, owner)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM top_up_orders WHERE user_id = ?", Integer.class, owner)).isZero();
    }

    @Test void createdIsNotChargedAndPrivateDataIsEncryptedAndMasked() throws Exception {
        UUID quote = quote();
        String response = mvc.perform(request(quote)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andExpect(jsonPath("$.data.plannedCharge.amount").value(10))
                .andExpect(jsonPath("$.data.charged").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.recipientPhoneMasked").value("010-****-5678"))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(response, "$.data.orderId"));
        String ciphertext = jdbc.queryForObject("SELECT private_payload FROM gift_order_drafts WHERE id = ?", String.class, id);
        assertThat(ciphertext).startsWith("v1:").doesNotContain("01012345678", "recipient", "Good luck!");
        assertThat(cipher.decrypt(ciphertext, owner + "|" + id)).contains("01012345678", "Good luck!");
        assertThat(response).doesNotContain("01012345678", "Good luck!", "requestHash", "privatePayload");
        mvc.perform(get("/api/v1/gift-orders/{id}", id).with(user(owner.toString()))).andExpect(status().isOk());
        assertThat(payload.toString()).doesNotContain("01012345678", "Good luck!");
        noMoneyWrites();
    }

    @Test void replayKeepsOriginalOrderAfterQuoteExpiryAndSaleClosure() throws Exception {
        UUID quote = quote();
        var initial = orders.create(owner, quote, payload, "first");
        Instant expired = Instant.now().minusSeconds(1);
        jdbc.update("UPDATE purchase_quotes SET created_at = ?, expires_at = ? WHERE id = ?",
                Timestamp.from(expired.minusSeconds(1800)), Timestamp.from(expired), quote);
        jdbc.update("UPDATE products SET active = FALSE WHERE code = 'GIFT_SUNEUNG_TALISMAN_ONLY'");
        assertThat(orders.create(owner, quote, payload, "first")).isEqualTo(initial);
        assertThat(orders.create(owner, quote, payload, "second")).isEqualTo(initial);
        assertThat(orders.get(owner, initial.orderId())).isEqualTo(initial);
        noMoneyWrites();
    }

    @Test void keyOrQuoteReuseWithDifferentPayloadIsRejectedAndNewDraftRollsBack() {
        UUID quote = quote();
        orders.create(owner, quote, payload, "first");
        var changed = new GiftOrderService.Payload("other", "01012345678", "sender", "changed");
        assertThatThrownBy(() -> orders.create(owner, quote, changed, "first"))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED));
        assertThatThrownBy(() -> orders.create(owner, quote, changed, "second")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> orders.create(owner, quote(), payload, "first")).isInstanceOf(ApiException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gift_order_drafts WHERE user_id = ?", Integer.class, owner)).isEqualTo(1);
        noMoneyWrites();
    }

    @Test void competingSameQuoteAndKeysConvergeOnOneOrder() throws Exception {
        UUID quote = quote();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { start.await(); return orders.create(owner, quote, payload, "a"); });
            var b = executor.submit(() -> { start.await(); return orders.create(owner, quote, payload, "b"); });
            start.countDown();
            assertThat(a.get(15, TimeUnit.SECONDS).orderId()).isEqualTo(b.get(15, TimeUnit.SECONDS).orderId());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gift_order_drafts WHERE user_id = ?", Integer.class, owner)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gift_order_commands WHERE user_id = ?", Integer.class, owner)).isEqualTo(2);
        noMoneyWrites();
    }

    @Test void sameKeyDifferentQuotesRaceRollsBackLosingDraft() throws Exception {
        UUID first = quote(), second = quote();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> createOrConflict(start, first));
            var b = executor.submit(() -> createOrConflict(start, second));
            start.countDown();
            assertThat(java.util.List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gift_order_drafts WHERE user_id = ?", Integer.class, owner)).isEqualTo(1);
        noMoneyWrites();
    }
    private boolean createOrConflict(CountDownLatch start, UUID quote) throws Exception {
        start.await();
        try { orders.create(owner, quote, payload, "shared"); return true; }
        catch (ApiException conflict) { assertThat(conflict.errorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED); return false; }
    }

    @Test void commandSaveFailureRollsBackEncryptedOrderToo() {
        UUID quote = quote();
        doThrow(new IllegalStateException("synthetic-save-failure"))
                .when(AopTestUtils.<JdbcGiftOrders>getUltimateTargetObject(repository)).bind(eq(owner), anyString(), any());
        assertThatThrownBy(() -> orders.create(owner, quote, payload, "a"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class).hasMessageContaining("synthetic-save-failure");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gift_order_drafts WHERE user_id = ?", Integer.class, owner)).isZero();
        reset(AopTestUtils.<JdbcGiftOrders>getUltimateTargetObject(repository));
        orders.create(owner, quote, payload, "a");
        noMoneyWrites();
    }

    @Test void ownershipAuthenticationCsrfAndInputsAreEnforced() throws Exception {
        UUID quote = quote();
        var order = orders.create(owner, quote, payload, "saved");
        mvc.perform(get("/api/v1/gift-orders/{id}", order.orderId()).with(user(UUID.randomUUID().toString()))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/gift-orders/{id}", UUID.randomUUID()).with(user(owner.toString()))).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/gift-orders").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(quote))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/gift-orders").with(user(owner.toString())).contentType(MediaType.APPLICATION_JSON).content(body(quote))).andExpect(status().isForbidden());
        mvc.perform(request(quote).with(user("invalid"))).andExpect(status().isUnauthorized());
        mvc.perform(request(quote).with(user(UUID.randomUUID().toString()))).andExpect(status().isNotFound());
        mvc.perform(request(quote).content(body(quote).replace("01012345678", "012")))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("RECIPIENT_PHONE_INVALID"));
        mvc.perform(post("/api/v1/gift-orders").with(user(owner.toString())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(quote))).andExpect(status().isBadRequest());
        assertThatThrownBy(() -> orders.create(owner, quote, new GiftOrderService.Payload("name", "01012345678", "sender", "x".repeat(201)), "long"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> orders.create(owner, quote, new GiftOrderService.Payload("name", "01012345678", "sender", "\u0000"), "control"))
                .isInstanceOf(ApiException.class);
        noMoneyWrites();
    }

    @Test void newOrdersRejectExpiredQuotesAndClosedProducts() {
        UUID quote = quote();
        jdbc.update("UPDATE products SET active = FALSE WHERE code = 'GIFT_SUNEUNG_TALISMAN_ONLY'");
        assertThatThrownBy(() -> orders.create(owner, quote, payload, "closed")).isInstanceOf(ApiException.class);
        jdbc.update("UPDATE products SET active = TRUE WHERE code = 'GIFT_SUNEUNG_TALISMAN_ONLY'");
        Instant expiry = Instant.now().minusSeconds(1);
        jdbc.update("UPDATE purchase_quotes SET created_at = ?, expires_at = ? WHERE id = ?",
                Timestamp.from(expiry.minusSeconds(1800)), Timestamp.from(expiry), quote);
        assertThatThrownBy(() -> orders.create(owner, quote, payload, "expired"))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.QUOTE_EXPIRED));
        noMoneyWrites();
    }
}
