package com.sajuppugi.payment.port;

import com.sajuppugi.payment.domain.TopUpOrder;
import java.util.Optional;
import java.util.UUID;

public interface TopUpOrderRepository {
    Optional<TopUpOrder> findOwned(UUID userId, UUID orderId);

    void insert(TopUpOrder order);

    /** Conditional transition; CREDITED requires an atomic wallet grant in the same transaction. */
    boolean transition(UUID orderId, TopUpOrder.Status expected, TopUpOrder.Status next);
}
