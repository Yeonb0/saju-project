package com.sajuppugi;

import com.sajuppugi.catalog.application.CatalogUseCase;
import com.sajuppugi.catalog.domain.Price;
import com.sajuppugi.catalog.domain.Product;
import com.sajuppugi.catalog.domain.TopUpProduct;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TopUpCatalogTest {
    @Autowired CatalogUseCase catalog;
    @Autowired JdbcTemplate jdbc;

    @Test
    void registersSixConfirmedProductsWithoutActivatingSales() {
        assertThat(catalog.availableTopUps()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE category = 'TOP_UP'", Integer.class))
                .isEqualTo(6);
        jdbc.update("UPDATE products SET active = TRUE WHERE category = 'TOP_UP'");
        assertThat(catalog.availableTopUps()).extracting(item -> item.product().price().amount())
                .containsExactly(1000, 3000, 5000, 10000, 30000, 50000);
        assertThat(catalog.availableTopUps()).extracting(TopUpProduct::paidAmount)
                .containsExactly(10, 30, 50, 100, 300, 500);
        assertThat(catalog.availableTopUps()).extracting(TopUpProduct::bonusAmount)
                .containsExactly(0, 3, 6, 14, 46, 80);
        assertThat(catalog.availableTopUps()).extracting(TopUpProduct::creditedAmount)
                .containsExactly(10, 33, 56, 114, 346, 580);
    }

    @Test
    void filtersInactiveFutureAndEndedTopUps() {
        jdbc.update("UPDATE products SET active = TRUE WHERE category = 'TOP_UP'");
        jdbc.update("UPDATE products SET active = FALSE WHERE code = 'TURTLE_SHELL_10'");
        jdbc.update("UPDATE products SET sale_start_at = ? WHERE code = 'TURTLE_SHELL_30'",
                Timestamp.from(Instant.now().plusSeconds(3600)));
        jdbc.update("UPDATE products SET sale_end_at = ? WHERE code = 'TURTLE_SHELL_50'",
                Timestamp.from(Instant.now().minusSeconds(3600)));
        assertThat(catalog.availableTopUps()).extracting(item -> item.product().code())
                .containsExactly("TURTLE_SHELL_100", "TURTLE_SHELL_300", "TURTLE_SHELL_500");
    }

    @ParameterizedTest
    @CsvSource({"0,0", "-1,0", "10,-1", "2147483647,1"})
    void rejectsInvalidGrantsInDomainAndDatabase(int paid, int bonus) {
        var product = new Product(UUID.randomUUID(), "TEST", Product.Category.TOP_UP,
                new Price(Price.Currency.KRW, 1000), false, null, null, "test");
        assertThatThrownBy(() -> new TopUpProduct(product, paid, bonus)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE products SET paid_shell_amount = ?, bonus_shell_amount = ? WHERE code = 'TURTLE_SHELL_10'",
                paid, bonus)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsWrongCurrency() {
        var product = new Product(UUID.randomUUID(), "TEST", Product.Category.TOP_UP,
                new Price(Price.Currency.TURTLE_SHELL, 10), false, null, null, "test");
        assertThatThrownBy(() -> new TopUpProduct(product, 10, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE products SET price_currency = 'TURTLE_SHELL' WHERE code = 'TURTLE_SHELL_10'"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
