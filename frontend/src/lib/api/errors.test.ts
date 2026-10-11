import { describe, expect, it } from "vitest";
import {
  ApiError,
  type ApiErrorKind,
  ApiNetworkError,
  classifyApiError,
} from "./errors";

// 픽스처일 뿐이며 실제 API · 규칙과 무관하다. code 와 status 조합은 분류를 보기 위한 값이다.
function apiError(status: number, code: string | null) {
  return new ApiError({ status, code, traceId: "fixture-trace" });
}

describe("classifyApiError", () => {
  const cases: Array<[number, string | null, ApiErrorKind]> = [
    [401, "AUTHENTICATION_REQUIRED", "login_required"],
    [401, "SESSION_EXPIRED", "login_required"],
    [401, "SOMETHING_NEW", "login_required"],
    [403, "CSRF_FAILED", "csrf_failed"],
    [403, "FORBIDDEN", "forbidden"],
    [409, "INSUFFICIENT_BALANCE", "insufficient_balance"],
    [409, "IDEMPOTENCY_REQUEST_PROCESSING", "request_processing"],
    [409, "QUOTE_EXPIRED", "quote_expired"],
    [409, "PRICE_CHANGED", "price_changed"],
    [409, "SOMETHING_NEW", "conflict"],
    [404, "RESOURCE_NOT_FOUND", "not_found"],
    [410, "GIFT_EXPIRED", "gone"],
    [400, "VALIDATION_FAILED", "invalid_input"],
    [422, "PRODUCT_NOT_AVAILABLE", "invalid_input"],
    [429, "RATE_LIMITED", "rate_limited"],
    [500, "INTERNAL_SERVER_ERROR", "server"],
    [502, null, "server"],
    [422, "BIRTH_TIME_REQUIRED_AT_TERM", "birth_time_required"],
    [500, "PURCHASE_DEBIT_MISMATCH", "server"],
    [503, "READING_FULFILLMENT_UNAVAILABLE", "server"],
    [418, "SOMETHING_NEW", "unknown"],
  ];

  it.each(cases)("%i %s → %s", (status, code, kind) => {
    expect(classifyApiError(apiError(status, code))).toBe(kind);
  });

  it("알려진 code 라도 status 가 401 이면 로그인", () => {
    expect(classifyApiError(apiError(401, "CSRF_FAILED"))).toBe(
      "login_required",
    );
  });

  it("네트워크 오류는 결과 불명확, 그 밖의 값은 unknown", () => {
    expect(classifyApiError(new ApiNetworkError())).toBe("outcome_unknown");
    expect(classifyApiError(new Error("x"))).toBe("unknown");
  });
});
