import { describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api/errors";
import type { ReadingPurchaseResult } from "@/lib/ports/fortune";
import {
  generationFailureOfError,
  generationFailureOfResult,
} from "./generation";

// 픽스처일 뿐이며 실제 가격 · 규칙과 무관하다
const result = (
  status: ReadingPurchaseResult["status"],
  refunded = false,
): ReadingPurchaseResult => ({
  purchaseId: "p",
  readingId: "r",
  status,
  charged: { currency: "TURTLE_SHELL", amount: 0 },
  balance: 0,
  refunded,
});

describe("결과 생성 실패 판단 (F-06 · COMMON 4.8)", () => {
  it("구매 응답 FAILED · REFUNDED 는 실패, 환급 여부는 서버 값", () => {
    expect(generationFailureOfResult(result("FAILED", true))).toEqual({
      refunded: true,
    });
    expect(generationFailureOfResult(result("FAILED", false))).toEqual({
      refunded: false,
    });
    expect(generationFailureOfResult(result("REFUNDED"))).toEqual({
      refunded: true,
    });
  });

  it("FULFILLED 와 모르는 상태는 실패로 보지 않는다", () => {
    expect(generationFailureOfResult(result("FULFILLED"))).toBeNull();
    expect(generationFailureOfResult(result("UNKNOWN"))).toBeNull();
  });

  it("오류 code 로만 판단한다", () => {
    const err = (code: string | null) =>
      new ApiError({ status: 500, code, traceId: null });
    expect(generationFailureOfError(err("READING_GENERATION_FAILED"))).toEqual({
      refunded: true,
    });
    expect(generationFailureOfError(err("CALCULATION_FAILED"))).not.toBeNull();
    expect(generationFailureOfError(err("INTERNAL_ERROR"))).toBeNull();
    expect(generationFailureOfError(err(null))).toBeNull();
    expect(generationFailureOfError(new Error("x"))).toBeNull();
  });
});
