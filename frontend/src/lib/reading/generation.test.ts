import { describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api/errors";
import type {
  CompensationStatus,
  PurchaseNextAction,
  ReadingPurchaseState,
  ReadingPurchaseStatus,
} from "@/lib/ports/fortune";
import {
  isGenerationFailureError,
  type PurchaseOutcome,
  purchaseOutcomeOf,
} from "./generation";

// 픽스처일 뿐이며 실제 가격 · 규칙과 무관하다
const state = (
  status: ReadingPurchaseStatus,
  nextAction: PurchaseNextAction,
  compensationStatus: CompensationStatus = "NOT_REQUIRED",
  readingId: string | null = null,
): ReadingPurchaseState => ({
  purchaseId: "p",
  readingId,
  status,
  compensationStatus,
  nextAction,
});

describe("구매 결과 판단 (Q-38 상태표 · F-06 · COMMON 4.8)", () => {
  it("FULFILLED + readingId 는 완료, readingId 가 없으면 처리 중", () => {
    expect(
      purchaseOutcomeOf(
        state("FULFILLED", "VIEW_READING", "NOT_REQUIRED", "r"),
      ),
    ).toEqual({ kind: "fulfilled", readingId: "r" });
    expect(purchaseOutcomeOf(state("FULFILLED", "VIEW_READING"))).toEqual({
      kind: "pending",
    });
  });

  it.each<
    [
      ReadingPurchaseStatus,
      PurchaseNextAction,
      CompensationStatus,
      PurchaseOutcome,
    ]
  >([
    // 다시 시도 — 환급 문장은 REFUNDED + COMPLETED 만
    [
      "FAILED",
      "RETRY_PURCHASE",
      "NOT_REQUIRED",
      { kind: "failed", failure: { refunded: false, next: "retry" } },
    ],
    [
      "REFUNDED",
      "RETRY_PURCHASE",
      "COMPLETED",
      { kind: "failed", failure: { refunded: true, next: "retry" } },
    ],
    [
      "REFUNDED",
      "RETRY_PURCHASE",
      "PENDING",
      { kind: "failed", failure: { refunded: false, next: "retry" } },
    ],
    [
      "FAILED",
      "RETRY_PURCHASE",
      "COMPLETED",
      { kind: "failed", failure: { refunded: false, next: "retry" } },
    ],
    // 보상 진행 중 — 아직 끝나지 않았다
    ["FAILED", "WAIT", "PENDING", { kind: "pending" }],
    ["REFUNDED", "WAIT", "PENDING", { kind: "pending" }],
    // 문의 · 다음 행동을 모르는 실패 — 새 구매를 열지 않는다
    [
      "FAILED",
      "CONTACT_SUPPORT",
      "REVIEW_REQUIRED",
      { kind: "failed", failure: { refunded: false, next: "contact_support" } },
    ],
    [
      "REFUNDED",
      "CONTACT_SUPPORT",
      "COMPLETED",
      { kind: "failed", failure: { refunded: true, next: "contact_support" } },
    ],
    [
      "FAILED",
      "VIEW_READING",
      "NOT_REQUIRED",
      { kind: "failed", failure: { refunded: false, next: "contact_support" } },
    ],
    [
      "FAILED",
      "UNKNOWN",
      "NOT_REQUIRED",
      { kind: "failed", failure: { refunded: false, next: "contact_support" } },
    ],
    [
      "REFUNDED",
      "UNKNOWN",
      "UNKNOWN",
      { kind: "failed", failure: { refunded: false, next: "contact_support" } },
    ],
  ])("%s · %s · %s", (status, nextAction, compensationStatus, expected) => {
    expect(
      purchaseOutcomeOf(state(status, nextAction, compensationStatus)),
    ).toEqual(expected);
  });

  it.each<ReadingPurchaseStatus>([
    "CREATED",
    "DEBITED",
    "GENERATING",
    "UNKNOWN",
  ])("%s 는 어떤 nextAction 이어도 처리 중", (status) => {
    for (const nextAction of [
      "WAIT",
      "VIEW_READING",
      "RETRY_PURCHASE",
      "CONTACT_SUPPORT",
      "UNKNOWN",
    ] as const) {
      expect(purchaseOutcomeOf(state(status, nextAction))).toEqual({
        kind: "pending",
      });
    }
  });
});

describe("생성 실패 오류 (COMMON 4.8)", () => {
  const err = (code: string | null) =>
    new ApiError({ status: 500, code, traceId: null });

  it("READING_GENERATION_FAILED · CALCULATION_FAILED 만 true", () => {
    expect(isGenerationFailureError(err("READING_GENERATION_FAILED"))).toBe(
      true,
    );
    expect(isGenerationFailureError(err("CALCULATION_FAILED"))).toBe(true);
    expect(isGenerationFailureError(err("INTERNAL_ERROR"))).toBe(false);
    expect(isGenerationFailureError(err(null))).toBe(false);
    expect(isGenerationFailureError(new Error("x"))).toBe(false);
  });
});
