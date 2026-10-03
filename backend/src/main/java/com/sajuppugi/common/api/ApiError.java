package com.sajuppugi.common.api;

import com.sajuppugi.common.logging.TraceContext;
import java.util.List;

public record ApiError(String code, String message, String traceId, List<FieldError> fieldErrors) {
    public static ApiError of(ErrorCode code) {
        return of(code, List.of());
    }

    public static ApiError of(ErrorCode code, List<FieldError> fields) {
        return new ApiError(code.name(), code.message(), TraceContext.currentId(), fields);
    }
}
