package com.sajuppugi;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.sajuppugi.operations.*;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest
@ActiveProfiles("test")
class OperationsAlertsTest {
    @Autowired JdbcOperationsAlerts alerts;
    @Autowired OperationsAlertService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.zaxxer.hikari.HikariDataSource dataSource;
    @Autowired Environment environment;
    @MockitoBean Clock clock;
    @MockitoBean OperationsAlertSink sink;
    static final Instant NOW = Instant.parse("2026-10-12T00:00:00Z");
    List<UUID> users;
    List<UUID> resources;
    List<UUID> directBatches;
    @BeforeEach void setup() {
        users = new ArrayList<>(); resources = new ArrayList<>(); directBatches = new ArrayList<>(); when(clock.instant()).thenReturn(NOW);
        when(sink.send(any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse(); return true;
        });
    }
    @AfterEach void cleanup() {
        for (UUID batch : directBatches) {
            jdbc.update("DELETE FROM operations_alert_deliveries WHERE incident_id IN (SELECT id FROM operations_incidents WHERE resource_id = ?)", batch);
            jdbc.update("DELETE FROM operations_incidents WHERE resource_id = ?", batch);
            jdbc.update("DELETE FROM direct_payment_batches WHERE id = ?", batch);
        }
        for (UUID resource : resources) {
            jdbc.update("DELETE FROM operations_alert_deliveries WHERE incident_id IN (SELECT id FROM operations_incidents WHERE resource_id = ?)", resource);
            jdbc.update("DELETE FROM operations_incidents WHERE resource_id = ?", resource);
        }
        for (UUID user : users) {
            jdbc.update("DELETE FROM payment_reconciliation_jobs WHERE order_id IN (SELECT id FROM top_up_orders WHERE user_id = ?)", user);
            jdbc.update("DELETE FROM top_up_refunds WHERE user_id = ?", user);
            jdbc.update("DELETE FROM top_up_confirmation_intents WHERE order_id IN (SELECT id FROM top_up_orders WHERE user_id = ?)", user);
            jdbc.update("DELETE FROM top_up_orders WHERE user_id = ?", user);
            jdbc.update("DELETE FROM wallet_integrity_findings WHERE wallet_user_id = ?", user);
            jdbc.update("DELETE FROM wallets WHERE user_id = ?", user);
        }
    }
    @Test void repeatedAndConcurrentDetectionMakesOneIncidentAndOneDelivery() throws Exception {
        UUID user = problem();
        var p = new JdbcOperationsAlerts.Problem("WALLET_INTEGRITY", user);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = pool.submit(() -> alerts.observe(p, NOW)); var b = pool.submit(() -> alerts.observe(p, NOW));
            a.get(10, TimeUnit.SECONDS); b.get(10, TimeUnit.SECONDS);
        }
        alerts.observe(p, NOW);
        assertThat(count(user)).isEqualTo(1);
        UUID delivery = delivery(user);
        service.deliver(delivery); service.deliver(delivery);
        assertThat(state(delivery)).isEqualTo("DELIVERED");
        verify(sink, times(1)).send(any());
    }
    @Test void failureRetriesSameAlertIdAndStopsAfterFiveAttempts() {
        UUID user = problem(); alerts.observe(new JdbcOperationsAlerts.Problem("WALLET_INTEGRITY", user), NOW);
        UUID delivery = delivery(user);
        doReturn(false).when(sink).send(any());
        for (int i = 0; i < 5; i++) { when(clock.instant()).thenReturn(NOW.plusSeconds(31L * i)); service.deliver(delivery); }
        assertThat(state(delivery)).isEqualTo("REVIEW_REQUIRED");
        service.deliver(delivery);
        verify(sink, times(5)).send(argThat(a -> delivery.equals(a.alertId())));
        assertThat(jdbc.queryForObject("SELECT delivered_at FROM operations_alert_deliveries WHERE id = ?", Timestamp.class, delivery)).isNull();
    }
    @Test void restartedLeaseRejectsLateAcknowledgementWithoutChangingFinancialData() {
        UUID user = problem(); alerts.observe(new JdbcOperationsAlerts.Problem("WALLET_INTEGRITY", user), NOW);
        UUID delivery = delivery(user);
        var stale = alerts.claim(delivery, NOW).orElseThrow();
        var fresh = alerts.claim(delivery, NOW.plusSeconds(121)).orElseThrow();
        alerts.finish(stale, true, NOW.plusSeconds(122));
        assertThat(state(delivery)).isEqualTo("IN_FLIGHT");
        alerts.finish(fresh, true, NOW.plusSeconds(122));
        assertThat(state(delivery)).isEqualTo("DELIVERED");
        assertThat(jdbc.queryForObject("SELECT paid_balance FROM wallets WHERE user_id = ?", Integer.class, user)).isZero();
    }
    @Test void resolvedIncidentCancelsUnsentAlertAndReopeningCreatesNewGeneration() {
        UUID user = problem(); var p = new JdbcOperationsAlerts.Problem("WALLET_INTEGRITY", user);
        alerts.observe(p, NOW); UUID first = delivery(user);
        UUID incident = jdbc.queryForObject("SELECT id FROM operations_incidents WHERE resource_id = ?", UUID.class, user);
        jdbc.update("UPDATE wallet_integrity_findings SET resolved_at = ? WHERE wallet_user_id = ?", Timestamp.from(NOW), user);
        alerts.resolveIfHealthy(incident, NOW);
        service.deliver(first);
        assertThat(state(first)).isEqualTo("CANCELED"); verifyNoInteractions(sink);
        jdbc.update("UPDATE wallet_integrity_findings SET resolved_at = NULL WHERE wallet_user_id = ?", user);
        alerts.observe(p, NOW.plusSeconds(60));
        assertThat(count(user)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT generation FROM operations_incidents WHERE id = ?", Integer.class, incident)).isEqualTo(2);
    }
    @Test void boundedCandidateScanDoesNotStarveIncidentsBeyondFirstBatch() {
        for (int i = 0; i < 105; i++) problem();
        var first = alerts.candidates(NOW);
        first.forEach(p -> alerts.observe(p, NOW));
        var second = alerts.candidates(NOW);
        second.forEach(p -> alerts.observe(p, NOW));
        assertThat(first.size()).isEqualTo(100); assertThat(second.size()).isEqualTo(5);
        assertThat(alerts.candidates(NOW)).isEmpty();
    }
    @Test void healthyOrInvalidSourcesCannotCreateAlertWork() {
        UUID user = problem();
        jdbc.update("UPDATE wallet_integrity_findings SET resolved_at = ? WHERE wallet_user_id = ?", Timestamp.from(NOW), user);
        alerts.observe(new JdbcOperationsAlerts.Problem("WALLET_INTEGRITY", user), NOW);
        assertThat(count(user)).isZero();
        assertThatThrownBy(() -> alerts.observe(new JdbcOperationsAlerts.Problem("INVALID", user), NOW)).isInstanceOf(RuntimeException.class);
    }
    @Test void refundAndPaymentSourcesRespectAgeAndExcludeResolvedRejections() {
        UUID user = UUID.randomUUID(), order = UUID.randomUUID(), refund = UUID.randomUUID();
        users.add(user); resources.add(order); resources.add(refund);
        jdbc.update("INSERT INTO top_up_orders(id,user_id,key_hash,product_code,amount_krw,paid_shells,bonus_shells,status,created_at) VALUES (?,?,?,'TEST',1000,50,6,'CREDITED',?)", order, user, order.toString(), Timestamp.from(NOW));
        jdbc.update("INSERT INTO top_up_refunds(id,order_id,user_id,key_hash,state,amount_krw,reason_code,next_attempt_at,created_at) VALUES (?,?,?,?,'RESERVED',1000,'UNUSED_TOP_UP',?,?)", refund, order, user, order.toString(), Timestamp.from(NOW), Timestamp.from(NOW));
        assertThat(alerts.candidates(NOW)).isEmpty();
        assertThat(alerts.candidates(NOW.plusSeconds(901))).extracting(JdbcOperationsAlerts.Problem::kind).containsExactly("REFUND_STALLED");
        jdbc.update("UPDATE top_up_refunds SET state = 'REVIEW_REQUIRED', resolution = 'CANCEL_REJECTED' WHERE id = ?", refund);
        assertThat(alerts.candidates(NOW.plusSeconds(901))).isEmpty();
        jdbc.update("UPDATE top_up_refunds SET resolution = NULL WHERE id = ?", refund);
        jdbc.update("INSERT INTO top_up_confirmation_intents(order_id,payment_key_hash,amount_krw,created_at) VALUES (?,?,1000,?)", order, order.toString().replace("-", "").repeat(2), Timestamp.from(NOW));
        jdbc.update("INSERT INTO top_up_executions(order_id,state,next_attempt_at,updated_at) VALUES (?,'REVIEW_REQUIRED',?,?)", order, Timestamp.from(NOW), Timestamp.from(NOW));
        jdbc.update("INSERT INTO payment_reconciliation_jobs(order_id,state,next_attempt_at) VALUES (?,'REVIEW_REQUIRED',?)", order, Timestamp.from(NOW));
        assertThat(alerts.candidates(NOW)).extracting(JdbcOperationsAlerts.Problem::kind)
                .containsExactlyInAnyOrder("REFUND_REVIEW", "TOP_UP_REVIEW", "PAYMENT_RECONCILIATION");
    }
    @Test void directPaymentSourcesRaiseAndResolveReviewAndStalledAlerts() {
        UUID buyer = UUID.randomUUID(), review = directBatch(buyer, "REVIEW_REQUIRED", NOW);
        UUID stalled = directBatch(buyer, "PAYMENT_PENDING", NOW.minusSeconds(901));
        jdbc.update("INSERT INTO direct_payment_confirmation_intents(batch_id,payment_key_hash,amount_krw,created_at) VALUES (?,?,3000,?)",
                stalled, "a".repeat(64), Timestamp.from(NOW.minusSeconds(901)));
        jdbc.update("INSERT INTO direct_payment_executions(batch_id,state,next_attempt_at,updated_at) VALUES (?,'RETRY',?,?)",
                stalled, Timestamp.from(NOW), Timestamp.from(NOW));

        assertThat(alerts.candidates(NOW)).extracting(JdbcOperationsAlerts.Problem::kind)
                .containsExactlyInAnyOrder("DIRECT_PAYMENT_REVIEW", "DIRECT_PAYMENT_STALLED");
        alerts.observe(new JdbcOperationsAlerts.Problem("DIRECT_PAYMENT_REVIEW", review), NOW);
        UUID incident = jdbc.queryForObject("SELECT id FROM operations_incidents WHERE kind = ? AND resource_id = ?",
                UUID.class, "DIRECT_PAYMENT_REVIEW", review);
        jdbc.update("UPDATE direct_payment_batches SET status = 'FAILED' WHERE id = ?", review);
        alerts.resolveIfHealthy(incident, NOW.plusSeconds(1));
        assertThat(jdbc.queryForObject("SELECT state FROM operations_incidents WHERE id = ?", String.class, incident)).isEqualTo("RESOLVED");
    }
    @Test void sharedServerConfigurationUsesBoundedDatabaseAndHttpQueues() {
        assertThat(dataSource.getMaximumPoolSize()).isEqualTo(6);
        assertThat(dataSource.getMinimumIdle()).isEqualTo(1);
        assertThat(dataSource.getConnectionTimeout()).isEqualTo(3000);
        assertThat(dataSource.getValidationTimeout()).isEqualTo(1000);
        assertThat(environment.getProperty("server.tomcat.threads.max", Integer.class)).isEqualTo(100);
        assertThat(environment.getProperty("server.tomcat.max-connections", Integer.class)).isEqualTo(200);
        assertThat(environment.getProperty("server.tomcat.accept-count", Integer.class)).isEqualTo(50);
    }
    private UUID problem() {
        UUID user = UUID.randomUUID(); users.add(user); resources.add(user);
        jdbc.update("INSERT INTO wallets(user_id, updated_at) VALUES (?, ?)", user, Timestamp.from(NOW));
        jdbc.update("INSERT INTO wallet_integrity_findings(wallet_user_id, kind, detected_at) VALUES (?, 'BALANCE_MISMATCH', ?)", user, Timestamp.from(NOW));
        return user;
    }
    private UUID directBatch(UUID buyer, String status, Instant createdAt) {
        UUID batch = UUID.randomUUID(); directBatches.add(batch);
        jdbc.update("""
                INSERT INTO direct_payment_batches
                    (id,buyer_user_id,quote_id,product_code,product_name,unit_amount_krw,quantity,total_amount_krw,key_hash,status,created_at)
                VALUES (?,?,NULL,'TEST_DIRECT','테스트 직접결제',3000,1,3000,?,?,?)
                """, batch, buyer, batch.toString().replace("-", "").repeat(2), status, Timestamp.from(createdAt));
        return batch;
    }
    private int count(UUID user) { return jdbc.queryForObject("SELECT COUNT(*) FROM operations_alert_deliveries d JOIN operations_incidents i ON i.id = d.incident_id WHERE i.resource_id = ?", Integer.class, user); }
    private UUID delivery(UUID user) { return jdbc.queryForObject("SELECT d.id FROM operations_alert_deliveries d JOIN operations_incidents i ON i.id = d.incident_id WHERE i.resource_id = ?", UUID.class, user); }
    private String state(UUID id) { return jdbc.queryForObject("SELECT state FROM operations_alert_deliveries WHERE id = ?", String.class, id); }
}
