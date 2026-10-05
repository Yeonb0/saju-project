package com.sajuppugi;

import com.sajuppugi.catalog.application.CatalogUseCase;
import com.sajuppugi.catalog.application.CatalogUseCase.IssueQuote;
import com.sajuppugi.catalog.application.QuoteFundingService;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
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
class QuoteFundingPersistenceTest {
    @Autowired CatalogUseCase catalog;
    @Autowired QuoteFundingService funding;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;

    @Test
    void joinsOwnedQuoteLiveWalletAndOnlyActiveTopUpsWithoutDebiting() {
        UUID user = UUID.randomUUID();
        var now = clock.instant();
        jdbc.update("""
                INSERT INTO products(id, code, category, price_currency, price_amount, active, catalog_version)
                VALUES (?, 'TEST_READING', 'READING', 'TURTLE_SHELL', 15, TRUE, 'test')
                """, UUID.randomUUID());
        jdbc.update("INSERT INTO wallets(user_id, updated_at) VALUES (?, ?)", user, Timestamp.from(now));
        jdbc.update("""
                INSERT INTO wallet_lots(id, wallet_user_id, balance_type, granted_amount,
                    remaining_amount, created_at, expires_at) VALUES (?, ?, 'BONUS', 3, 3, ?, ?)
                """, UUID.randomUUID(), user, Timestamp.from(now.minusSeconds(1)), Timestamp.from(now.plusSeconds(3600)));
        var quote = catalog.issueQuote(new IssueQuote(user, "TEST_READING", "a".repeat(64)));
        var gated = funding.get(user, quote.id());
        assertThat(gated.walletBalance()).isEqualTo(3);
        assertThat(gated.shortage()).isEqualTo(12);
        assertThat(gated.recommendedTopUp()).isNull();

        jdbc.update("UPDATE products SET active = TRUE WHERE code IN ('TURTLE_SHELL_10', 'TURTLE_SHELL_30')");
        var recommended = funding.get(user, quote.id());
        assertThat(recommended.recommendedTopUp()).isEqualTo("TURTLE_SHELL_30");
        assertThat(recommended.walletBalance()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wallet_transactions", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT purchase_id FROM purchase_quotes WHERE id = ?", UUID.class, quote.id()))
                .isNull();
    }
}
