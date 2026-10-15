package com.sajuppugi.gift.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.gift.application.DirectGiftBatchService.Option;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DirectGiftBatchServiceTest {
    @Autowired DirectGiftBatchService service;
    @Autowired JdbcTemplate jdbc;
    private UUID buyer;

    @AfterEach
    void cleanUp() {
        if (buyer == null) return;
        jdbc.update("DELETE FROM direct_gift_batches WHERE buyer_user_id = ?", buyer);
        jdbc.update("DELETE FROM direct_payment_batches WHERE buyer_user_id = ?", buyer);
    }

    @Test
    void replaysOnlyWhenTheIdempotencyKeyAndRecipientOptionsBothMatch() {
        buyer = UUID.randomUUID();
        List<Option> options = List.of(Option.TALISMAN_ONLY, Option.READING_WITH_TALISMAN);

        var created = service.create(buyer, options, "same-key");
        var replayed = service.create(buyer, options, "same-key");

        assertThat(replayed.id()).isEqualTo(created.id());
        assertThatThrownBy(() -> service.create(buyer, List.of(Option.TALISMAN_ONLY), "same-key"))
                .isInstanceOfSatisfying(ApiException.class,
                        failure -> assertThat(failure.errorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED));
    }

    @Test
    void concurrentSameRequestConvergesOnOneBatch() throws Exception {
        buyer = UUID.randomUUID();
        List<Option> options = List.of(Option.READING_WITH_TALISMAN);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { start.await(); return service.create(buyer, options, "concurrent-key"); });
            var second = pool.submit(() -> { start.await(); return service.create(buyer, options, "concurrent-key"); });
            start.countDown();

            assertThat(first.get(10, TimeUnit.SECONDS).id()).isEqualTo(second.get(10, TimeUnit.SECONDS).id());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM direct_payment_batches WHERE buyer_user_id = ?",
                Integer.class, buyer)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM direct_gift_batch_recipients r
                JOIN direct_gift_batches b ON b.payment_batch_id = r.payment_batch_id
                WHERE b.buyer_user_id = ?
                """, Integer.class, buyer)).isEqualTo(1);
    }
}
