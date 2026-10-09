package com.sajuppugi;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;

import com.sajuppugi.catalog.application.CatalogUseCase;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.common.idempotency.IdempotencyKey;
import com.sajuppugi.wallet.application.WalletPurchasePort;
import com.sajuppugi.wallet.application.WalletPurchaseService;
import com.sajuppugi.wallet.domain.WalletBalance;
import com.sajuppugi.wallet.port.WalletPurchaseRepository;
import com.sajuppugi.wallet.port.WalletRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Real commits and separate thread/DB transactions, not a surrounding rollback test. */
@SpringBootTest(properties = "app.wallet.purchases-enabled=true")
@ActiveProfiles("test")
class WalletPurchasePersistenceTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired WalletPurchasePort wallet;
    @Autowired CatalogUseCase catalog;
    @Autowired WalletRepository walletRepository;
    @Autowired Clock clock;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoSpyBean WalletPurchaseRepository purchases;
    private UUID owner;
    private UUID product;
    private String code;
    private Instant now;

    @BeforeEach
    void setUp() {
        owner = UUID.randomUUID();
        product = UUID.randomUUID();
        code = "TEST_WALLET_" + product;
        now = Instant.now();
        jdbc.update("INSERT INTO wallets(user_id, updated_at) VALUES (?, ?)", owner, Timestamp.from(now));
        jdbc.update("""
                INSERT INTO products(id, code, category, price_currency, price_amount, active, catalog_version)
                VALUES (?, ?, 'READING', 'TURTLE_SHELL', 15, TRUE, 'wallet-test')
                """, product, code);
    }

    @AfterEach
    void cleanUp() {
        // Only this test's synthetic owner; no shared-table cleanup or production identifiers.
        for (String table : List.of("wallet_purchase_commands", "wallet_purchase_compensations",
                "wallet_purchase_debits", "wallet_transaction_lines")) {
            jdbc.update("DELETE FROM " + table + " WHERE wallet_user_id = ?", owner);
        }
        jdbc.update("DELETE FROM wallet_transactions WHERE wallet_user_id = ? AND reversal_of_id IS NOT NULL", owner);
        jdbc.update("DELETE FROM wallet_transactions WHERE wallet_user_id = ?", owner);
        jdbc.update("DELETE FROM wallet_lots WHERE wallet_user_id = ?", owner);
        jdbc.update("DELETE FROM wallets WHERE user_id = ?", owner);
        jdbc.update("DELETE FROM purchase_quotes WHERE requester_user_id = ?", owner);
        jdbc.update("DELETE FROM products WHERE id = ?", product);
    }

    @Test
    void debitsLotsByExpiryThenBonusAndPersistsBalancedLedger() {
        UUID earlyPaid = grant("PAID", 4, 60, -30);
        UUID bonus = grant("BONUS", 8, 120, -10);
        UUID paid = grant("PAID", 8, 120, -20);
        UUID quote = quote();
        var result = wallet.debit(owner, quote, key("buy"));
        assertThat(result.debitedAmount()).isEqualTo(15);
        assertThat(result.balance()).isEqualTo(new WalletBalance(5, 0));
        assertThat(remaining(earlyPaid)).isZero();
        assertThat(remaining(bonus)).isZero();
        assertThat(remaining(paid)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT total_amount FROM wallet_transactions WHERE id = ?",
                Integer.class, result.transactionId())).isEqualTo(-15);
        assertThat(jdbc.queryForObject("SELECT SUM(amount) FROM wallet_transaction_lines WHERE transaction_id = ?",
                Integer.class, result.transactionId())).isEqualTo(-15);
        assertThat(projection()).isEqualTo(result.balance());
    }

    @Test
    void sameQuoteWithSameOrDifferentKeyNeverDebitsTwiceEvenAfterExpiry() {
        grant("PAID", 30, 3600, -10);
        UUID quote = quote();
        var first = wallet.debit(owner, quote, key("first"));
        expireQuote(quote);
        assertThat(wallet.debit(owner, quote, key("first"))).isEqualTo(first);
        assertThat(wallet.debit(owner, quote, key("different"))).isEqualTo(first);
        assertThat(count("PURCHASE")).isEqualTo(1);
        assertThat(projection().balance()).isEqualTo(15);
    }

    @Test
    void reusedKeyWithDifferentQuoteRollsBackWithoutAdditionalDebit() {
        grant("PAID", 30, 3600, -10);
        wallet.debit(owner, quote(), key("one"));
        assertCode(() -> wallet.debit(owner, quote(), key("one")), ErrorCode.IDEMPOTENCY_KEY_REUSED);
        assertThat(count("PURCHASE")).isEqualTo(1);
        assertThat(projection().balance()).isEqualTo(15);
    }

    @Test
    void shortageIgnoresExpiredAndFutureLotsAndDoesNotPersistAnyDebit() {
        grant("PAID", 5, 3600, -10);
        grant("PAID", 100, -1, -3600);
        grant("BONUS", 100, 7200, 3600);
        assertCode(() -> wallet.debit(owner, quote(), key("poor")), ErrorCode.INSUFFICIENT_BALANCE);
        assertThat(count("PURCHASE")).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wallet_purchase_commands WHERE wallet_user_id = ?",
                Integer.class, owner)).isZero();
    }

    @Test
    void wrongOwnerMissingAndExpiredQuotesCannotDebit() {
        grant("PAID", 30, 3600, -10);
        UUID quote = quote();
        jdbc.update("UPDATE purchase_quotes SET requester_user_id = ? WHERE id = ?", UUID.randomUUID(), quote);
        assertCode(() -> wallet.debit(owner, quote, key("other")), ErrorCode.RESOURCE_NOT_FOUND);
        jdbc.update("UPDATE purchase_quotes SET requester_user_id = ? WHERE id = ?", owner, quote);
        assertCode(() -> wallet.debit(owner, UUID.randomUUID(), key("missing")), ErrorCode.RESOURCE_NOT_FOUND);
        expireQuote(quote);
        assertCode(() -> wallet.debit(owner, quote, key("expired")), ErrorCode.QUOTE_EXPIRED);
        assertThat(count("PURCHASE")).isZero();
    }

    @Test
    void disabledProductCannotDebitButPriceChangesKeepQuotedPrice() {
        grant("PAID", 30, 3600, -10);
        UUID quote = quote();
        jdbc.update("UPDATE products SET active = FALSE WHERE id = ?", product);
        assertCode(() -> wallet.debit(owner, quote, key("inactive")), ErrorCode.PRODUCT_NOT_AVAILABLE);
        jdbc.update("UPDATE products SET active = TRUE, price_amount = 20 WHERE id = ?", product);
        assertThat(wallet.debit(owner, quote, key("snapshot")).debitedAmount()).isEqualTo(15);
    }

    @Test
    void failedCommandWriteRollsBackLotsLedgerProjectionAndDebitReceipt() {
        UUID lot = grant("PAID", 20, 3600, -10);
        UUID quote = quote();
        doThrow(new IllegalStateException("test interruption before commit"))
                .when(AopTestUtils.<WalletPurchaseRepository>getUltimateTargetObject(purchases))
                .saveCommand(eq(owner), anyString(), anyString(), any(), any());
        assertThatThrownBy(() -> wallet.debit(owner, quote, key("fault")))
                .hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(remaining(lot)).isEqualTo(20);
        assertThat(count("PURCHASE")).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wallet_purchase_debits WHERE quote_id = ?",
                Integer.class, quote)).isZero();
        assertThat(projection().balance()).isEqualTo(20);
    }

    @Test
    void compensatesOriginalAllocationsOnceAndCanReplayAfterNewServiceInstance() {
        UUID paid = grant("PAID", 10, 120, -20);
        UUID bonus = grant("BONUS", 10, 120, -10);
        var debit = wallet.debit(owner, quote(), key("debit"));
        var refund = wallet.compensate(debit.transactionId(), "READING_GENERATION_FAILED", key("refund"));
        assertThat(remaining(paid)).isEqualTo(10);
        assertThat(remaining(bonus)).isEqualTo(10);
        assertThat(refund.balance()).isEqualTo(new WalletBalance(10, 10));
        assertThat(wallet.compensate(debit.transactionId(), "READING_GENERATION_FAILED", key("refund"))).isEqualTo(refund);
        assertThat(wallet.compensate(debit.transactionId(), "READING_GENERATION_FAILED", key("new-key"))).isEqualTo(refund);
        var newInstance = new WalletPurchaseService(walletRepository, purchases, catalog, clock);
        var replay = new TransactionTemplate(transactionManager).execute(status ->
                newInstance.compensate(debit.transactionId(), "READING_GENERATION_FAILED", key("after-instance-replacement")));
        assertThat(replay).isEqualTo(refund);
        assertThat(count("REFUND")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT SUM(total_amount) FROM wallet_transactions WHERE wallet_user_id = ?",
                Integer.class, owner)).isEqualTo(20);
        assertCode(() -> wallet.compensate(debit.transactionId(), "ANOTHER_REASON", key("refund")),
                ErrorCode.IDEMPOTENCY_KEY_REUSED);
    }

    @Test
    void compensationDoesNotExtendExpiredLotsOrMakeThemSpendable() {
        UUID lot = grant("PAID", 20, 3600, -3600);
        var debit = wallet.debit(owner, quote(), key("debit"));
        Instant expiry = Instant.now().minusSeconds(1).truncatedTo(ChronoUnit.MICROS);
        jdbc.update("UPDATE wallet_lots SET expires_at = ? WHERE id = ?", Timestamp.from(expiry), lot);
        var refund = wallet.compensate(debit.transactionId(), "READING_GENERATION_FAILED", key("refund"));
        assertThat(remaining(lot)).isEqualTo(20);
        assertThat(refund.balance().balance()).isZero();
        assertThat(jdbc.queryForObject("SELECT expires_at FROM wallet_lots WHERE id = ?", Timestamp.class, lot))
                .isEqualTo(Timestamp.from(expiry));
    }

    @Test
    void concurrentDifferentQuotesCannotOverspendOneWallet() throws Exception {
        grant("PAID", 20, 3600, -10);
        UUID first = quote();
        UUID second = quote();
        var outcomes = concurrent(() -> outcome(first, "one"), () -> outcome(second, "two"));
        assertThat(outcomes).containsExactlyInAnyOrder("OK", "INSUFFICIENT_BALANCE");
        assertThat(projection().balance()).isEqualTo(5);
        assertThat(count("PURCHASE")).isEqualTo(1);
    }

    @Test
    void concurrentSameQuoteAndDifferentKeysHaveOneDebit() throws Exception {
        grant("PAID", 20, 3600, -10);
        UUID quote = quote();
        var outcomes = concurrent(() -> wallet.debit(owner, quote, key("one")).transactionId(),
                () -> wallet.debit(owner, quote, key("two")).transactionId());
        assertThat(outcomes.get(0)).isEqualTo(outcomes.get(1));
        assertThat(count("PURCHASE")).isEqualTo(1);
        assertThat(projection().balance()).isEqualTo(5);
    }

    @Test
    void concurrentCompensationsHaveOneReversal() throws Exception {
        grant("PAID", 20, 3600, -10);
        var debit = wallet.debit(owner, quote(), key("buy"));
        var outcomes = concurrent(
                () -> wallet.compensate(debit.transactionId(), "READING_GENERATION_FAILED", key("one")).transactionId(),
                () -> wallet.compensate(debit.transactionId(), "READING_GENERATION_FAILED", key("two")).transactionId());
        assertThat(outcomes.get(0)).isEqualTo(outcomes.get(1));
        assertThat(count("REFUND")).isEqualTo(1);
        assertThat(projection().balance()).isEqualTo(20);
    }

    @Test
    void failedCompensationWriteRollsBackAndRetryRestoresOnlyOnce() {
        UUID lot = grant("PAID", 20, 3600, -10);
        var debit = wallet.debit(owner, quote(), key("debit"));
        var target = AopTestUtils.<WalletPurchaseRepository>getUltimateTargetObject(purchases);
        doThrow(new IllegalStateException("test refund commit failure")).doCallRealMethod()
                .when(target).saveCommand(eq(owner), anyString(), anyString(), any(), any());
        assertThatThrownBy(() -> wallet.compensate(debit.transactionId(), "READING_GENERATION_FAILED", key("refund")))
                .hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(remaining(lot)).isEqualTo(5);
        assertThat(count("REFUND")).isZero();
        assertThat(projection().balance()).isEqualTo(5);
        wallet.compensate(debit.transactionId(), "READING_GENERATION_FAILED", key("refund"));
        assertThat(remaining(lot)).isEqualTo(20);
        assertThat(count("REFUND")).isEqualTo(1);
    }

    @Test
    void missingWalletAndNonPurchaseCompensationFailClosed() {
        assertCode(() -> wallet.debit(UUID.randomUUID(), quote(), key("no-wallet")), ErrorCode.INSUFFICIENT_BALANCE);
        grant("PAID", 20, 3600, -10);
        UUID topUp = jdbc.queryForObject("SELECT id FROM wallet_transactions WHERE wallet_user_id = ?",
                UUID.class, owner);
        assertCode(() -> wallet.compensate(topUp, "READING_GENERATION_FAILED", key("bad-refund")),
                ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(count("REFUND")).isZero();
    }

    private String outcome(UUID quote, String key) {
        try { wallet.debit(owner, quote, key(key)); return "OK"; }
        catch (ApiException exception) { return exception.errorCode().name(); }
    }

    private <T> List<T> concurrent(Callable<T> first, Callable<T> second) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = List.of(first, second).stream().map(task -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test start timed out");
                return task.call();
            })).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(futures.get(0).get(15, TimeUnit.SECONDS), futures.get(1).get(15, TimeUnit.SECONDS));
        }
    }

    private UUID grant(String type, int amount, long expiryOffset, long createdOffset) {
        UUID lot = UUID.randomUUID();
        UUID transaction = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO wallet_lots(id, wallet_user_id, balance_type, granted_amount, remaining_amount,
                created_at, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?)
                """, lot, owner, type, amount, amount, Timestamp.from(now.plusSeconds(createdOffset)),
                Timestamp.from(now.plusSeconds(expiryOffset)));
        jdbc.update("""
                INSERT INTO wallet_transactions(id, wallet_user_id, type, total_amount, reference_id, created_at)
                VALUES (?, ?, 'TOP_UP', ?, ?, ?)
                """, transaction, owner, amount, UUID.randomUUID(), Timestamp.from(now.plusSeconds(createdOffset)));
        jdbc.update("INSERT INTO wallet_transaction_lines(transaction_id, lot_id, wallet_user_id, amount) VALUES (?, ?, ?, ?)",
                transaction, lot, owner, amount);
        if (createdOffset <= 0 && expiryOffset > 0) {
            String column = type.equals("PAID") ? "paid_balance" : "bonus_balance";
            jdbc.update("UPDATE wallets SET " + column + " = " + column + " + ? WHERE user_id = ?", amount, owner);
        }
        return lot;
    }

    private UUID quote() { return catalog.issueQuote(new CatalogUseCase.IssueQuote(owner, code, "a".repeat(64))).id(); }
    private IdempotencyKey key(String value) { return new IdempotencyKey(value); }
    private int remaining(UUID lot) { return jdbc.queryForObject("SELECT remaining_amount FROM wallet_lots WHERE id = ?", Integer.class, lot); }
    private int count(String type) { return jdbc.queryForObject("SELECT COUNT(*) FROM wallet_transactions WHERE wallet_user_id = ? AND type = ?", Integer.class, owner, type); }
    private WalletBalance projection() {
        return jdbc.queryForObject("SELECT paid_balance, bonus_balance FROM wallets WHERE user_id = ?",
                (rs, row) -> new WalletBalance(rs.getInt(1), rs.getInt(2)), owner);
    }
    private void expireQuote(UUID quote) {
        Instant expiry = Instant.now().minusSeconds(1);
        jdbc.update("UPDATE purchase_quotes SET created_at = ?, expires_at = ? WHERE id = ?",
                Timestamp.from(expiry.minusSeconds(1800)), Timestamp.from(expiry), quote);
    }
    private void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, ErrorCode code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.errorCode()).isEqualTo(code));
    }
}
