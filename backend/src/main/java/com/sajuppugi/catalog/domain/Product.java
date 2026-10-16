package com.sajuppugi.catalog.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Product(UUID id, String code, Category category, Price price,
        boolean active, Instant saleStartsAt, Instant saleEndsAt, String catalogVersion,
        String productName, Price regularPrice, FortuneType fortuneType, ProductOption productOption) {
    public Product(UUID id, String code, Category category, Price price, boolean active,
            Instant saleStartsAt, Instant saleEndsAt, String catalogVersion) {
        this(id, code, category, price, active, saleStartsAt, saleEndsAt, catalogVersion,
                code, null, null, null);
    }

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
        if (productName == null || productName.isBlank()) {
            productName = code;
        }
        if (regularPrice != null && (regularPrice.currency() != price.currency()
                || regularPrice.amount() < price.amount())) {
            throw new IllegalArgumentException("Regular price must use the sale currency and cover the sale price");
        }
    }

    public boolean isAvailableAt(Instant now) {
        Objects.requireNonNull(now, "now");
        return active && (saleStartsAt == null || !now.isBefore(saleStartsAt))
                && (saleEndsAt == null || now.isBefore(saleEndsAt));
    }

    /**
     * Display-only lifecycle. Purchase eligibility must continue to use {@link #isAvailableAt(Instant)}.
     * An inactive product is retired even if it has historical sale dates.
     */
    public SaleStatus saleStatusAt(Instant now) {
        Objects.requireNonNull(now, "now");
        if (!active) return SaleStatus.RETIRED;
        if (saleStartsAt != null && now.isBefore(saleStartsAt)) return SaleStatus.UPCOMING;
        if (saleEndsAt != null && !now.isBefore(saleEndsAt)) return SaleStatus.ENDED;
        return SaleStatus.AVAILABLE;
    }

    // Internal persistence names; the HTTP catalog uses FORTUNE for READING.
    public enum Category { TOP_UP, READING, GIFT, TALISMAN_ADDON }
    public enum FortuneType { OVERALL, LOVE, WEALTH, COMPATIBILITY, SINSAL, SUNEUNG }
    public enum ProductOption { READING_ONLY, READING_WITH_TALISMAN }
    public enum SaleStatus { UPCOMING, AVAILABLE, ENDED, RETIRED }
}
