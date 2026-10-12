package com.sajuppugi.payment.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sajuppugi.payment.application.TopUpUseCase.Confirmation;
import com.sajuppugi.payment.port.PaymentProviderPort;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "app.toss.adapter", havingValue = "api")
public class TossPaymentProvider implements PaymentProviderPort {
    private static final int MAX_RESPONSE_BYTES = 262144;
    private final RestClient client;
    private final ObjectMapper mapper;

    public TossPaymentProvider(RestClient.Builder builder, ObjectMapper mapper, TossPaymentProperties properties,
            Environment environment) {
        validate(properties, environment);
        this.mapper = mapper;
        var factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String method) throws IOException {
                super.prepareConnection(connection, method);
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        String authorization = "Basic " + Base64.getEncoder().encodeToString(
                (properties.secretKey() + ":").getBytes(StandardCharsets.UTF_8));
        client = builder.baseUrl(properties.baseUrl()).requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, authorization)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).build();
    }

    @Override
    public Result confirm(UUID orderId, Confirmation confirmation) {
        Objects.requireNonNull(orderId);
        Objects.requireNonNull(confirmation);
        if (confirmation.paymentKey().length() > 200) throw new IllegalArgumentException("Invalid payment key");
        try {
            return client.post().uri("/v1/payments/confirm")
                    .header("Idempotency-Key", "top-up-confirm-" + orderId)
                    .body(Map.of("orderId", orderId.toString(), "paymentKey", confirmation.paymentKey(),
                            "amount", confirmation.amountKrw()))
                    .exchange((request, response) -> parse(orderId, confirmation, response));
        } catch (RuntimeException exception) {
            // Exception messages and causes can include provider bodies, URLs or credentials.
            return unknown(orderId, "TOSS_TRANSPORT_ERROR");
        }
    }

    @Override
    public Result lookup(UUID orderId) {
        Objects.requireNonNull(orderId);
        try {
            return client.get().uri("/v1/payments/orders/{id}", orderId)
                    .exchange((request, response) -> parse(orderId, null, response));
        } catch (RuntimeException exception) {
            return unknown(orderId, "TOSS_TRANSPORT_ERROR");
        }
    }

    @Override
    public Result cancel(UUID orderId, UUID refundId, int amountKrw, String reasonCode) {
        Objects.requireNonNull(orderId);
        Objects.requireNonNull(refundId);
        if (amountKrw <= 0 || !"UNUSED_TOP_UP".equals(reasonCode)) return unknown(orderId, "TOSS_CANCEL_INVALID");
        try {
            // Resolve the raw provider key transiently; only its hash is stored in our database.
            JsonNode payment = client.get().uri("/v1/payments/orders/{id}", orderId)
                    .exchange((request, response) -> readPayment(response));
            if (payment == null || !orderId.toString().equals(text(payment, "orderId"))
                    || !"KRW".equals(text(payment, "currency")) || !"NORMAL".equals(text(payment, "type"))
                    || !payment.path("totalAmount").isIntegralNumber() || !payment.path("totalAmount").canConvertToInt()
                    || payment.path("totalAmount").intValue() != amountKrw) return unknown(orderId, "TOSS_RESPONSE_MISMATCH");
            String key = text(payment, "paymentKey");
            if (key == null || !key.matches("[A-Za-z0-9_-]{1,200}")) return unknown(orderId, "TOSS_RESPONSE_MISMATCH");
            if ("CANCELED".equals(text(payment, "status")) && zeroBalance(payment))
                return new Result(Outcome.CANCELED, orderId, hash(key), amountKrw, "KRW", null);
            if (!"DONE".equals(text(payment, "status")) || !payment.path("balanceAmount").isIntegralNumber()
                    || payment.path("balanceAmount").longValue() != amountKrw) return unknown(orderId, "TOSS_CANCEL_REVIEW_REQUIRED");
            return client.post().uri("/v1/payments/{key}/cancel", key)
                    .header("Idempotency-Key", "top-up-refund-" + refundId)
                    .body(Map.of("cancelReason", "Unused top-up refund", "cancelAmount", amountKrw))
                    .exchange((request, response) -> {
                        JsonNode canceled = readPayment(response);
                        if (canceled == null || !orderId.toString().equals(text(canceled, "orderId"))
                                || !key.equals(text(canceled, "paymentKey")) || !"KRW".equals(text(canceled, "currency"))
                                || !"NORMAL".equals(text(canceled, "type")) || !"CANCELED".equals(text(canceled, "status"))
                                || !zeroBalance(canceled) || !canceled.path("totalAmount").isIntegralNumber()
                                || !canceled.path("totalAmount").canConvertToInt()
                                || canceled.path("totalAmount").longValue() != amountKrw) return unknown(orderId, "TOSS_CANCEL_UNVERIFIED");
                        return new Result(Outcome.CANCELED, orderId, hash(key), amountKrw, "KRW", null);
                    });
        } catch (RuntimeException failure) { return unknown(orderId, "TOSS_TRANSPORT_ERROR"); }
    }

    private JsonNode readPayment(ClientHttpResponse response) throws IOException {
        if (response.getStatusCode().value() != 200) return null;
        byte[] body = response.getBody().readNBytes(MAX_RESPONSE_BYTES + 1);
        return body.length > MAX_RESPONSE_BYTES ? null : mapper.readTree(body);
    }
    private boolean zeroBalance(JsonNode payment) {
        return payment.path("balanceAmount").isIntegralNumber() && payment.path("balanceAmount").canConvertToInt() && payment.path("balanceAmount").intValue() == 0;
    }

    private Result parse(UUID orderId, Confirmation expected, ClientHttpResponse response) throws IOException {
        int status = response.getStatusCode().value();
        if (status != 200) return unknown(orderId, "TOSS_HTTP_" + status);
        byte[] body = response.getBody().readNBytes(MAX_RESPONSE_BYTES + 1);
        if (body.length > MAX_RESPONSE_BYTES) return unknown(orderId, "TOSS_RESPONSE_TOO_LARGE");
        try {
            JsonNode payment = mapper.readTree(body);
            if (payment == null || !orderId.toString().equals(text(payment, "orderId"))
                    || !"KRW".equals(text(payment, "currency")) || !"NORMAL".equals(text(payment, "type"))) {
                return unknown(orderId, "TOSS_RESPONSE_MISMATCH");
            }
            String paymentKey = text(payment, "paymentKey");
            JsonNode amount = payment.path("totalAmount");
            if (paymentKey == null || paymentKey.isBlank() || paymentKey.length() > 200
                    || !amount.isIntegralNumber() || !amount.canConvertToInt() || amount.intValue() <= 0
                    || (expected != null && (!expected.paymentKey().equals(paymentKey)
                            || expected.amountKrw() != amount.intValue()))) {
                return unknown(orderId, "TOSS_RESPONSE_MISMATCH");
            }
            if ("PARTIAL_CANCELED".equals(text(payment, "status"))) {
                JsonNode balance = payment.path("balanceAmount");
                if (!balance.isIntegralNumber() || !balance.canConvertToInt() || balance.intValue() <= 0 || balance.intValue() >= amount.intValue())
                    return unknown(orderId, "TOSS_RESPONSE_MISMATCH");
                return new Result(Outcome.UNKNOWN, orderId, hash(paymentKey), amount.intValue(), "KRW", "PAYMENT_PARTIAL_CANCEL_REVIEW");
            }
            Outcome outcome = switch (Objects.toString(text(payment, "status"), "")) {
                case "DONE" -> Outcome.APPROVED;
                case "READY", "IN_PROGRESS", "WAITING_FOR_DEPOSIT" -> Outcome.PENDING;
                case "CANCELED" -> Outcome.CANCELED;
                case "ABORTED", "EXPIRED" -> Outcome.DECLINED;
                default -> Outcome.UNKNOWN;
            };
            if (outcome == Outcome.UNKNOWN) return unknown(orderId, "TOSS_STATUS_UNSUPPORTED");
            if (outcome == Outcome.CANCELED && !zeroBalance(payment)) return unknown(orderId, "TOSS_CANCEL_UNVERIFIED");
            if (outcome == Outcome.APPROVED) {
                JsonNode balance = payment.path("balanceAmount");
                if (!balance.isIntegralNumber() || !balance.canConvertToInt()
                        || balance.intValue() != amount.intValue()) return unknown(orderId, "TOSS_RESPONSE_MISMATCH");
                OffsetDateTime.parse(text(payment, "approvedAt"));
            }
            return new Result(outcome, orderId, hash(paymentKey), amount.intValue(), "KRW", null);
        } catch (RuntimeException | IOException exception) {
            return unknown(orderId, "TOSS_INVALID_RESPONSE");
        }
    }

    private String text(JsonNode value, String field) {
        return value.path(field).isTextual() ? value.path(field).textValue() : null;
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }

    private Result unknown(UUID id, String code) { return new Result(Outcome.UNKNOWN, id, null, 0, null, code); }

    private static void validate(TossPaymentProperties properties, Environment environment) {
        try {
            URI url = URI.create(properties.baseUrl());
            boolean official = "https://api.tosspayments.com".equals(properties.baseUrl());
            boolean localTest = environment.acceptsProfiles(Profiles.of("test"))
                    && !environment.acceptsProfiles(Profiles.of("staging", "production", "prod"))
                    && "http".equals(url.getScheme()) && "127.0.0.1".equals(url.getHost())
                    && url.getPort() > 0 && url.getUserInfo() == null && url.getQuery() == null
                    && url.getFragment() == null && url.getPath().isEmpty();
            if (environment.acceptsProfiles(Profiles.of("production", "prod"))
                    || (!official && !localTest) || properties.secretKey() == null
                    || !properties.secretKey().matches("test_gsk_[A-Za-z0-9_]+")
                    || !validTimeout(properties.connectTimeout()) || !validTimeout(properties.readTimeout())) {
                throw new IllegalStateException();
            }
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Toss test API configuration is invalid");
        }
    }

    private static boolean validTimeout(Duration timeout) {
        return timeout != null && timeout.toMillis() > 0 && timeout.compareTo(Duration.ofSeconds(60)) <= 0;
    }
}
