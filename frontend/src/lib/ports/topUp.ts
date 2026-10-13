// 충전 포트 — 화면(/wallet · /pay/success)이 부르는 함수 모양. 근거: docs/FRONTEND.md 1-2 (MOCK-PORT), PG-3.
// 이 모델은 FE 모델이다. 백엔드 타입이 아니며, 진짜 구현(adapters)이 OpenAPI 생성 타입을 이 모양으로 바꾼다.
// 상태 이름은 docs/API_SPEC.md 6장 초안을 따른다 (초안 — OpenAPI 수령 후 대조). 모르는 상태는 UNKNOWN.
// 금액 · 수량은 서버(또는 가짜 구현)가 준 값만 표시한다. 화면에서 계산하지 않는다 (P-03 · P-09).
// 실패는 src/lib/api/errors.ts 의 ApiError · ApiNetworkError · ApiContractError 로 던진다 (가짜도 같다).

export type Money = Readonly<{ currency: string; amount: number }>;

export type Wallet = Readonly<{ currency: string; balance: number }>;

export type TopUpProduct = Readonly<{
  code: string;
  price: Money;
  paidAmount: number;
  bonusAmount: number;
  creditedAmount: number;
  active: boolean;
}>;

// OpenAPI OrderResponse.status — 알려진 처리 중 상태도 UNKNOWN 으로 낮추지 않는다.
export const TOP_UP_ORDER_STATUSES = [
  "CREATED",
  "PAYMENT_PENDING",
  "PAID",
  "CREDITED",
  "FAILED",
  "CANCELED",
  "REFUNDED",
  "REFUND_PENDING",
] as const;

export type TopUpOrderStatus =
  | (typeof TOP_UP_ORDER_STATUSES)[number]
  | "UNKNOWN";

export function toTopUpOrderStatus(raw: string): TopUpOrderStatus {
  return (TOP_UP_ORDER_STATUSES as readonly string[]).includes(raw)
    ? (raw as TopUpOrderStatus)
    : "UNKNOWN";
}

// 결제창을 열 때 쓰는 서버 주문. orderId · amount 는 서버 값 그대로 (P-08)
export type TopUpOrder = Readonly<{
  orderId: string;
  orderName: string;
  amount: Money;
  status: TopUpOrderStatus;
}>;

// 서버가 알려주는 다음 행동 (OpenAPI OrderResponse.nextAction, Q-17 해결 PR #19). 모르는 값은 UNKNOWN.
export const TOP_UP_NEXT_ACTIONS = [
  "NONE",
  "POLL",
  "VIEW_WALLET",
  "CONTACT_SUPPORT",
  "RETRY_PAYMENT",
] as const;

export type TopUpNextAction = (typeof TOP_UP_NEXT_ACTIONS)[number] | "UNKNOWN";

export function toTopUpNextAction(raw: string): TopUpNextAction {
  return (TOP_UP_NEXT_ACTIONS as readonly string[]).includes(raw)
    ? (raw as TopUpNextAction)
    : "UNKNOWN";
}

// 승인 · 조회 결과. 충전 완료는 status === "CREDITED" 일 때만 (TOPUP-DONE). 근거: Q-17 해결 (PR #19).
// walletBalance 는 지급 당시 잔액 snapshot 이다 — 현재 잔액은 getWallet. grantedShellAmount 는 CREDITED 일 때 실제 지급량 (서버 값만, 아니면 null).
// null 을 0 으로 바꾸지 않는다. failureCode 는 포트에 넣지 않는다 (TODO(Q-33): 실패 사유 문구 미정, 토스 원문 비노출).
export type TopUpOrderState = Readonly<{
  orderId: string;
  status: TopUpOrderStatus;
  processing: boolean;
  walletBalance: number | null;
  grantedShellAmount: number | null;
  nextAction: TopUpNextAction;
}>;

export const CASH_REFUND_STATES = [
  "RESERVED",
  "IN_FLIGHT",
  "RETRY",
  "REFUNDED",
  "REVIEW_REQUIRED",
  "REJECTED",
] as const;

export type CashRefundState = (typeof CASH_REFUND_STATES)[number] | "UNKNOWN";

export const CASH_REFUND_NEXT_ACTIONS = [
  "POLL",
  "NONE",
  "CONTACT_SUPPORT",
] as const;

export type CashRefundNextAction =
  | (typeof CASH_REFUND_NEXT_ACTIONS)[number]
  | "UNKNOWN";

export type CashRefund = Readonly<{
  refundId: string;
  orderId: string;
  state: CashRefundState;
  amount: number;
  nextAction: CashRefundNextAction;
  completedAt: string | null;
}>;

export function toCashRefundState(raw: string): CashRefundState {
  return (CASH_REFUND_STATES as readonly string[]).includes(raw)
    ? (raw as CashRefundState)
    : "UNKNOWN";
}

export function toCashRefundNextAction(raw: string): CashRefundNextAction {
  return (CASH_REFUND_NEXT_ACTIONS as readonly string[]).includes(raw)
    ? (raw as CashRefundNextAction)
    : "UNKNOWN";
}

// PG 복귀 쿼리 그대로. 계산 · 변환 없이 승인 요청에 넘긴다 (CLAUDE.md 원화 결제).
export type PaymentReturn = Readonly<{
  paymentKey: string;
  orderId: string;
  amount: string;
}>;

export type TopUpPort = {
  getWallet(): Promise<Wallet>;
  listTopUpProducts(): Promise<readonly TopUpProduct[]>;
  // idempotencyKey 는 구매 의도 하나에 하나 — 재시도 · 새로고침에도 같은 키 (I-05)
  createOrder(
    input: Readonly<{ productCode: string }>,
    idempotencyKey: string,
  ): Promise<TopUpOrder>;
  // 키는 구현이 주문 ID 로 만든다 (CONFIRM-KEY, createOrderBoundCommand)
  confirm(ret: PaymentReturn): Promise<TopUpOrderState>;
  getOrder(orderId: string): Promise<TopUpOrderState>;
  requestRefund(orderId: string, idempotencyKey: string): Promise<CashRefund>;
  getRefund(orderId: string): Promise<CashRefund>;
};
