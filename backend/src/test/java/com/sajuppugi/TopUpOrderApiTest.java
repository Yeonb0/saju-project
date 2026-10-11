package com.sajuppugi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.sajuppugi.catalog.application.CatalogUseCase;
import com.sajuppugi.catalog.domain.Price;
import com.sajuppugi.catalog.domain.Product;
import com.sajuppugi.catalog.domain.TopUpProduct;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.payment.application.TopUpOrderService;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TopUpOrderApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TopUpOrderService orders;
    @MockitoBean CatalogUseCase catalog;
    UUID owner;

    @BeforeEach
    void setup() {
        owner = UUID.randomUUID();
        when(catalog.availableTopUps()).thenReturn(List.of(new TopUpProduct(
                new Product(UUID.randomUUID(), "TEST_TOPUP", Product.Category.TOP_UP,
                        new Price(Price.Currency.KRW, 5000), true, null, null, "test"), 50, 6)));
    }

    @AfterEach
    void cleanup() { jdbc.update("DELETE FROM top_up_orders WHERE user_id = ?", owner); }

    @Test
    void snapshotReplayAndOwnedLookupDoNotGrantWalletFunds() throws Exception {
        String first = create("key", "TEST_TOPUP", 201);
        String id = JsonPath.read(first, "$.data.orderId");
        String second = create("key", "TEST_TOPUP", 201);
        assertThat((String) JsonPath.read(second, "$.data.orderId")).isEqualTo(id);
        mvc.perform(get("/api/v1/top-up-orders/{id}", id).with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.paidShellAmount").value(50))
                .andExpect(jsonPath("$.data.bonusShellAmount").value(6))
                .andExpect(jsonPath("$.data.creditedShellAmount").value(56))
                .andExpect(jsonPath("$.data.status").value("PAYMENT_PENDING"))
                .andExpect(jsonPath("$.data.walletBalance").doesNotExist())
                .andExpect(jsonPath("$.data.keyHash").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM top_up_orders WHERE user_id = ?", Integer.class, owner))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wallet_transactions WHERE wallet_user_id = ?",
                Integer.class, owner)).isZero();
        when(catalog.availableTopUps()).thenReturn(List.of());
        assertThat(orders.create(owner, "TEST_TOPUP", "key").id().toString()).isEqualTo(id);
    }

    @Test
    void changedProductConflictsAndUnavailableProductDoesNotCreate() throws Exception {
        create("key", "TEST_TOPUP", 201);
        create("key", "OTHER", 409);
        create("new-key", "OTHER", 422);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM top_up_orders WHERE user_id = ?", Integer.class, owner))
                .isEqualTo(1);
    }

    @Test
    void ownershipAndMissingOrderHaveSameNotFound() throws Exception {
        var order = orders.create(owner, "TEST_TOPUP", "key");
        mvc.perform(get("/api/v1/top-up-orders/{id}", order.id()).with(user(UUID.randomUUID().toString())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/top-up-orders/{id}", UUID.randomUUID()).with(user(owner.toString())))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingKeyAndInvalidInputDoNotCreate() throws Exception {
        mvc.perform(post("/api/v1/top-up-orders").with(user(owner.toString())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"productCode\":\"TEST_TOPUP\"}"))
                .andExpect(status().isBadRequest());
        create("key", "", 400);
        create("x".repeat(513), "TEST_TOPUP", 400);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM top_up_orders WHERE user_id = ?", Integer.class, owner))
                .isZero();
    }

    @Test
    void authenticationAndCsrfAreRequired() throws Exception {
        mvc.perform(post("/api/v1/top-up-orders").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productCode\":\"TEST_TOPUP\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/top-up-orders").with(user(owner.toString())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productCode\":\"TEST_TOPUP\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/top-up-orders/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/top-up-orders").with(user("invalid-principal")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key", "key")
                .content("{\"productCode\":\"TEST_TOPUP\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentRequestsReturnOneCommittedOrder() throws Exception {
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { start.await(); return orders.create(owner, "TEST_TOPUP", "race"); });
            var b = pool.submit(() -> { start.await(); return orders.create(owner, "TEST_TOPUP", "race"); });
            start.countDown();
            assertThat(a.get(10, TimeUnit.SECONDS).id()).isEqualTo(b.get(10, TimeUnit.SECONDS).id());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM top_up_orders WHERE user_id = ?", Integer.class, owner))
                .isEqualTo(1);
        assertThatThrownBy(() -> orders.create(owner, "OTHER", "race")).isInstanceOf(ApiException.class);
    }

    @Test
    void sameKeyIsIsolatedBetweenUsersAndOnlyHashesAreStored() {
        UUID other = UUID.randomUUID();
        try {
            var a = orders.create(owner, "TEST_TOPUP", "private-key");
            var b = orders.create(other, "TEST_TOPUP", "private-key");
            assertThat(a.id()).isNotEqualTo(b.id());
            assertThat(jdbc.queryForObject("SELECT key_hash FROM top_up_orders WHERE id = ?", String.class, a.id()))
                    .matches("[a-f0-9]{64}").isNotEqualTo("private-key");
        } finally {
            jdbc.update("DELETE FROM top_up_orders WHERE user_id = ?", other);
        }
    }

    private String create(String key, String product, int expected) throws Exception {
        return mvc.perform(post("/api/v1/top-up-orders").with(user(owner.toString())).with(csrf())
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productCode\":\"" + product + "\"}"))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
    }
}
