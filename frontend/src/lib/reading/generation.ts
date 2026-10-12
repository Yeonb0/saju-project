// 구매 결과 판단 (Phase 4 LoadingScene). 근거: Q-38 답 (API_SPEC 8장 "GET /reading-purchases/by-quote/{quoteId}" 상태표 — 화면 분기는 status,
// 버튼 · 안내는 nextAction), PURCHASE-STATUS-WAIT 해제 (2026-10-12), F-06 (만세력 계산 · Liner 문장은 구매 직후 동기 생성, 최종 실패면 서버가 재화를 복구),
// COMMON 4.8 (500 READING_GENERATION_FAILED · CALCULATION_FAILED 는 "환급 · 재시도 안내"), I-05 (새 구매는 새 견적 · 새 키).
// 규칙: FULFILLED + readingId → 완료, FAILED · REFUNDED → nextAction 으로 다시 시도 / 문의 / 보상 대기, 그 밖의 status 는 결과를 아직 모른다.
// 환급 문장은 서버가 REFUNDED + COMPLETED 로 알려 줬을 때만 쓴다 — 오류 code 나 FAILED 만으로 환급을 단정하지 않는다 (Q-38).
import { ApiError } from "@/lib/api/errors";
import type { ReadingPurchaseState } from "@/lib/ports/fortune";

const GENERATION_FAILURE_CODES = new Set([
  "READING_GENERATION_FAILED",
  "CALCULATION_FAILED",
]);

export type GenerationFailure = Readonly<{
  // 서버가 환급 완료(REFUNDED · COMPLETED)로 알려 줬는지 (Q-38)
  refunded: boolean;
  // retry = 새 견적으로 다시 시도, contact_support = 문의 안내 (새 구매를 열지 않는다)
  next: "retry" | "contact_support";
}>;

export type PurchaseOutcome =
  | { kind: "fulfilled"; readingId: string }
  | { kind: "pending" }
  | { kind: "failed"; failure: GenerationFailure };

export function purchaseOutcomeOf(
  state: ReadingPurchaseState,
): PurchaseOutcome {
  if (state.status === "FULFILLED") {
    // readingId 가 없으면 결과로 보내지 않는다 (Q-38)
    return state.readingId !== null
      ? { kind: "fulfilled", readingId: state.readingId }
      : { kind: "pending" };
  }
  if (state.status === "FAILED" || state.status === "REFUNDED") {
    const refunded =
      state.status === "REFUNDED" && state.compensationStatus === "COMPLETED";
    // 보상 진행 중 — 아직 끝나지 않았다
    if (state.nextAction === "WAIT") return { kind: "pending" };
    if (state.nextAction === "RETRY_PURCHASE") {
      return { kind: "failed", failure: { refunded, next: "retry" } };
    }
    // CONTACT_SUPPORT · VIEW_READING · UNKNOWN: 다음 행동을 모르는 실패에 새 구매를 열면 이중 차감 위험 — 문의로 안내
    return { kind: "failed", failure: { refunded, next: "contact_support" } };
  }
  // CREATED · DEBITED · GENERATING · UNKNOWN
  return { kind: "pending" };
}

// 결과 생성 실패 오류 (COMMON 4.8). 이 오류만으로 실패 화면을 그리지 않는다 — 구매 상태 조회 결과로 그린다
export function isGenerationFailureError(error: unknown): boolean {
  return (
    error instanceof ApiError &&
    error.code !== null &&
    GENERATION_FAILURE_CODES.has(error.code)
  );
}
