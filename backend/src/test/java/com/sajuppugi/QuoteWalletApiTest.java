package com.sajuppugi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sajuppugi.catalog.application.CatalogUseCase;
import com.sajuppugi.fortune.calculation.domain.BirthInput;
import com.sajuppugi.fortune.reading.port.ReadingSubjectPort;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class QuoteWalletApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired CatalogUseCase catalog;
    @Autowired Clock clock;
    @MockitoBean ReadingSubjectPort subjects;
    private UUID owner;
    private UUID other;
    private UUID person;
    private UUID counterpart;

    @BeforeEach
    void setUp() {
        owner = UUID.randomUUID();
        other = UUID.randomUUID();
        person = UUID.randomUUID();
        counterpart = UUID.randomUUID();
        for (UUID id : new UUID[] {person, counterpart}) {
            when(subjects.getOwnedSubject(owner, id)).thenReturn(new ReadingSubjectPort.OwnedSubject(id, "test",
                    new BirthInput(LocalDate.of(2004, 3, 15), LocalTime.of(14, 32), false,
                            BirthInput.CalendarType.SOLAR, false, BirthInput.Gender.FEMALE)));
        }
        jdbc.update("INSERT INTO wallets(user_id, updated_at) VALUES (?, ?)", owner,
                Timestamp.from(clock.instant()));
        jdbc.update("""
                INSERT INTO products(id, code, category, price_currency, price_amount, active, catalog_version)
                SELECT ?, 'SUNEUNG_READING_WITH_TALISMAN', 'READING', 'TURTLE_SHELL', 15, TRUE, 'test'
                WHERE NOT EXISTS (SELECT 1 FROM products WHERE code = 'SUNEUNG_READING_WITH_TALISMAN')
                """, UUID.randomUUID());
    }

    @ParameterizedTest
    @CsvSource({"overall,OVERALL_FLOW", "love,CURRENT_RELATIONSHIP", "wealth,OVERALL_WEALTH",
            "compatibility,OVERALL_MATCH", "sinsal,OVERALL_SINSAL"})
    void categoryQuotesExposeFundingWithOneChargedShape(String category, String question) throws Exception {
        lot(owner, "PAID", 7, -1, 3600);
        jdbc.update("UPDATE products SET active = FALSE WHERE category = 'TOP_UP'");
        jdbc.update("UPDATE products SET active = TRUE WHERE code = 'TURTLE_SHELL_10'");
        String extra = category.equals("compatibility")
                ? ",\"counterpartPersonId\":\"" + counterpart + "\",\"relationType\":\"LOVER\"" : "";
        mvc.perform(post("/api/v1/quotes/fortune/{category}", category)
                        .with(user(owner.toString())).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"personId":"%s","productOption":"READING_WITH_TALISMAN","questionKey":"%s"%s}
                                """.formatted(person, question, extra)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.charged.currency").value("TURTLE_SHELL"))
                .andExpect(jsonPath("$.data.charged.amount").value(15))
                .andExpect(jsonPath("$.data.walletBalance").value(7))
                .andExpect(jsonPath("$.data.balanceAfter").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.shortage").value(8))
                .andExpect(jsonPath("$.data.recommendedTopUp").value("TURTLE_SHELL_10"))
                .andExpect(jsonPath("$.data.currency").doesNotExist())
                .andExpect(jsonPath("$.data.amount").doesNotExist());
        assertNoWalletWrites();
    }

    @Test
    void suneungQuoteAndReloadUseCurrentBalanceWithoutConsumingTheQuote() throws Exception {
        lot(owner, "BONUS", 7, -1, 3600);
        jdbc.update("UPDATE products SET active = FALSE WHERE category = 'TOP_UP'");
        String response = mvc.perform(post("/api/v1/quotes/fortune").with(user(owner.toString())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"personId\":\"" + person + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.charged.amount").value(15))
                .andExpect(jsonPath("$.data.event.type").value("CSAT"))
                .andExpect(jsonPath("$.data.walletBalance").value(7))
                .andExpect(jsonPath("$.data.shortage").value(8))
                .andExpect(jsonPath("$.data.recommendedTopUp").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.currency").doesNotExist())
                .andExpect(jsonPath("$.data.amount").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        UUID quote = UUID.fromString(JsonPath.read(response, "$.data.quoteId"));
        lot(owner, "PAID", 10, -1, 3600);
        mvc.perform(get("/api/v1/quotes/{id}", quote).with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.charged.amount").value(15))
                .andExpect(jsonPath("$.data.walletBalance").value(17))
                .andExpect(jsonPath("$.data.balanceAfter").value(2))
                .andExpect(jsonPath("$.data.shortage").value(0))
                .andExpect(jsonPath("$.data.recommendedTopUp").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.contextHash").doesNotExist())
                .andExpect(jsonPath("$.data.requesterUserId").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT purchase_id FROM purchase_quotes WHERE id = ?", UUID.class, quote))
                .isNull();
        assertNoWalletWrites();
    }

    @Test
    void quoteOwnershipMissingAndExactExpiryAreEnforced() throws Exception {
        UUID quote = catalog.issueQuote(new CatalogUseCase.IssueQuote(owner,
                "LOVE_READING_ONLY", "a".repeat(64))).id();
        for (UUID id : new UUID[] {quote, UUID.randomUUID()}) {
            mvc.perform(get("/api/v1/quotes/{id}", id).with(user(other.toString())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        }
        jdbc.update("UPDATE purchase_quotes SET created_at = ?, expires_at = ? WHERE id = ?",
                Timestamp.from(clock.instant().minusSeconds(1801)),
                Timestamp.from(clock.instant().minusSeconds(1)), quote);
        mvc.perform(get("/api/v1/quotes/{id}", quote).with(user(owner.toString())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUOTE_EXPIRED"));
        mvc.perform(get("/api/v1/quotes/{id}", quote).with(user(other.toString())))
                .andExpect(status().isNotFound());
    }

    @Test
    void walletReportsOnlyOwnedUsablePaidAndBonusLots() throws Exception {
        lot(owner, "PAID", 10, -1, 3600);
        lot(owner, "BONUS", 3, -1, 3600);
        lot(owner, "PAID", 100, -3600, -1);
        lot(owner, "BONUS", 100, 3600, 7200);
        jdbc.update("INSERT INTO wallets(user_id, updated_at) VALUES (?, ?)", other,
                Timestamp.from(clock.instant()));
        lot(other, "PAID", 100, -1, 3600);
        mvc.perform(get("/api/v1/wallet").with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currency").value("TURTLE_SHELL"))
                .andExpect(jsonPath("$.data.balance").value(13))
                .andExpect(jsonPath("$.data.paidBalance").value(10))
                .andExpect(jsonPath("$.data.bonusBalance").value(3));
        UUID empty = UUID.randomUUID();
        mvc.perform(get("/api/v1/wallet").with(user(empty.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.balance").value(0));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wallets WHERE user_id = ?", Integer.class, empty))
                .isZero();
        assertNoWalletWrites();
    }

    @Test
    void readsRequireAuthenticationAndQuoteCreationStillRequiresCsrf() throws Exception {
        mvc.perform(get("/api/v1/quotes/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/wallet")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/wallet").with(user("not-a-uuid"))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/quotes/fortune").with(user(owner.toString()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"personId\":\"" + person + "\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_FAILED"));
    }

    private void lot(UUID owner, String type, int amount, long startSeconds, long endSeconds) {
        jdbc.update("""
                INSERT INTO wallet_lots(id, wallet_user_id, balance_type, granted_amount, remaining_amount,
                    created_at, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), owner, type, amount, amount,
                Timestamp.from(clock.instant().plusSeconds(startSeconds)),
                Timestamp.from(clock.instant().plusSeconds(endSeconds)));
    }

    private void assertNoWalletWrites() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wallet_transactions WHERE wallet_user_id = ?",
                Integer.class, owner)).isZero();
    }
}
