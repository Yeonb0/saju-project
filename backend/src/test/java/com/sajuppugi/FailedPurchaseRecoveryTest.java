package com.sajuppugi;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doReturn;

import com.sajuppugi.catalog.application.CatalogUseCase;
import com.sajuppugi.common.idempotency.IdempotencyKey;
import com.sajuppugi.fortune.reading.application.FailedPurchaseCompensationService;
import com.sajuppugi.fortune.reading.application.FailedPurchaseCompensationService.Outcome;
import com.sajuppugi.fortune.reading.application.SuneungPurchaseClaimService;
import com.sajuppugi.fortune.reading.application.FailedPurchaseRecoveryScheduler;
import com.sajuppugi.fortune.reading.application.WalletRecoveryProperties;
import com.sajuppugi.fortune.reading.port.ReadingRecoveryRepository;
import com.sajuppugi.fortune.reading.port.ReadingRepository;
import com.sajuppugi.wallet.application.WalletPurchasePort;
import com.sajuppugi.wallet.application.WalletPurchaseRecoveryPort;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
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
import org.springframework.transaction.UnexpectedRollbackException;

@SpringBootTest(properties = {"app.wallet.purchases-enabled=true", "app.wallet.recovery.enabled=false",
        "app.wallet.recovery.max-attempts=3"})
@ActiveProfiles("test")
class FailedPurchaseRecoveryTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired CatalogUseCase catalog;
    @Autowired WalletPurchasePort wallet;
    @Autowired SuneungPurchaseClaimService claims;
    @Autowired FailedPurchaseCompensationService recovery;
    @Autowired WalletRecoveryProperties properties;
    @Autowired Clock clock;
    @Autowired ReadingRepository originalReadings;
    @MockitoSpyBean ReadingRecoveryRepository readings;
    @MockitoSpyBean WalletPurchaseRecoveryPort recoveryWallet;
    private UUID owner;
    private UUID product;
    private UUID quote;
    private UUID purchase;
    private UUID debit;

    @BeforeEach
    void setUp() {
        owner = UUID.randomUUID();
        product = UUID.randomUUID();
        String code = "RECOVERY_TEST_" + product;
        Instant now = Instant.now();
        jdbc.update("INSERT INTO wallets(user_id, paid_balance, updated_at) VALUES (?, 20, ?)", owner, Timestamp.from(now));
        UUID lot = UUID.randomUUID();
        UUID opening = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO wallet_lots(id, wallet_user_id, balance_type, granted_amount, remaining_amount,
                created_at, expires_at) VALUES (?, ?, 'PAID', 20, 20, ?, ?)
                """, lot, owner, Timestamp.from(now.minusSeconds(10)), Timestamp.from(now.plusSeconds(3600)));
        jdbc.update("""
                INSERT INTO wallet_transactions(id, wallet_user_id, type, total_amount, reference_id, created_at)
                VALUES (?, ?, 'TOP_UP', 20, ?, ?)
                """, opening, owner, UUID.randomUUID(), Timestamp.from(now.minusSeconds(10)));
        jdbc.update("INSERT INTO wallet_transaction_lines(transaction_id, lot_id, wallet_user_id, amount) VALUES (?, ?, ?, 20)",
                opening, lot, owner);
        jdbc.update("""
                INSERT INTO products(id, code, category, price_currency, price_amount, active, catalog_version)
                VALUES (?, ?, 'READING', 'TURTLE_SHELL', 15, TRUE, 'recovery-test')
                """, product, code);
        quote = catalog.issueQuote(new CatalogUseCase.IssueQuote(owner, code, "a".repeat(64))).id();
        purchase = claims.claim(owner, quote, product, UUID.randomUUID()).id();
        debit = wallet.debit(owner, quote, new IdempotencyKey("test-purchase:" + purchase)).transactionId();
        jdbc.update("UPDATE reading_purchases SET status = 'FAILED', wallet_transaction_id = ? WHERE id = ?", debit, purchase);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM reading_purchases WHERE buyer_user_id = ?", owner);
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
    void restoresFailedPurchaseAndChangesDurableStateToRefunded() {
        assertThat(recovery.recover(purchase)).isEqualTo(Outcome.REFUNDED);
        assertThat(status()).isEqualTo("REFUNDED");
        assertThat(balance()).isEqualTo(20);
        assertThat(refunds()).isEqualTo(1);
        assertThat(recovery.recover(purchase)).isEqualTo(Outcome.SKIPPED);
        originalReadings.markRefunded(purchase);
        assertThat(status()).isEqualTo("REFUNDED");
        assertThat(refunds()).isEqualTo(1);
    }

    @Test
    void usesExistingReversalWhenWalletCommitSucceededBeforePurchaseStateCommit() {
        wallet.compensate(debit, "READING_GENERATION_FAILED", new IdempotencyKey("inline-refund"));
        assertThat(status()).isEqualTo("FAILED");
        assertThat(recovery.recover(purchase)).isEqualTo(Outcome.REFUNDED);
        assertThat(refunds()).isEqualTo(1);
        assertThat(balance()).isEqualTo(20);
    }

    @Test
    void stateWriteFailureLeavesCommittedWalletReversalSafeForNextRun() {
        doThrow(new IllegalStateException("test failure after wallet commit")).doCallRealMethod()
                .when(AopTestUtils.<ReadingRecoveryRepository>getUltimateTargetObject(readings)).markRefunded(purchase);
        assertThatThrownBy(() -> recovery.recover(purchase)).isInstanceOf(UnexpectedRollbackException.class);
        assertThat(status()).isEqualTo("FAILED");
        assertThat(balance()).isEqualTo(20);
        assertThat(refunds()).isEqualTo(1);
        assertThat(recovery.recover(purchase)).isEqualTo(Outcome.REFUNDED);
        assertThat(refunds()).isEqualTo(1);
    }

    @Test
    void compensationFailurePersistsBoundedRetryAndDoesNotClaimRefundCompletion() {
        failWalletOnce();
        assertThat(recovery.recover(purchase)).isEqualTo(Outcome.RETRY_SCHEDULED);
        assertThat(status()).isEqualTo("FAILED");
        assertThat(balance()).isEqualTo(5);
        assertThat(refunds()).isZero();
        assertThat(attempts()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT recovery_next_attempt_at FROM reading_purchases WHERE id = ?",
                Timestamp.class, purchase).toInstant()).isAfter(Instant.now());
        assertThat(recovery.recover(purchase)).isEqualTo(Outcome.SKIPPED);
        readyAgain();
        assertThat(recovery.recover(purchase)).isEqualTo(Outcome.REFUNDED);
        assertThat(balance()).isEqualTo(20);
    }

    @Test
    void exhaustedJobsRemainFailedWithErrorCodeAndStopAutomaticRetries() {
        doThrow(new IllegalStateException("test provider unavailable"))
                .when(AopTestUtils.<WalletPurchaseRecoveryPort>getUltimateTargetObject(recoveryWallet))
                .compensatePurchase(any(), any(), any(), anyString(), any());
        assertThat(recovery.recover(purchase)).isEqualTo(Outcome.RETRY_SCHEDULED);
        readyAgain();
        assertThat(recovery.recover(purchase)).isEqualTo(Outcome.RETRY_SCHEDULED);
        readyAgain();
        assertThat(recovery.recover(purchase)).isEqualTo(Outcome.EXHAUSTED);
        assertThat(attempts()).isEqualTo(3);
        assertThat(status()).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT recovery_last_error_code FROM reading_purchases WHERE id = ?",
                String.class, purchase)).isEqualTo("WALLET_COMPENSATION_FAILED");
        assertThat(recovery.recover(purchase)).isEqualTo(Outcome.SKIPPED);
        assertThat(refunds()).isZero();
    }

    @Test
    void staleCandidatesCannotCompensateGeneratingOrFulfilledPurchases() {
        for (String state : List.of("CREATED", "DEBITED", "GENERATING", "FULFILLED", "REFUNDED")) {
            jdbc.update("UPDATE reading_purchases SET status = ? WHERE id = ?", state, purchase);
            assertThat(recovery.recover(purchase)).isEqualTo(Outcome.SKIPPED);
        }
        assertThat(refunds()).isZero();
    }

    @Test
    void invalidPurchaseOwnerOrQuoteLinkDoesNotRestoreAnotherDebit() {
        jdbc.update("UPDATE reading_purchases SET buyer_user_id = ? WHERE id = ?", UUID.randomUUID(), purchase);
        try {
            assertThat(recovery.recover(purchase)).isEqualTo(Outcome.RETRY_SCHEDULED);
            assertThat(refunds()).isZero();
            assertThat(jdbc.queryForObject("SELECT recovery_last_error_code FROM reading_purchases WHERE id = ?",
                    String.class, purchase)).isEqualTo("RESOURCE_NOT_FOUND");
        } finally {
            jdbc.update("UPDATE reading_purchases SET buyer_user_id = ? WHERE id = ?", owner, purchase);
        }
        String code = jdbc.queryForObject("SELECT code FROM products WHERE id = ?", String.class, product);
        UUID anotherQuote = catalog.issueQuote(new CatalogUseCase.IssueQuote(owner, code, "b".repeat(64))).id();
        jdbc.update("UPDATE reading_purchases SET quote_id = ? WHERE id = ?", anotherQuote, purchase);
        readyAgain();
        assertThat(recovery.recover(purchase)).isEqualTo(Outcome.RETRY_SCHEDULED);
        assertThat(refunds()).isZero();
    }

    @Test
    void concurrentWorkersHaveOneReversalAndOneRefundedPurchase() throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = java.util.stream.IntStream.range(0, 2).mapToObj(index -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Worker start timed out");
                return recovery.recover(purchase);
            })).toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(Outcome.REFUNDED, Outcome.SKIPPED);
        }
        assertThat(status()).isEqualTo("REFUNDED");
        assertThat(refunds()).isEqualTo(1);
        assertThat(balance()).isEqualTo(20);
    }

    @Test
    void missingCompensationReceiptCannotBecomeRefunded() {
        doReturn(null).when(AopTestUtils.<WalletPurchaseRecoveryPort>getUltimateTargetObject(recoveryWallet))
                .compensatePurchase(any(), any(), any(), anyString(), any());
        assertThat(recovery.recover(purchase)).isEqualTo(Outcome.RETRY_SCHEDULED);
        assertThat(status()).isEqualTo("FAILED");
        assertThat(refunds()).isZero();
    }

    @Test
    void candidateQueryExcludesFutureExhaustedAndNonFailedJobs() {
        assertThat(readings.findReadyFailed(Instant.now(), 3, 100)).contains(purchase);
        jdbc.update("UPDATE reading_purchases SET recovery_next_attempt_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().plusSeconds(60)), purchase);
        assertThat(readings.findReadyFailed(Instant.now(), 3, 100)).doesNotContain(purchase);
        readyAgain();
        jdbc.update("UPDATE reading_purchases SET recovery_attempts = 3 WHERE id = ?", purchase);
        assertThat(readings.findReadyFailed(Instant.now(), 3, 100)).doesNotContain(purchase);
        jdbc.update("UPDATE reading_purchases SET recovery_attempts = 0, status = 'GENERATING' WHERE id = ?", purchase);
        assertThat(readings.findReadyFailed(Instant.now(), 3, 100)).doesNotContain(purchase);
    }

    @Test
    void schedulerPollDelegatesToTransactionalRecoveryService() {
        doReturn(List.of(purchase)).when(AopTestUtils.<ReadingRecoveryRepository>getUltimateTargetObject(readings))
                .findReadyFailed(any(), anyInt(), anyInt());
        new FailedPurchaseRecoveryScheduler(readings, recovery, properties, clock).poll();
        assertThat(status()).isEqualTo("REFUNDED");
        assertThat(refunds()).isEqualTo(1);
    }

    private void failWalletOnce() {
        doThrow(new IllegalStateException("test wallet unavailable")).doCallRealMethod()
                .when(AopTestUtils.<WalletPurchaseRecoveryPort>getUltimateTargetObject(recoveryWallet))
                .compensatePurchase(any(), any(), any(), anyString(), any());
    }
    private void readyAgain() {
        jdbc.update("UPDATE reading_purchases SET recovery_next_attempt_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minusSeconds(1)), purchase);
    }
    private String status() { return jdbc.queryForObject("SELECT status FROM reading_purchases WHERE id = ?", String.class, purchase); }
    private int attempts() { return jdbc.queryForObject("SELECT recovery_attempts FROM reading_purchases WHERE id = ?", Integer.class, purchase); }
    private int balance() { return jdbc.queryForObject("SELECT paid_balance FROM wallets WHERE user_id = ?", Integer.class, owner); }
    private int refunds() { return jdbc.queryForObject("SELECT COUNT(*) FROM wallet_transactions WHERE wallet_user_id = ? AND type = 'REFUND'", Integer.class, owner); }
}
