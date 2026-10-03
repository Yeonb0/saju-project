package com.sajuppugi.common.api;

import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.errorCode().status())
                .body(ApiError.of(exception.errorCode()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpectedException(Exception exception) {
        // Exception messages can contain request values or provider secrets.
        LOG.error("Unhandled request failure: type={}", exception.getClass().getSimpleName());
        return ResponseEntity.internalServerError().body(ApiError.of(ErrorCode.INTERNAL_SERVER_ERROR));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        List<FieldError> fields = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), validationReason(error.getCode())))
                .distinct()
                .sorted(Comparator.comparing(FieldError::field).thenComparing(FieldError::reason))
                .toList();
        return new ResponseEntity<>(ApiError.of(ErrorCode.VALIDATION_FAILED, fields), headers, status);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        ErrorCode code = switch (status.value()) {
            case 400 -> exception instanceof org.springframework.http.converter.HttpMessageNotReadableException
                    ? ErrorCode.MALFORMED_JSON : ErrorCode.INVALID_REQUEST;
            case 404 -> ErrorCode.RESOURCE_NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 406 -> ErrorCode.NOT_ACCEPTABLE;
            case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            default -> status.is5xxServerError() ? ErrorCode.INTERNAL_SERVER_ERROR : ErrorCode.INVALID_REQUEST;
        };
        return new ResponseEntity<>(ApiError.of(code), headers, status);
    }

    private static String validationReason(String code) {
        if (code == null) {
            return "NOT_ALLOWED";
        }
        return switch (code) {
            case "NotNull", "NotBlank", "NotEmpty" -> "REQUIRED";
            case "Min", "Max", "DecimalMin", "DecimalMax", "Positive", "PositiveOrZero",
                    "Negative", "NegativeOrZero", "Past", "PastOrPresent", "Future", "FutureOrPresent" -> "OUT_OF_RANGE";
            case "Email", "Pattern", "typeMismatch" -> "INVALID_FORMAT";
            case "Size" -> "OUT_OF_RANGE";
            default -> "NOT_ALLOWED";
        };
    }
}
