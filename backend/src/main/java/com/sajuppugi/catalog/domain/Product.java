package com.sajuppugi.catalog.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Product(UUID id, String code, Category category, Price price,
        boolean active, Instant saleStartsAt, Instant saleEndsAt, String catalogVersion) {
    public Product {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(price, "price");
        if (code == null || code.isBlank() || catalogVersion == null || catalogVersion.isBlank()) {
            throw new IllegalArgumentException("Product code and catalog version are required");
        }
        if (saleStartsAt != null && saleEndsAt != null && !saleEndsAt.isAfter(saleStartsAt)) {
            throw new IllegalArgumentException("Sale end must follow sale start");
        }
    }

    public boolean isAvailableAt(Instant now) {
        Objects.requireNonNull(now, "now");
        return active && (saleStartsAt == null || !now.isBefore(saleStartsAt))
                && (saleEndsAt == null || now.isBefore(saleEndsAt));
    }

    // Internal persistence names; the HTTP catalog uses FORTUNE for READING.
    public enum Category { TOP_UP, READING, GIFT, TALISMAN_ADDON }
}
