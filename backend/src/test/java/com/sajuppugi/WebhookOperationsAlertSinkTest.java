package com.sajuppugi;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajuppugi.operations.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestClient;

class WebhookOperationsAlertSinkTest {
    HttpServer server;
    AtomicReference<String> body = new AtomicReference<>();
    AtomicReference<String> key = new AtomicReference<>();
    volatile int response = 204;
    @BeforeEach void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/alerts", e -> {
            body.set(new String(e.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            key.set(e.getRequestHeaders().getFirst("Idempotency-Key"));
            e.sendResponseHeaders(response, -1); e.close();
        }); server.start();
    }
    @AfterEach void cleanup() { server.stop(0); }
    @Test void sendsMinimalPayloadWithStableIdentityAndDoesNotFollowRedirectOrAssumeFailureIsSuccess() throws Exception {
        var sink = sink(url(), "test");
        var alert = new OperationsAlertSink.Alert(UUID.randomUUID(), "REFUND_REVIEW", UUID.randomUUID(), 1, Instant.parse("2026-10-12T00:00:00Z"));
        assertThat(sink.send(alert)).isTrue();
        assertThat(key.get()).isEqualTo(alert.alertId().toString());
        var json = new ObjectMapper().readTree(body.get());
        assertThat(json.size()).isEqualTo(5);
        assertThat(body.get()).doesNotContain("synthetic-token", "paymentKey", "amount", "nickname");
        response = 500; assertThat(sink.send(alert)).isFalse();
        response = 302; assertThat(sink.send(alert)).isFalse();
    }
    @Test void rejectsUnsafeUrlAndLoopbackOutsideTestWithoutLeakingToken() {
        for (String url : java.util.List.of("http://example.com/alerts", "https://user:private@example.com/alerts", "https://example.com/alerts?secret=private"))
            assertThatThrownBy(() -> sink(url, "test")).hasMessage("Operations alert webhook configuration is invalid");
        assertThatThrownBy(() -> sink(url(), "local")).hasMessage("Operations alert webhook configuration is invalid");
        assertThat(new OperationsAlertProperties(url(), "synthetic-token", Duration.ofMinutes(15)).toString()).doesNotContain("synthetic-token", url());
    }
    private WebhookOperationsAlertSink sink(String url, String profile) {
        var environment = new MockEnvironment(); environment.setActiveProfiles(profile);
        return new WebhookOperationsAlertSink(RestClient.builder(), new OperationsAlertProperties(url, "synthetic-token", Duration.ofMinutes(15)), environment);
    }
    private String url() { return "http://127.0.0.1:" + server.getAddress().getPort() + "/alerts"; }
}
