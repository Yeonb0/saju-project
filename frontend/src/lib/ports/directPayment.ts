// 직접결제(건별 원화 결제) 포트 모델 — 등껍질 차감 대신 운세 한 건을 원화로 바로 결제한다 (DIRECT-PAY, 2026-10-16 사용자 결정).
// 진짜 구현 src/lib/api/adapters/directPayment.ts (PR #49 c2a1392 기준). TODO(OPENAPI-FE-SYNC): 공유 OpenAPI 반영 후 생성 타입과 대조 (MOCK-PORT). 이름은 BE 전달본 DIRECT_PAYMENT_FRONTEND_HANDOFF 응답을 따른다.
// 금액 · 상태는 서버 값만 쓴다 — 화면이 주문 금액을 계산하지 않는다 (P-09).
import type { FortuneSelection, ReadingPurchaseStatus } from "./fortune";
import type { KrwPrice, PaymentReturn } from "./topUp";

// 주문 상태 (전달본). 모르는 값은 UNKNOWN
export const DIRECT_PAYMENT_STATUSES = [
  "PAYMENT_PENDING",
  "PAID",
  "FAILED",
  "CANCELED",
  "REVIEW_REQUIRED",
] as const;

export type DirectPaymentStatus =
  | (typeof DIRECT_PAYMENT_STATUSES)[number]
  | "UNKNOWN";

export function toDirectPaymentStatus(raw: string): DirectPaymentStatus {
  return (DIRECT_PAYMENT_STATUSES as readonly string[]).includes(raw)
    ? (raw as DirectPaymentStatus)
    : "UNKNOWN";
}

// 전달본 응답 orderId · productCode · orderName · amount · currency · status (amount + currency 는 KrwPrice 하나)
export type DirectPaymentOrder = Readonly<{
  orderId: string;
  productCode: string;
  orderName: string;
  amount: KrwPrice;
  status: DirectPaymentStatus;
}>;

// 결제 뒤 운세 생성 응답. 서버 응답에는 nextAction · compensationStatus 가 없다 —
// 완료가 아니면 화면은 기존 getPurchaseStatus(quoteId) 로 다음 행동을 본다. readingId null 을 완료로 바꾸지 않는다 (Q-38)
export type DirectReadingFulfillment = Readonly<{
  purchaseId: string;
  readingId: string | null;
  status: ReadingPurchaseStatus;
}>;

// 결제 흐름: createOrder → 결제창 → confirm(복귀 값 그대로) → 확인 · getOrder → PAID 일 때만 fulfillReading.
// REVIEW_REQUIRED 주문은 새 주문으로 덮지 않는다 — 안내만 한다 (BE 전달본 10/16). 결과가 불명확하면(ApiNetworkError)
// 새 주문을 만들지 않고 getOrder 로 조회한다 (I-05).
export type DirectPaymentPort = {
  // 수량은 1 고정 (선물만 여러 명 — 이번 범위 밖). idempotencyKey 는 구매 의도(견적) 하나에 하나 (I-05)
  createOrder(
    input: Readonly<{ quoteId: string }>,
    idempotencyKey: string,
  ): Promise<DirectPaymentOrder>;
  // 승인 키는 구현이 주문 ID 로 만든다 — 승인 재전송은 같은 키 (CONFIRM-KEY)
  confirm(ret: PaymentReturn): Promise<DirectPaymentOrder>;
  getOrder(orderId: string): Promise<DirectPaymentOrder>;
  // 주문이 PAID 일 때만 부른다. 결제 뒤 생성은 서버가 견적 스냅숏을 쓴다 (견적 만료와 무관)
  fulfillReading(
    input: Readonly<{
      orderId: string;
      quoteId: string;
      selection: FortuneSelection;
    }>,
    idempotencyKey: string,
  ): Promise<DirectReadingFulfillment>;
};
