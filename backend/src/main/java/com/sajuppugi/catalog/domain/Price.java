package com.sajuppugi.catalog.domain;

import java.util.Objects;

public record Price(Currency currency, int amount) {
    public Price {
        Objects.requireNonNull(currency, "currency");
        if (amount <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }
    }

    public enum Currency { KRW, TURTLE_SHELL }
}
