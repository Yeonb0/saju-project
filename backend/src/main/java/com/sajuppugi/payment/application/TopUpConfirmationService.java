package com.sajuppugi.payment.application;

import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.payment.domain.TopUpConfirmationIntent;
import com.sajuppugi.payment.infrastructure.JdbcTopUpConfirmations;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class TopUpConfirmationService {
    private final JdbcTopUpConfirmations confirmations;
    private final Clock clock;

    public TopUpConfirmationService(JdbcTopUpConfirmations confirmations, Clock clock) {
        this.confirmations = confirmations;
        this.clock = clock;
    }

    /** Called before any provider request; does not call the provider or grant wallet lots. */
    public TopUpConfirmationIntent prepare(UUID user, UUID orderId, String paymentKey, int amount, String key) {
        if (user == null || orderId == null || invalidKey(paymentKey) || paymentKey.length() > 200
                || invalidKey(key) || amount <= 0) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        String keyHash = hash(key);
        try {
            return confirmations.bind(user, orderId, hash(paymentKey), amount, keyHash,
                    clock.instant().truncatedTo(ChronoUnit.MICROS));
        } catch (DuplicateKeyException exception) {
            // Cross-order races on a payment or request key roll back the complete losing intent.
            if (confirmations.findRequestOrder(user, keyHash).filter(id -> !id.equals(orderId)).isPresent()) {
                throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
            }
            throw new ApiException(ErrorCode.PAYMENT_CONFIRMATION_CONFLICT);
        }
    }

    private boolean invalidKey(String key) { return key == null || key.isBlank() || key.length() > 512; }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
