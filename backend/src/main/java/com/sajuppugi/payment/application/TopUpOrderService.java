package com.sajuppugi.payment.application;

import com.sajuppugi.catalog.application.CatalogUseCase;
import com.sajuppugi.common.api.ApiException;
import com.sajuppugi.common.api.ErrorCode;
import com.sajuppugi.payment.domain.TopUpOrder;
import com.sajuppugi.payment.infrastructure.JdbcTopUpOrders;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class TopUpOrderService {
    private final JdbcTopUpOrders orders;
    private final CatalogUseCase catalog;
    private final Clock clock;

    public TopUpOrderService(JdbcTopUpOrders orders, CatalogUseCase catalog, Clock clock) {
        this.orders = orders;
        this.catalog = catalog;
        this.clock = clock;
    }

    public TopUpOrder create(UUID user, String productCode, String key) {
        if (user == null || productCode == null || productCode.isBlank()
                || key == null || key.isBlank() || key.length() > 512) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        String hash = hash(key);
        var existing = orders.findByKey(user, hash);
        if (existing.isPresent()) return match(existing.get(), productCode);
        var product = catalog.availableTopUps().stream()
                .filter(value -> value.product().code().equals(productCode)).findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.PRODUCT_NOT_AVAILABLE));
        var order = new TopUpOrder(UUID.randomUUID(), user, productCode, product.product().price().amount(),
                product.paidAmount(), product.bonusAmount(), TopUpOrder.Status.PAYMENT_PENDING, clock.instant());
        try {
            orders.insert(order, hash);
            return order;
        } catch (DuplicateKeyException exception) {
            // The losing insert has rolled back before reading the committed winner.
            return match(orders.findByKey(user, hash).orElseThrow(() -> exception), productCode);
        }
    }

    public TopUpOrder get(UUID user, UUID id) {
        return orders.findOwned(user, id).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private TopUpOrder match(TopUpOrder order, String product) {
        if (!order.productCode().equals(product)) throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        return order;
    }

    private String hash(String key) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
