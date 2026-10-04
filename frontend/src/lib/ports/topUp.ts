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

// API_SPEC 6장 초안 — CREATED · PAYMENT_PENDING · PAID · CREDITED · FAILED · CANCELED · REFUNDED
export const TOP_UP_ORDER_STATUSES = [
  "CREATED",
  "PAYMENT_PENDING",
  "PAID",
  "CREDITED",
  "FAILED",
  "CANCELED",
  "REFUNDED",
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

// 승인 · 조회 결과. 충전 완료는 status === "CREDITED" 일 때만 (TOPUP-DONE).
// processing 은 Q-17 계약 반영 전 초안이다. walletBalance 는 서버가 준 경우에만 숫자.
export type TopUpOrderState = Readonly<{
  orderId: string;
  status: TopUpOrderStatus;
  processing: boolean;
  walletBalance: number | null;
}>;

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
};
