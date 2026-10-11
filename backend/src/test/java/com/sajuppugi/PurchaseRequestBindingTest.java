package com.sajuppugi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.common.idempotency.PurchaseRequestBindingService;
import java.util.Map;
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
class PurchaseRequestBindingTest {
    @Autowired PurchaseRequestBindingService service;
    @Autowired com.sajuppugi.common.idempotency.JdbcPurchaseRequestBindings bindings;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    private final UUID actor = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM purchase_request_bindings WHERE actor_id IN (?, ?)", actor, other);
    }

    @Test
    void canonicalJsonOrderReplaysWithoutRawKeyOrBody() throws Exception {
        var first = mapper.readTree("{\"quoteId\":\"synthetic-quote\",\"nested\":{\"b\":2,\"a\":1}}");
        var reordered = mapper.readTree("{\"nested\":{\"a\":1,\"b\":2},\"quoteId\":\"synthetic-quote\"}");
        service.bind(actor, "private-client-key", "purchase", first);
        service.bind(actor, "private-client-key", "purchase", reordered);
        var rows = jdbc.queryForList("SELECT key_hash, request_hash FROM purchase_request_bindings WHERE actor_id = ?", actor);
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().get("key_hash").toString()).matches("[a-f0-9]{64}");
        assertThat(rows.getFirst().get("request_hash").toString()).matches("[a-f0-9]{64}");
        assertThat(rows.toString()).doesNotContain("private-client-key", "synthetic-quote");
    }

    @Test
    void differentBodyOrOperationCannotReuseKeyButOtherUserCan() {
        service.bind(actor, "key", "purchase", Map.of("quoteId", "one"));
        assertMismatch(() -> service.bind(actor, "key", "purchase", Map.of("quoteId", "two")));
        assertMismatch(() -> service.bind(actor, "key", "other-route", Map.of("quoteId", "one")));
        service.bind(other, "key", "purchase", Map.of("quoteId", "two"));
    }

    @Test
    void rejectsInvalidKeysBeforeWriting() {
        for (String key : new String[] {null, "", " ", "x".repeat(513)}) {
            assertThatThrownBy(() -> service.bind(actor, key, "purchase", Map.of()))
                    .isInstanceOf(ApiException.class);
        }
        assertThat(count()).isZero();
    }

    @Test
    void committedBindingSurvivesNewServiceInstance() {
        service.bind(actor, "key", "purchase", Map.of("quoteId", "one"));
        var restarted = new PurchaseRequestBindingService(
                bindings, mapper, java.time.Clock.systemUTC());
        assertMismatch(() -> restarted.bind(actor, "key", "purchase", Map.of("quoteId", "two")));
        assertThat(count()).isEqualTo(1);
    }

    @Test
    void simultaneousIdenticalRequestsBothMatchOneCommittedBinding() throws Exception {
        assertThat(race("one", "one")).containsExactlyInAnyOrder("accepted", "accepted");
        assertThat(count()).isEqualTo(1);
    }

    @Test
    void simultaneousDifferentRequestsAcceptOnlyOne() throws Exception {
        assertThat(race("one", "two")).containsExactlyInAnyOrder("accepted", "IDEMPOTENCY_KEY_REUSED");
        assertThat(count()).isEqualTo(1);
    }

    private java.util.List<String> race(String first, String second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = java.util.stream.Stream.of(first, second).map(value -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
                try {
                    service.bind(actor, "race-key", "purchase", Map.of("quoteId", value));
                    return "accepted";
                } catch (ApiException error) {
                    return error.errorCode().name();
                }
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return java.util.List.of(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS));
        }
    }

    private int count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM purchase_request_bindings WHERE actor_id = ?", Integer.class, actor);
    }

    private void assertMismatch(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED));
    }
}
