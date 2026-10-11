package com.sajuppugi.common.api;

import java.util.Map;

public class ApiException extends RuntimeException {
    private final ErrorCode errorCode;
    private final Map<String, Object> details;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, Map.of());
    }

    public ApiException(ErrorCode errorCode, Map<String, Object> details) {
        super(errorCode.name());
        this.errorCode = errorCode;
        if (details == null || details.entrySet().stream().anyMatch(entry -> entry.getKey() == null
                || !(entry.getValue() == null || entry.getValue() instanceof String
                || entry.getValue() instanceof Number || entry.getValue() instanceof Boolean))) {
            throw new IllegalArgumentException("Error details must be primitive values");
        }
        this.details = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(details));
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
    public Map<String, Object> details() { return details; }
}
