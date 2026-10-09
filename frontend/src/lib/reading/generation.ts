// 결과 생성 실패 판단 (Phase 4 LoadingScene). 근거: F-06 부분 확정 — 만세력 계산 · Liner 문장은 구매 직후 동기 생성,
// 최종 실패면 서버가 재화를 복구한다. COMMON 4.8 — 500 READING_GENERATION_FAILED · CALCULATION_FAILED 는 "환급 · 재시도 안내".
// API_SPEC 8장 초안 — 구매 응답이 FAILED 이고 refunded=true 일 수도 있다. 두 경우를 한 모양으로 모은다.
import { ApiError } from "@/lib/api/errors";
import type { ReadingPurchaseResult } from "@/lib/ports/fortune";

const GENERATION_FAILURE_CODES = new Set([
  "READING_GENERATION_FAILED",
  "CALCULATION_FAILED",
]);

export type GenerationFailure = Readonly<{
  // 서버가 환급했다고 알려 줬는지. 오류 응답이면 COMMON 4.8 의 "보상 거래로 복구" 를 따른다
  refunded: boolean;
}>;

export function generationFailureOfResult(
  result: ReadingPurchaseResult,
): GenerationFailure | null {
  if (result.status === "FAILED" || result.status === "REFUNDED") {
    return { refunded: result.refunded || result.status === "REFUNDED" };
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
    return { refunded: true };
  }
  return null;
}
