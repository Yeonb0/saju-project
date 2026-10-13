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
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class TopUpOrderService {
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;
    private final JdbcTopUpOrders orders;
    private final CatalogUseCase catalog;
    private final Clock clock;
    private final com.sajuppugi.wallet.application.WalletOperationsService walletOperations;

    public TopUpOrderService(JdbcTopUpOrders orders, CatalogUseCase catalog, Clock clock,
            com.sajuppugi.wallet.application.WalletOperationsService walletOperations) {
        this.orders = orders;
        this.catalog = catalog;
        this.clock = clock;
        this.walletOperations = walletOperations;
    }

    public TopUpOrder create(UUID user, String productCode, String key) {
        if (user == null || productCode == null || productCode.isBlank()
                || key == null || key.isBlank() || key.length() > 512) {
            throw new ApiException(ErrorCode.INVALID_REQUEST);
        }
        String hash = hash(key);
        var existing = orders.findByKey(user, hash);
        if (existing.isPresent()) return match(existing.get(), productCode);
        walletOperations.assertUsable(user);
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

    public Page list(UUID user, String cursor, Integer size) {
        if (user == null) throw new ApiException(ErrorCode.AUTHENTICATION_REQUIRED);
        int pageSize = size == null ? DEFAULT_SIZE : size;
        if (pageSize < 1 || pageSize > MAX_SIZE) throw new ApiException(ErrorCode.INVALID_REQUEST);
        Cursor decoded = decode(cursor);
        List<TopUpOrder> fetched = orders.findOwnedPage(user,
                decoded == null ? null : decoded.createdAt(), decoded == null ? null : decoded.id(), pageSize + 1);
        boolean hasNext = fetched.size() > pageSize;
        List<TopUpOrder> items = hasNext ? List.copyOf(fetched.subList(0, pageSize)) : List.copyOf(fetched);
        return new Page(items, hasNext ? encode(items.getLast()) : null, hasNext);
    }

    private String encode(TopUpOrder order) {
        String value = order.createdAt() + "|" + order.id();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private Cursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) return null;
        try {
            String value = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = value.split("\\|", -1);
            if (parts.length != 2) throw new IllegalArgumentException("invalid cursor parts");
            return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.INVALID_CURSOR);
        }
    }

    public record Page(List<TopUpOrder> items, String nextCursor, boolean hasNext) {}
    private record Cursor(Instant createdAt, UUID id) {}

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
