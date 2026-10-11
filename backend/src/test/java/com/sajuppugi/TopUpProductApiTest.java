package com.sajuppugi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.nullValue;

import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TopUpProductApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void inactiveCatalogReturnsEmptyWithoutAuthentication() throws Exception {
        mvc.perform(get("/api/v1/products").param("category", "TOP_UP"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void exposesServerPricesAndAllSixGrantAmountsWithoutWrites() throws Exception {
        jdbc.update("UPDATE products SET active = TRUE WHERE category = 'TOP_UP'");
        int orders = count("top_up_orders");
        int transactions = count("wallet_transactions");
        int lots = count("wallet_lots");
        mvc.perform(get("/api/v1/products").param("category", "TOP_UP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(6))
                .andExpect(jsonPath("$.data.items[*].price.amount").value(contains(1000, 3000, 5000, 10000, 30000, 50000)))
                .andExpect(jsonPath("$.data.items[*].paidAmount").value(contains(10, 30, 50, 100, 300, 500)))
                .andExpect(jsonPath("$.data.items[*].bonusAmount").value(contains(0, 3, 6, 14, 46, 80)))
                .andExpect(jsonPath("$.data.items[*].creditedAmount").value(contains(10, 33, 56, 114, 346, 580)))
                .andExpect(jsonPath("$.data.items[0].category").value("TOP_UP"))
                .andExpect(jsonPath("$.data.items[*].productName").value(contains(
                        "등껍질 10개 충전", "등껍질 30개 충전", "등껍질 50개 충전",
                        "등껍질 100개 충전", "등껍질 300개 충전", "등껍질 500개 충전")))
                .andExpect(jsonPath("$.data.items[0].price.currency").value("KRW"))
                .andExpect(jsonPath("$.data.items[0].active").value(true))
                .andExpect(jsonPath("$.data.items[0].id").doesNotExist());
        assertThat(count("top_up_orders")).isEqualTo(orders);
        assertThat(count("wallet_transactions")).isEqualTo(transactions);
        assertThat(count("wallet_lots")).isEqualTo(lots);
    }

    @Test
    void filtersInactiveNotStartedAndEndedProducts() throws Exception {
        jdbc.update("UPDATE products SET active = TRUE WHERE category = 'TOP_UP'");
        jdbc.update("UPDATE products SET active = FALSE WHERE code = 'TURTLE_SHELL_10'");
        jdbc.update("UPDATE products SET sale_start_at = ? WHERE code = 'TURTLE_SHELL_30'",
                Timestamp.from(Instant.now().plusSeconds(3600)));
        jdbc.update("UPDATE products SET sale_end_at = ? WHERE code = 'TURTLE_SHELL_50'",
                Timestamp.from(Instant.now().minusSeconds(3600)));
        mvc.perform(get("/api/v1/products").param("category", "TOP_UP"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[*].code").value(
                        contains("TURTLE_SHELL_100", "TURTLE_SHELL_300", "TURTLE_SHELL_500")));
    }

    @Test
    void rejectsMissingUnsupportedAndIncompatibleFilters() throws Exception {
        mvc.perform(get("/api/v1/products")).andExpect(status().isBadRequest());
        for (String category : java.util.List.of("GIFT", "UNKNOWN", "")) {
            mvc.perform(get("/api/v1/products").param("category", category))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        mvc.perform(get("/api/v1/products").param("category", "TOP_UP").param("fortuneType", "LOVE"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/products").param("category", "FORTUNE").param("fortuneType", "UNKNOWN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fortuneCatalogExposesOptionsNamesAndRegularMoneyWithoutAuthentication() throws Exception {
        mvc.perform(get("/api/v1/products").param("category", "FORTUNE").param("fortuneType", "LOVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].productName").value("애정운"))
                .andExpect(jsonPath("$.data.items[0].fortuneType").value("LOVE"))
                .andExpect(jsonPath("$.data.items[0].option").value("READING_ONLY"))
                .andExpect(jsonPath("$.data.items[0].saleEndsAt").value(nullValue()))
                .andExpect(jsonPath("$.data.items[0].price.amount").value(10))
                .andExpect(jsonPath("$.data.items[0].regularPrice").value(nullValue()))
                .andExpect(jsonPath("$.data.items[0].paidAmount").value(nullValue()))
                .andExpect(jsonPath("$.data.items[1].productName").value("애정운 + 부적"))
                .andExpect(jsonPath("$.data.items[1].price.amount").value(15))
                .andExpect(jsonPath("$.data.items[1].regularPrice.currency").value("TURTLE_SHELL"))
                .andExpect(jsonPath("$.data.items[1].regularPrice.amount").value(20));
    }

    @Test
    void suneungHasOnlyBundleAndStillRequiresExplicitActivation() throws Exception {
        jdbc.update("UPDATE products SET active = FALSE WHERE code = 'SUNEUNG_READING_WITH_TALISMAN'");
        mvc.perform(get("/api/v1/products").param("category", "FORTUNE").param("fortuneType", "SUNEUNG"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items").isEmpty());
        jdbc.update("UPDATE products SET active = TRUE WHERE code = 'SUNEUNG_READING_WITH_TALISMAN'");
        mvc.perform(get("/api/v1/products").param("category", "FORTUNE").param("fortuneType", "SUNEUNG"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].option").value("READING_WITH_TALISMAN"))
                .andExpect(jsonPath("$.data.items[0].regularPrice.amount").value(20));
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
