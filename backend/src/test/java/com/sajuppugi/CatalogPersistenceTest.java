package com.sajuppugi;

import com.sajuppugi.catalog.application.CatalogService;
import com.sajuppugi.catalog.application.CatalogUseCase.IssueQuote;
import com.sajuppugi.catalog.domain.Product;
import com.sajuppugi.catalog.port.CatalogRepository;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CatalogPersistenceTest {
    private static final Instant NOW = Instant.parse("2026-10-05T00:00:00Z");
    private static final UUID USER = UUID.randomUUID();
    @Autowired JdbcTemplate jdbc;
    @Autowired CatalogRepository repository;
    private CatalogService service;

    @BeforeEach
    void setUp() {
        service = new CatalogService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void filtersInactiveFutureAndEndedProductsAndKeepsSaleBoundaries() {
        product("AVAILABLE", "READING", true, NOW, NOW.plusSeconds(1));
        product("INACTIVE", "READING", false, null, null);
        product("FUTURE", "READING", true, NOW.plusSeconds(1), null);
        product("ENDED", "READING", true, null, NOW);
        product("TOPUP", "TOP_UP", true, null, null);
        assertThat(service.availableProducts(Product.Category.READING)).extracting(Product::code)
                .contains("AVAILABLE").doesNotContain("INACTIVE", "FUTURE", "ENDED", "TOPUP");
        assertThat(service.availableProducts(null)).extracting(Product::code)
                .contains("AVAILABLE", "TOPUP").doesNotContain("INACTIVE", "FUTURE", "ENDED");
    }

    @Test
    void quotePersistsSnapshotRatherThanReadingNewCatalogPrice() {
        product("READING", "READING", true, null, null);
        var quote = service.issueQuote(new IssueQuote(USER, "READING", "a".repeat(64)));
        jdbc.update("UPDATE products SET price_amount = 20, catalog_version = 'new', active = FALSE WHERE code = 'READING'");
        assertThat(service.getQuote(USER, quote.id())).isEqualTo(quote);
        assertThat(quote.productSnapshot().price().amount()).isEqualTo(10);
        assertThat(quote.expiresAt()).isEqualTo(NOW.plusSeconds(1800));
    }

    @Test
    void hidesOtherUsersQuoteBeforeRevealingExpiry() {
        product("READING", "READING", true, null, null);
        var quote = service.issueQuote(new IssueQuote(USER, "READING", "a".repeat(64)));
        var expiredService = new CatalogService(repository, Clock.fixed(NOW.plusSeconds(1800), ZoneOffset.UTC));
        assertCode(() -> expiredService.getQuote(UUID.randomUUID(), quote.id()), ErrorCode.RESOURCE_NOT_FOUND);
        assertCode(() -> expiredService.getQuote(USER, quote.id()), ErrorCode.QUOTE_EXPIRED);
        assertCode(() -> service.getQuote(USER, UUID.randomUUID()), ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(new CatalogService(repository, Clock.fixed(NOW.plusSeconds(1799), ZoneOffset.UTC))
                .getQuote(USER, quote.id())).isEqualTo(quote);
    }

    @Test
    void rejectsUnknownUnavailableAndInvalidQuoteRequests() {
        product("INACTIVE", "READING", false, null, null);
        assertCode(() -> service.issueQuote(new IssueQuote(USER, "UNKNOWN", "a".repeat(64))), ErrorCode.PRODUCT_NOT_FOUND);
        assertCode(() -> service.issueQuote(new IssueQuote(USER, "INACTIVE", "a".repeat(64))), ErrorCode.PRODUCT_NOT_AVAILABLE);
        assertCode(() -> service.issueQuote(new IssueQuote(null, "INACTIVE", "a".repeat(64))), ErrorCode.INVALID_REQUEST);
        assertCode(() -> service.issueQuote(new IssueQuote(USER, "INACTIVE", "raw input")), ErrorCode.INVALID_REQUEST);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM purchase_quotes WHERE requester_user_id = ?",
                Integer.class, USER)).isZero();
    }

    @Test
    void bindsQuoteOnlyOnceWithoutOverwritingExistingPurchase() {
        product("READING", "READING", true, null, null);
        var quote = service.issueQuote(new IssueQuote(USER, "READING", "a".repeat(64)));
        UUID purchase = UUID.randomUUID();
        assertThat(repository.bindQuoteToPurchase(quote.id(), purchase)).isTrue();
        assertThat(repository.bindQuoteToPurchase(quote.id(), UUID.randomUUID())).isFalse();
        assertThat(jdbc.queryForObject("SELECT purchase_id FROM purchase_quotes WHERE id = ?", UUID.class, quote.id()))
                .isEqualTo(purchase);
    }

    private void product(String code, String category, boolean active, Instant starts, Instant ends) {
        boolean topUp = category.equals("TOP_UP");
        jdbc.update("""
                INSERT INTO products (id, code, category, price_currency, price_amount, active,
                sale_start_at, sale_end_at, catalog_version, paid_shell_amount, bonus_shell_amount)
                VALUES (?, ?, ?, ?, 10, ?, ?, ?, 'test-v1', ?, ?)
                """, UUID.randomUUID(), code, category, topUp ? "KRW" : "TURTLE_SHELL", active,
                starts == null ? null : Timestamp.from(starts), ends == null ? null : Timestamp.from(ends),
                topUp ? 10 : null, topUp ? 0 : null);
    }

    private void assertCode(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.errorCode()).isEqualTo(code));
    }
}
