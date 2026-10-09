package com.sajuppugi;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajuppugi.catalog.application.CatalogUseCase;
import com.sajuppugi.common.idempotency.IdempotencyKey;
import com.sajuppugi.fortune.reading.application.FailedPurchaseCompensationService;
import com.sajuppugi.fortune.reading.application.SuneungPurchaseClaimService;
import com.sajuppugi.fortune.reading.port.ReadingRepository;
import com.sajuppugi.wallet.application.WalletPurchasePort;
import com.sajuppugi.wallet.application.WalletQueryUseCase;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** Opt-in only: intentionally retains synthetic rows for manual SELECT inspection. */
@EnabledIfSystemProperty(named = "wallet.demo.path", matches = ".+")
@SpringBootTest(properties = {"app.wallet.purchases-enabled=true", "app.wallet.recovery.enabled=false",
        "spring.datasource.url=jdbc:postgresql://localhost:55432/sajuppugi_test",
        "spring.datasource.username=sajuppugi_test", "spring.datasource.password=local-test-only"})
@ActiveProfiles("test")
class WalletPostgresDemoTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired CatalogUseCase catalog;
    @Autowired SuneungPurchaseClaimService claims;
    @Autowired ReadingRepository readings;
    @Autowired WalletPurchasePort wallet;
    @Autowired WalletQueryUseCase query;
    @Autowired FailedPurchaseCompensationService recovery;
    @Autowired ObjectMapper mapper;

    @Test
    void retainsSyntheticTwentyToFiveToTwentyHistoryForManualInspection() throws Exception {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("sajuppugi_test");
        UUID owner = UUID.randomUUID();
        UUID product = UUID.randomUUID();
        UUID lot = UUID.randomUUID();
        UUID grant = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO wallets(user_id, paid_balance, updated_at) VALUES (?, 20, ?)", owner, Timestamp.from(now));
        jdbc.update("""
                INSERT INTO wallet_lots(id, wallet_user_id, balance_type, granted_amount, remaining_amount,
                created_at, expires_at) VALUES (?, ?, 'PAID', 20, 20, ?, ?)
                """, lot, owner, Timestamp.from(now.minusSeconds(10)), Timestamp.from(now.plusSeconds(3600)));
        jdbc.update("""
                INSERT INTO wallet_transactions(id, wallet_user_id, type, total_amount, reference_id, created_at)
                VALUES (?, ?, 'TOP_UP', 20, ?, ?)
                """, grant, owner, UUID.randomUUID(), Timestamp.from(now.minusSeconds(10)));
        jdbc.update("INSERT INTO wallet_transaction_lines(transaction_id, lot_id, wallet_user_id, amount) VALUES (?, ?, ?, 20)",
                grant, lot, owner);
        String code = "LOCAL_DEMO_" + product;
        jdbc.update("""
                INSERT INTO products(id, code, category, price_currency, price_amount, active, catalog_version)
                VALUES (?, ?, 'READING', 'TURTLE_SHELL', 15, TRUE, 'local-demo')
                """, product, code);
        int before = query.getBalance(owner).balance();
        var quote = catalog.issueQuote(new CatalogUseCase.IssueQuote(owner, code, "a".repeat(64)));
        var purchase = claims.claim(owner, quote.id(), product, UUID.randomUUID());
        UUID token = UUID.randomUUID();
        assertThat(readings.tryClaimDebit(purchase.id(), token, now, now.minusSeconds(60))).isTrue();
        var key = new IdempotencyKey("local-demo:" + purchase.id());
        var debit = wallet.debit(owner, quote.id(), key);
        assertThat(wallet.debit(owner, quote.id(), key)).isEqualTo(debit);
        readings.markDebited(purchase.id(), token, debit.transactionId());
        readings.markGenerating(purchase.id());
        // Simulate a confirmed content failure, not a real Liner/PG failure.
        readings.markFailed(purchase.id());
        assertThat(recovery.recover(purchase.id())).isEqualTo(FailedPurchaseCompensationService.Outcome.REFUNDED);
        int after = query.getBalance(owner).balance();
        assertThat(before).isEqualTo(20);
        assertThat(debit.balance().balance()).isEqualTo(5);
        assertThat(after).isEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wallet_transactions WHERE wallet_user_id = ?", Integer.class, owner))
                .isEqualTo(3);
        var output = new LinkedHashMap<String, Object>();
        output.put("database", "sajuppugi_test");
        output.put("userId", owner);
        output.put("quoteId", quote.id());
        output.put("purchaseId", purchase.id());
        output.put("debitTransactionId", debit.transactionId());
        output.put("balanceBefore", before);
        output.put("balanceAfterDebit", debit.balance().balance());
        output.put("balanceAfterRecovery", after);
        output.put("purchaseStatus", "REFUNDED");
        Path path = Path.of(System.getProperty("wallet.demo.path"));
        Files.createDirectories(path.getParent());
        Files.writeString(path, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(output) + "\n", StandardCharsets.UTF_8);
    }
}
