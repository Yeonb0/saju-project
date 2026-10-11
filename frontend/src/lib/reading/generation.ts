// 결과 생성 실패 판단 (Phase 4 LoadingScene). 근거: F-06 부분 확정 — 만세력 계산 · Liner 문장은 구매 직후 동기 생성,
// 최종 실패면 서버가 재화를 복구한다. COMMON 4.8 — 500 READING_GENERATION_FAILED · CALCULATION_FAILED 는 "환급 · 재시도 안내".
// 환급 문장은 서버가 상태로 알려 준 REFUNDED 일 때만 쓴다. 구매 응답 FAILED 와 위 오류 code 는 실패 안내만 하고 환급은 단정하지 않는다
// (보상이 실패해도 이 code 가 온다, Q-38). 두 경우를 한 모양으로 모은다.
import { ApiError } from "@/lib/api/errors";
import type { ReadingPurchaseResult } from "@/lib/ports/fortune";

const GENERATION_FAILURE_CODES = new Set([
  "READING_GENERATION_FAILED",
  "CALCULATION_FAILED",
]);

export type GenerationFailure = Readonly<{
  // 서버가 환급했다고 상태(REFUNDED)로 알려 줬는지. 오류 code 만으로는 true 로 하지 않는다 (Q-38)
  refunded: boolean;
}>;

export function generationFailureOfResult(
  result: ReadingPurchaseResult,
): GenerationFailure | null {
  if (result.status === "FAILED" || result.status === "REFUNDED") {
    return { refunded: result.status === "REFUNDED" };
  }
  return null;
}

export function generationFailureOfError(
  error: unknown,
): GenerationFailure | null {
  if (
    error instanceof ApiError &&
    error.code !== null &&
    GENERATION_FAILURE_CODES.has(error.code)
  ) {
    return { refunded: false };
  }
  return null;
}
