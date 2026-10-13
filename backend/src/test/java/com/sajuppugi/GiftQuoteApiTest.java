package com.sajuppugi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class GiftQuoteApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private UUID owner;
    @Test void orderDraftsRemainDisabledByDefault() throws Exception {
        mvc.perform(post("/api/v1/gift-orders").with(user(UUID.randomUUID().toString())).with(csrf())
                .header("Idempotency-Key", "disabled").contentType(MediaType.APPLICATION_JSON)
                .content("{\"quoteId\":\"" + UUID.randomUUID() + "\",\"recipientName\":\"recipient\",\"recipientPhone\":\"01012345678\",\"senderName\":\"sender\",\"message\":\"hello\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("GIFT_FULFILLMENT_UNAVAILABLE"));
        assertNoMoneyWrites();
    }
    @BeforeEach void setUp() {
        owner = UUID.randomUUID();
        jdbc.update("UPDATE products SET active = TRUE WHERE code IN ('GIFT_SUNEUNG_TALISMAN_ONLY', 'GIFT_SUNEUNG_READING_WITH_TALISMAN')");
        jdbc.update("UPDATE products SET active = FALSE WHERE category = 'TOP_UP'");
    }

    @ParameterizedTest @CsvSource({"TALISMAN_ONLY,10,3", "READING_WITH_TALISMAN,15,4"})
    void quotesUseServerPricesAndOptionContentsWithoutMoneyWrites(String option, int price, int contents) throws Exception {
        mvc.perform(request(option)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.charged.currency").value("TURTLE_SHELL"))
                .andExpect(jsonPath("$.data.charged.amount").value(price))
                .andExpect(jsonPath("$.data.option").value(option))
                .andExpect(jsonPath("$.data.recipientCount").value(1))
                .andExpect(jsonPath("$.data.includedContents.length()").value(contents))
                .andExpect(jsonPath("$.data.walletBalance").value(0))
                .andExpect(jsonPath("$.data.shortage").value(price))
                .andExpect(jsonPath("$.data.balanceAfter").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.recommendedTopUp").value(org.hamcrest.Matchers.nullValue()));
        assertThat(count("purchase_quotes", "requester_user_id")).isEqualTo(1);
        assertNoMoneyWrites();
    }

    @Test void shortageRecommendationUsesAvailableTopUpsAndCountsCurrentLots() throws Exception {
        jdbc.update("INSERT INTO wallets(user_id, updated_at) VALUES (?, ?)", owner, Timestamp.from(Instant.now()));
        lot(7, -1, 3600); lot(100, -7200, -3600); lot(100, 3600, 7200);
        jdbc.update("UPDATE products SET active = TRUE WHERE code = 'TURTLE_SHELL_10'");
        mvc.perform(request("READING_WITH_TALISMAN")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.walletBalance").value(7))
                .andExpect(jsonPath("$.data.shortage").value(8))
                .andExpect(jsonPath("$.data.recommendedTopUp").value("TURTLE_SHELL_10"));
        assertThat(count("wallet_transactions", "wallet_user_id")).isZero();
        assertThat(count("wallet_lots", "wallet_user_id")).isEqualTo(3);
        lot(8, -1, 3600);
        mvc.perform(request("READING_WITH_TALISMAN")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.shortage").value(0))
                .andExpect(jsonPath("$.data.balanceAfter").value(0));
    }

    @Test void ownedReadsKeepPriceSnapshotAndRejectOtherOwnersAndExpiry() throws Exception {
        UUID quote = quote("TALISMAN_ONLY");
        jdbc.update("UPDATE products SET active = FALSE, price_amount = 20, product_name = 'changed' WHERE code = 'GIFT_SUNEUNG_TALISMAN_ONLY'");
        mvc.perform(get("/api/v1/gift-quotes/{id}", quote).with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.charged.amount").value(10))
                .andExpect(jsonPath("$.data.productName").value("수능 선물 부적"));
        mvc.perform(get("/api/v1/gift-quotes/{id}", quote).with(user(UUID.randomUUID().toString())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/gift-quotes/{id}", UUID.randomUUID()).with(user(owner.toString())))
                .andExpect(status().isNotFound());
        Instant expiry = Instant.now().minusSeconds(1);
        jdbc.update("UPDATE purchase_quotes SET created_at = ?, expires_at = ? WHERE id = ?",
                Timestamp.from(expiry.minusSeconds(1800)), Timestamp.from(expiry), quote);
        mvc.perform(get("/api/v1/gift-quotes/{id}", quote).with(user(owner.toString())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("QUOTE_EXPIRED"));
        assertNoMoneyWrites();
    }

    @Test void disabledOrOutOfSaleProductsCannotIssueQuotes() throws Exception {
        jdbc.update("UPDATE products SET active = FALSE WHERE category = 'GIFT'");
        mvc.perform(request("TALISMAN_ONLY")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_AVAILABLE"));
        jdbc.update("UPDATE products SET active = TRUE, sale_end_at = ? WHERE category = 'GIFT'", Timestamp.from(Instant.now().minusSeconds(1)));
        mvc.perform(request("TALISMAN_ONLY")).andExpect(status().isUnprocessableEntity());
        assertThat(count("purchase_quotes", "requester_user_id")).isZero();
    }

    @Test void authenticationCsrfAndInputsAreEnforced() throws Exception {
        String body = "{\"fortuneType\":\"SUNEUNG\",\"option\":\"TALISMAN_ONLY\"}";
        mvc.perform(post("/api/v1/gift-quotes").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/gift-quotes").with(user(owner.toString())).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        for (String invalid : java.util.List.of("{}", body.replace("SUNEUNG", "LOVE"), body.replace("TALISMAN_ONLY", "READING_ONLY"))) {
            mvc.perform(request("TALISMAN_ONLY").content(invalid)).andExpect(status().isBadRequest());
        }
        mvc.perform(request("TALISMAN_ONLY").with(user("not-a-uuid"))).andExpect(status().isUnauthorized());
        assertThat(count("purchase_quotes", "requester_user_id")).isZero();
        assertNoMoneyWrites();
    }

    private UUID quote(String option) throws Exception {
        String json = mvc.perform(request(option)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(json, "$.data.quoteId"));
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(String option) {
        return post("/api/v1/gift-quotes").with(user(owner.toString())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fortuneType\":\"SUNEUNG\",\"option\":\"" + option + "\"}");
    }
    private void lot(int amount, long created, long expires) {
        Instant now = Instant.now();
        jdbc.update("INSERT INTO wallet_lots(id, wallet_user_id, balance_type, granted_amount, remaining_amount, created_at, expires_at) VALUES (?, ?, 'PAID', ?, ?, ?, ?)",
                UUID.randomUUID(), owner, amount, amount, Timestamp.from(now.plusSeconds(created)), Timestamp.from(now.plusSeconds(expires)));
    }
    private int count(String table, String column) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, owner);
    }
    private void assertNoMoneyWrites() {
        assertThat(count("wallets", "user_id")).isZero();
        assertThat(count("wallet_lots", "wallet_user_id")).isZero();
        assertThat(count("wallet_transactions", "wallet_user_id")).isZero();
        assertThat(count("top_up_orders", "user_id")).isZero();
    }
}
