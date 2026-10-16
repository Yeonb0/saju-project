package com.sajuppugi.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductSaleStatusTest {
    private static final Instant NOW = Instant.parse("2026-10-16T00:00:00Z");

    @Test
    void displayLifecycleKeepsInactiveProductsRetiredAndSaleWindowsExplicit() {
        assertThat(product(true, NOW.plusSeconds(1), null).saleStatusAt(NOW))
                .isEqualTo(Product.SaleStatus.UPCOMING);
        assertThat(product(true, null, NOW).saleStatusAt(NOW))
                .isEqualTo(Product.SaleStatus.ENDED);
        assertThat(product(false, null, NOW.plusSeconds(1)).saleStatusAt(NOW))
                .isEqualTo(Product.SaleStatus.RETIRED);
        assertThat(product(true, null, null).saleStatusAt(NOW))
                .isEqualTo(Product.SaleStatus.AVAILABLE);
    }

    private Product product(boolean active, Instant startsAt, Instant endsAt) {
        return new Product(UUID.randomUUID(), "TEST", Product.Category.READING,
                new Price(Price.Currency.KRW, 1800), active, startsAt, endsAt, "test");
    }
}
