package com.sajuppugi.common.api;

import com.sajuppugi.common.logging.TraceContext;

public record ApiResponse<T>(T data, String traceId) {
    public static <T> ApiResponse<T> of(T data) {
        return new ApiResponse<>(data, TraceContext.currentId());
    }
}
