package com.sajuppugi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.payment.application.TopUpConfirmationService;
import com.sajuppugi.payment.domain.TopUpOrder;
import com.sajuppugi.payment.infrastructure.JdbcTopUpOrders;
import com.sajuppugi.payment.infrastructure.JdbcTopUpConfirmations;
import java.time.Clock;
import java.time.Instant;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class TopUpConfirmationPersistenceTest {
    @Autowired TopUpConfirmationService confirmations;
    @Autowired JdbcTopUpOrders orders;
    @Autowired JdbcTopUpConfirmations repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    UUID user;
    UUID order;

    @BeforeEach
    void setup() {
        user = UUID.randomUUID();
        order = createOrder();
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM top_up_confirmation_requests WHERE user_id = ?", user);
        jdbc.update("DELETE FROM top_up_confirmation_intents WHERE order_id IN (SELECT id FROM top_up_orders WHERE user_id = ?)", user);
        jdbc.update("DELETE FROM top_up_orders WHERE user_id = ?", user);
    }

    @Test
    void persistsImmutableIdentityAndReplaysWithoutApprovingOrGranting() {
        var first = confirmations.prepare(user, order, "private-payment", 5000, "private-request");
        var second = confirmations.prepare(user, order, "private-payment", 5000, "private-request");
        assertThat(second).isEqualTo(first);
        assertThat(new TopUpConfirmationService(repository, Clock.systemUTC())
                .prepare(user, order, "private-payment", 5000, "private-request")).isEqualTo(first);
        assertThat(first.paymentKeyHash()).matches("[a-f0-9]{64}").isNotEqualTo("private-payment");
        assertThat(first.toString()).doesNotContain(first.paymentKeyHash(), "private-payment");
        assertThat(countIntents()).isEqualTo(1);
        assertThat(countRequests()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT key_hash FROM top_up_confirmation_requests WHERE order_id = ?",
                String.class, order)).matches("[a-f0-9]{64}").isNotEqualTo("private-request");
        assertThat(orders.findOwned(user, order).orElseThrow().status()).isEqualTo(TopUpOrder.Status.PAYMENT_PENDING);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wallets WHERE user_id = ?", Integer.class, user)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wallet_transactions WHERE wallet_user_id = ?", Integer.class, user)).isZero();
    }

    @Test
    void newRequestKeyForSamePaymentReturnsOriginalIntent() {
        var first = confirmations.prepare(user, order, "payment", 5000, "first");
        assertThat(confirmations.prepare(user, order, "payment", 5000, "second")).isEqualTo(first);
        assertThat(countIntents()).isEqualTo(1);
        assertThat(countRequests()).isEqualTo(2);
    }

    @Test
    void rejectsWrongAmountAndDoesNotBindAnything() {
        assertError(() -> confirmations.prepare(user, order, "payment", 1, "key"), ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        assertThat(countIntents()).isZero();
        assertThat(countRequests()).isZero();
    }

    @Test
    void hidesAnotherUsersOrderLikeMissingOrder() {
        assertError(() -> confirmations.prepare(UUID.randomUUID(), order, "payment", 5000, "key"), ErrorCode.RESOURCE_NOT_FOUND);
        assertError(() -> confirmations.prepare(user, UUID.randomUUID(), "payment", 5000, "key"), ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(countIntents()).isZero();
    }

    @Test
    void rejectsChangedPaymentEvenWithANewRequestKey() {
        confirmations.prepare(user, order, "first", 5000, "key");
        assertError(() -> confirmations.prepare(user, order, "other", 5000, "key"), ErrorCode.IDEMPOTENCY_KEY_REUSED);
        assertError(() -> confirmations.prepare(user, order, "other", 5000, "new"), ErrorCode.PAYMENT_CONFIRMATION_CONFLICT);
        assertThat(countIntents()).isEqualTo(1);
        assertThat(countRequests()).isEqualTo(1);
    }

    @Test
    void rejectsReusingRequestOrPaymentAcrossOrders() {
        UUID other = createOrder();
        confirmations.prepare(user, order, "payment", 5000, "key");
        assertError(() -> confirmations.prepare(user, other, "other-payment", 5000, "key"), ErrorCode.IDEMPOTENCY_KEY_REUSED);
        assertError(() -> confirmations.prepare(user, other, "payment", 5000, "other-key"), ErrorCode.PAYMENT_CONFIRMATION_CONFLICT);
        assertThat(countIntents()).isEqualTo(1);
        assertThat(countRequests()).isEqualTo(1);
    }

    @Test
    void rejectsTerminalOrdersButKeepsExistingIdentityForLaterReconciliation() {
        for (String state : java.util.List.of("CREATED", "PAID", "CREDITED", "FAILED", "CANCELED", "REFUNDED")) {
            jdbc.update("UPDATE top_up_orders SET status = ? WHERE id = ?", state, order);
            assertError(() -> confirmations.prepare(user, order, "payment", 5000, "key"), ErrorCode.PAYMENT_ORDER_NOT_CONFIRMABLE);
        }
        jdbc.update("UPDATE top_up_orders SET status = 'PAYMENT_PENDING' WHERE id = ?", order);
        var intent = confirmations.prepare(user, order, "payment", 5000, "key");
        jdbc.update("UPDATE top_up_orders SET status = 'CANCELED' WHERE id = ?", order);
        assertThat(confirmations.prepare(user, order, "payment", 5000, "key")).isEqualTo(intent);
        assertThat(orders.findOwned(user, order).orElseThrow().status()).isEqualTo(TopUpOrder.Status.CANCELED);
    }

    @Test
    void rejectsInvalidInputsBeforeWriting() {
        for (String invalid : java.util.Arrays.asList(null, "", " ", "x".repeat(513))) {
            assertError(() -> confirmations.prepare(user, order, invalid, 5000, "key"), ErrorCode.INVALID_REQUEST);
            assertError(() -> confirmations.prepare(user, order, "payment", 5000, invalid), ErrorCode.INVALID_REQUEST);
        }
        assertError(() -> confirmations.prepare(null, order, "payment", 5000, "key"), ErrorCode.INVALID_REQUEST);
        assertError(() -> confirmations.prepare(user, null, "payment", 5000, "key"), ErrorCode.INVALID_REQUEST);
        assertError(() -> confirmations.prepare(user, order, "payment", 0, "key"), ErrorCode.INVALID_REQUEST);
        assertThat(countIntents()).isZero();
    }

    @Test
    void concurrentSameOrderCreatesOneIntent() throws Exception {
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> { start.await(); return confirmations.prepare(user, order, "payment", 5000, "key"); });
            var b = pool.submit(() -> { start.await(); return confirmations.prepare(user, order, "payment", 5000, "key"); });
            start.countDown();
            assertThat(a.get(10, TimeUnit.SECONDS)).isEqualTo(b.get(10, TimeUnit.SECONDS));
        }
        assertThat(countIntents()).isEqualTo(1);
        assertThat(countRequests()).isEqualTo(1);
    }

    @Test
    void concurrentCrossOrderRequestConflictRollsBackLosingIntent() throws Exception {
        UUID other = createOrder();
        var results = race(order, "first-payment", "key", other, "second-payment", "key");
        assertThat(results).containsExactlyInAnyOrder("OK", "IDEMPOTENCY_KEY_REUSED");
        assertThat(countIntents()).isEqualTo(1);
        assertThat(countRequests()).isEqualTo(1);
    }

    @Test
    void concurrentCrossOrderPaymentConflictRollsBackLosingIntent() throws Exception {
        UUID other = createOrder();
        var results = race(order, "payment", "first", other, "payment", "second");
        assertThat(results).containsExactlyInAnyOrder("OK", "PAYMENT_CONFIRMATION_CONFLICT");
        assertThat(countIntents()).isEqualTo(1);
        assertThat(countRequests()).isEqualTo(1);
    }

    @Test
    void concurrentDifferentPaymentsForOneOrderBindOnlyOneIdentity() throws Exception {
        assertThat(race(order, "first-payment", "first", order, "second-payment", "second"))
                .containsExactlyInAnyOrder("OK", "PAYMENT_CONFIRMATION_CONFLICT");
        assertThat(countIntents()).isEqualTo(1);
        assertThat(countRequests()).isEqualTo(1);
    }

    @Test
    void failureAfterIntentInsertRollsBackBothRecordsAndAllowsRetry() {
        var failingJdbc = new JdbcTemplate(jdbc.getDataSource()) {
            @Override
            public int update(String sql, Object... args) {
                if (sql.contains("INSERT INTO top_up_confirmation_requests")) {
                    throw new IllegalStateException("Injected request persistence failure");
                }
                return super.update(sql, args);
            }
        };
        var persistence = new JdbcTopUpConfirmations(failingJdbc);
        var transaction = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> transaction.execute(status -> persistence.bind(user, order,
                "a".repeat(64), 5000, "b".repeat(64), Instant.now())))
                .isInstanceOf(IllegalStateException.class);
        assertThat(countIntents()).isZero();
        assertThat(countRequests()).isZero();
        confirmations.prepare(user, order, "payment", 5000, "key");
        assertThat(countIntents()).isEqualTo(1);
        assertThat(countRequests()).isEqualTo(1);
    }

    private java.util.List<String> race(UUID a, String paymentA, String keyA, UUID b, String paymentB, String keyB) throws Exception {
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { start.await(); return outcome(a, paymentA, keyA); });
            var second = pool.submit(() -> { start.await(); return outcome(b, paymentB, keyB); });
            start.countDown();
            return java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }
    }

    private String outcome(UUID id, String payment, String key) {
        try { confirmations.prepare(user, id, payment, 5000, key); return "OK"; }
        catch (ApiException exception) { return exception.errorCode().name(); }
    }

    private UUID createOrder() {
        UUID id = UUID.randomUUID();
        orders.insert(new TopUpOrder(id, user, "TEST_TOPUP", 5000, 50, 6,
                TopUpOrder.Status.PAYMENT_PENDING, Instant.now()), id.toString().replace("-", "") + "0".repeat(32));
        return id;
    }

    private int countIntents() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM top_up_confirmation_intents WHERE order_id IN (SELECT id FROM top_up_orders WHERE user_id = ?)", Integer.class, user);
    }

    private int countRequests() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM top_up_confirmation_requests WHERE user_id = ?", Integer.class, user);
    }

    private void assertError(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.errorCode()).isEqualTo(code));
    }
}
