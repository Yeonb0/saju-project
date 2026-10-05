package com.sajuppugi.catalog.domain;

import java.util.Objects;

public record TopUpProduct(Product product, int paidAmount, int bonusAmount) {
    public TopUpProduct {
        Objects.requireNonNull(product, "product");
        if (product.category() != Product.Category.TOP_UP || product.price().currency() != Price.Currency.KRW
                || paidAmount <= 0 || bonusAmount < 0) {
            throw new IllegalArgumentException("Invalid top-up product");
        }
        Math.addExact(paidAmount, bonusAmount);
    }

    public int creditedAmount() {
        return Math.addExact(paidAmount, bonusAmount);
    }
}
