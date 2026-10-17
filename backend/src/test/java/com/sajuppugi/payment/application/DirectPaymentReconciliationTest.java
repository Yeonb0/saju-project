package com.sajuppugi.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.sajuppugi.payment.port.PaymentProviderPort;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class DirectPaymentReconciliationTest {
    @Autowired DirectPaymentConfirmationService confirmations;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @MockitoBean PaymentProviderPort provider;

    private UUID batchId;

    @AfterEach
    void cleanUp() {
        if (batchId != null) jdbc.update("DELETE FROM direct_payment_batches WHERE id = ?", batchId);
    }

    @Test
    void findsDueRetryAndSettlesItOnlyFromProviderLookup() {
        batchId = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();
        String paymentHash = "a".repeat(64);
        Instant now = clock.instant();
        jdbc.update("""
                INSERT INTO direct_payment_batches
                    (id,buyer_user_id,quote_id,product_code,product_name,unit_amount_krw,quantity,
                     total_amount_krw,key_hash,status,created_at)
                VALUES (?,?,NULL,'GIFT_SUNEUNG_BATCH','수능 선물',3000,1,3000,?,'PAYMENT_PENDING',?)
                """, batchId, buyer, "b".repeat(64), Timestamp.from(now.minusSeconds(60)));
        jdbc.update("""
                INSERT INTO direct_payment_confirmation_intents(batch_id,payment_key_hash,amount_krw,created_at)
                VALUES (?,?,3000,?)
                """, batchId, paymentHash, Timestamp.from(now.minusSeconds(60)));
        jdbc.update("""
                INSERT INTO direct_payment_executions
                    (batch_id,state,attempts,confirmation_started,next_attempt_at,updated_at)
                VALUES (?,'RETRY',1,TRUE,?,?)
                """, batchId, Timestamp.from(now.minusSeconds(1)), Timestamp.from(now.minusSeconds(10)));
        when(provider.lookup(batchId)).thenReturn(new PaymentProviderPort.Result(
                PaymentProviderPort.Outcome.APPROVED, batchId, paymentHash, 3000, "KRW", null));

        assertThat(confirmations.dueForReconciliation(20)).contains(batchId);
        confirmations.reconcile(batchId);

        assertThat(jdbc.queryForObject("SELECT status FROM direct_payment_batches WHERE id = ?", String.class, batchId))
                .isEqualTo("PAID");
        assertThat(jdbc.queryForObject("SELECT state FROM direct_payment_executions WHERE batch_id = ?", String.class, batchId))
                .isEqualTo("COMPLETED");
    }
}
