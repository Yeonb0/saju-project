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
import java.time.Instant;
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
        boolean remaining = "REMAINING_TOP_UP".equals(reasonCode);
        if (amountKrw <= 0 || !(remaining || "UNUSED_TOP_UP".equals(reasonCode))) return unknown(orderId, "TOSS_CANCEL_INVALID");
        try {
            // Resolve the raw provider key transiently; only its hash is stored in our database.
            JsonNode payment = client.get().uri("/v1/payments/orders/{id}", orderId)
                    .exchange((request, response) -> readPayment(response));
            if (payment == null || !orderId.toString().equals(text(payment, "orderId"))
                    || !"KRW".equals(text(payment, "currency")) || !"NORMAL".equals(text(payment, "type"))
                    || !payment.path("totalAmount").isIntegralNumber() || !payment.path("totalAmount").canConvertToInt()
                    || payment.path("totalAmount").intValue() < amountKrw
                    || !remaining && payment.path("totalAmount").intValue() != amountKrw) return unknown(orderId, "TOSS_RESPONSE_MISMATCH");
            int originalAmount = payment.path("totalAmount").intValue();
            String key = text(payment, "paymentKey");
            if (key == null || !key.matches("[A-Za-z0-9_-]{1,200}")) return unknown(orderId, "TOSS_RESPONSE_MISMATCH");
            if ("CANCELED".equals(text(payment, "status")) && zeroBalance(payment) && amountKrw == originalAmount)
                return new Result(Outcome.CANCELED, orderId, hash(key), originalAmount, "KRW", null);
            if (!"DONE".equals(text(payment, "status")) || !payment.path("balanceAmount").isIntegralNumber()
                    || payment.path("balanceAmount").longValue() != originalAmount) return unknown(orderId, "TOSS_CANCEL_REVIEW_REQUIRED");
            if (remaining && amountKrw < originalAmount && payment.path("isPartialCancelable").isBoolean()
                    && !payment.path("isPartialCancelable").booleanValue())
                return new Result(Outcome.CANCEL_REJECTED, orderId, hash(key), originalAmount, "KRW", "CASH_REFUND_REJECTED");
            if (remaining && "가상계좌".equals(text(payment, "method")))
                return new Result(Outcome.CANCEL_REJECTED, orderId, hash(key), originalAmount, "KRW", "CASH_REFUND_REJECTED");
            return client.post().uri("/v1/payments/{key}/cancel", key)
                    .header("Idempotency-Key", "top-up-refund-" + refundId)
                    .body(Map.of("cancelReason", remaining ? "Remaining paid top-up refund" : "Unused top-up refund", "cancelAmount", amountKrw))
                    .exchange((request, response) -> {
                        if (response.getStatusCode().value() == 400) {
                            byte[] error = response.getBody().readNBytes(MAX_RESPONSE_BYTES + 1);
                            if (error.length <= MAX_RESPONSE_BYTES && "REFUND_REJECTED".equals(text(mapper.readTree(error), "code"))) {
                                Result stillPaid = lookup(orderId);
                                var intent = new com.sajuppugi.payment.domain.TopUpConfirmationIntent(orderId, hash(key), originalAmount, Instant.EPOCH);
                                if (stillPaid.confirmsRefund(intent, amountKrw)) return stillPaid;
                                if (stillPaid.outcome() == Outcome.APPROVED && hash(key).equals(stillPaid.paymentKeyHash())
                                        && stillPaid.amountKrw() == originalAmount)
                                    return new Result(Outcome.CANCEL_REJECTED, orderId, hash(key), originalAmount, "KRW", "CASH_REFUND_REJECTED");
                            }
                            return unknown(orderId, "TOSS_CANCEL_UNVERIFIED");
                        }
                        JsonNode canceled = readPayment(response);
                        if (canceled == null || !orderId.toString().equals(text(canceled, "orderId"))
                                || !key.equals(text(canceled, "paymentKey")) || !"KRW".equals(text(canceled, "currency"))
                                || !"NORMAL".equals(text(canceled, "type")) || !canceled.path("totalAmount").isIntegralNumber()
                                || !canceled.path("totalAmount").canConvertToInt()
                                || canceled.path("totalAmount").longValue() != originalAmount) return unknown(orderId, "TOSS_CANCEL_UNVERIFIED");
                        if (amountKrw == originalAmount && "CANCELED".equals(text(canceled, "status")) && zeroBalance(canceled))
                            return new Result(Outcome.CANCELED, orderId, hash(key), originalAmount, "KRW", null);
                        Integer canceledAmount = verifiedPartialAmount(canceled, originalAmount);
                        if (remaining && canceledAmount != null && canceledAmount == amountKrw)
                            return new Result(Outcome.UNKNOWN, orderId, hash(key), originalAmount, "KRW", "PAYMENT_PARTIAL_CANCEL_REVIEW", canceledAmount);
                        return unknown(orderId, "TOSS_CANCEL_UNVERIFIED");
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
                return new Result(Outcome.UNKNOWN, orderId, hash(paymentKey), amount.intValue(), "KRW", "PAYMENT_PARTIAL_CANCEL_REVIEW",
                        verifiedPartialAmount(payment, amount.intValue()));
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

    private Integer verifiedPartialAmount(JsonNode payment, int original) {
        if (!"PARTIAL_CANCELED".equals(text(payment, "status"))) return null;
        JsonNode balance = payment.path("balanceAmount"), cancels = payment.path("cancels");
        if (!balance.isIntegralNumber() || !balance.canConvertToInt() || balance.intValue() <= 0
                || balance.intValue() >= original || !cancels.isArray() || cancels.isEmpty()) return null;
        long sum = 0;
        var keys = new java.util.HashSet<String>();
        for (JsonNode cancel : cancels) {
            String transaction = text(cancel, "transactionKey");
            JsonNode amount = cancel.path("cancelAmount");
            if (!"DONE".equals(text(cancel, "cancelStatus")) || transaction == null || transaction.isBlank()
                    || transaction.length() > 64 || !keys.add(transaction) || !amount.isIntegralNumber()
                    || !amount.canConvertToInt() || amount.intValue() <= 0) return null;
            sum += amount.intValue();
            if (sum >= original) return null;
        }
        return sum == original - balance.intValue() ? (int) sum : null;
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
            boolean production = environment.acceptsProfiles(Profiles.of("production", "prod"));
            boolean staging = environment.acceptsProfiles(Profiles.of("staging"));
            boolean localProfile = environment.acceptsProfiles(Profiles.of("local", "test"));
            boolean liveEnabled = environment.getProperty("app.toss.live-enabled", Boolean.class, false);
            boolean localTest = environment.acceptsProfiles(Profiles.of("test"))
                    && !environment.acceptsProfiles(Profiles.of("staging", "production", "prod"))
                    && "http".equals(url.getScheme()) && "127.0.0.1".equals(url.getHost())
                    && url.getPort() > 0 && url.getUserInfo() == null && url.getQuery() == null
                    && url.getFragment() == null && url.getPath().isEmpty();
            boolean validKey = properties.secretKey() != null && properties.secretKey().matches(
                    production ? "live_(?:gsk|sk)_[A-Za-z0-9_]+" : "test_(?:gsk|sk)_[A-Za-z0-9_]+");
            if ((production && (!liveEnabled || !official || localProfile || staging))
                    || (!production && liveEnabled) || (!official && !localTest) || !validKey
                    || !validTimeout(properties.connectTimeout()) || !validTimeout(properties.readTimeout())) {
                throw new IllegalStateException();
            }
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Toss API environment, credentials or timeouts are invalid");
        }
    }

    private static boolean validTimeout(Duration timeout) {
        return timeout != null && timeout.toMillis() > 0 && timeout.compareTo(Duration.ofSeconds(60)) <= 0;
    }
}
