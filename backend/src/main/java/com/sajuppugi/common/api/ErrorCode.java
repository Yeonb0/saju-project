package com.sajuppugi.common.api;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 형식을 확인해 주세요."),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "같은 요청 키에 다른 요청을 사용할 수 없습니다."),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.CONFLICT, "주문 금액과 결제 요청 금액이 다릅니다."),
    PAYMENT_CONFIRMATION_CONFLICT(HttpStatus.CONFLICT, "기존 결제 확인 요청과 일치하지 않습니다."),
    PAYMENT_ORDER_NOT_CONFIRMABLE(HttpStatus.CONFLICT, "현재 결제를 승인할 수 없는 주문입니다."),
    PAYMENT_FULFILLMENT_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "결제 처리 준비 중입니다. 잠시 후 다시 시도해 주세요."),
    REFUND_NOT_ELIGIBLE(HttpStatus.CONFLICT, "환불 가능한 지급분이 없거나 먼저 확인할 거래가 있습니다. 고객센터로 문의해 주세요."),
    REFUND_QUOTE_CHANGED(HttpStatus.CONFLICT, "환불 대상 금액 또는 수량이 바뀌었습니다. 다시 확인해 주세요."),
    WALLET_REVIEW_REQUIRED(HttpStatus.CONFLICT, "결제 상태 확인이 필요해 등껍질 사용이 제한되었습니다. 고객센터로 문의해 주세요."),
    INSUFFICIENT_BALANCE(HttpStatus.CONFLICT, "등껍질이 부족합니다."),
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "목록 조회 위치를 확인해 주세요."),
    MALFORMED_JSON(HttpStatus.BAD_REQUEST, "JSON 요청 형식을 확인해 주세요."),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),
    UNSUPPORTED_CALENDAR_DATE(HttpStatus.UNPROCESSABLE_ENTITY, "지원하지 않는 생년월일입니다."),
    BIRTH_TIME_REQUIRED_AT_TERM(HttpStatus.UNPROCESSABLE_ENTITY, "절기 경계일에는 태어난 시간이 필요합니다."),
    INVALID_FORTUNE_TYPE(HttpStatus.UNPROCESSABLE_ENTITY, "지원하지 않는 운세 유형입니다."),
    INVALID_QUESTION_KEY(HttpStatus.UNPROCESSABLE_ENTITY, "운세 유형에 맞지 않는 질문입니다."),
    COMPATIBILITY_PERSON_REQUIRED(HttpStatus.UNPROCESSABLE_ENTITY, "궁합을 볼 상대를 선택해 주세요."),
    COMPATIBILITY_SELF_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY, "본인과 같은 인물로 궁합을 볼 수 없습니다."),
    RELATION_TYPE_REQUIRED(HttpStatus.UNPROCESSABLE_ENTITY, "궁합 관계를 선택해 주세요."),
    PERSON_NOT_FOUND(HttpStatus.NOT_FOUND, "인물을 찾을 수 없습니다."),
    PERSON_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "저장할 수 있는 타인은 최대 10명입니다."),
    PRIMARY_PERSON_ALREADY_EXISTS(HttpStatus.CONFLICT, "본인 정보가 이미 등록되어 있습니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원 정보를 찾을 수 없습니다."),
    MEMBER_WITHDRAWAL_NOT_ALLOWED(HttpStatus.CONFLICT, "현재 상태에서는 탈퇴를 요청할 수 없습니다."),
    PAID_BALANCE_REMAINS(HttpStatus.CONFLICT, "유료 등껍질 환불 후 탈퇴할 수 있습니다."),
    READING_NOT_FOUND(HttpStatus.NOT_FOUND, "운세 결과를 찾을 수 없습니다."),
    PURCHASE_NOT_FOUND(HttpStatus.NOT_FOUND, "구매 내역을 찾을 수 없습니다."),
    READING_FULFILLMENT_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "운세 구매 준비 중입니다. 잠시 후 다시 시도해 주세요."),
    READING_GENERATION_FAILED(HttpStatus.BAD_GATEWAY, "운세 생성에 실패했습니다. 사용한 등껍질은 복구됩니다."),
    PURCHASE_DEBIT_MISMATCH(HttpStatus.INTERNAL_SERVER_ERROR, "구매 처리 중 금액 검증에 실패했습니다."),
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
