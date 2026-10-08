package com.sajuppugi.common.api;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식을 확인해 주세요."),
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "목록 조회 위치를 확인해 주세요."),
    MALFORMED_JSON(HttpStatus.BAD_REQUEST, "JSON 요청 형식을 확인해 주세요."),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),
    UNSUPPORTED_CALENDAR_DATE(HttpStatus.UNPROCESSABLE_ENTITY, "지원하지 않는 생년월일입니다."),
    BIRTH_TIME_REQUIRED_AT_TERM(HttpStatus.UNPROCESSABLE_ENTITY, "절기 경계일에는 태어난 시간이 필요합니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 정보를 찾을 수 없습니다."),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "상품을 찾을 수 없습니다."),
    PRODUCT_NOT_AVAILABLE(HttpStatus.UNPROCESSABLE_ENTITY, "현재 구매할 수 없는 상품입니다."),
    QUOTE_EXPIRED(HttpStatus.CONFLICT, "견적이 만료되었습니다. 다시 확인해 주세요."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "지원하지 않는 응답 형식입니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식입니다."),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    CSRF_FAILED(HttpStatus.FORBIDDEN, "요청을 확인할 수 없습니다. 새로고침 후 다시 시도해 주세요."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
