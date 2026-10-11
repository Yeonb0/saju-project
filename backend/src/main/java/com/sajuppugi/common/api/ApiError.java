package com.sajuppugi.common.api;

import com.sajuppugi.common.logging.TraceContext;
import java.util.List;
import java.util.Map;

@io.swagger.v3.oas.annotations.media.Schema(requiredProperties = {"code", "message", "traceId", "fieldErrors", "details"})
public record ApiError(String code, String message, String traceId, List<FieldError> fieldErrors,
                       Map<String, Object> details) {
    public static ApiError of(ErrorCode code) {
        return of(code, List.of());
    }

    public static ApiError of(ErrorCode code, List<FieldError> fields) {
        return of(code, fields, Map.of());
    }

    public static ApiError of(ErrorCode code, List<FieldError> fields, Map<String, Object> details) {
        return new ApiError(code.name(), code.message(), TraceContext.currentId(), fields, details);
    }
}
