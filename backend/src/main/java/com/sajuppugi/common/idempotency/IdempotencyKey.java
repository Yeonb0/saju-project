package com.sajuppugi.common.idempotency;

public record IdempotencyKey(String value) {
    public IdempotencyKey {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Idempotency key is required");
        }
    }

    @Override
    public String toString() {
        return "IdempotencyKey[REDACTED]";
    }
}
