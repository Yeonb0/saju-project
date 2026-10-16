// 결제창 열기 포트. 근거: docs/FRONTEND.md 1-2 (MOCK-PORT), PG-3.
// 진짜 구현은 토스 V2 결제창형(TOSS-SDK, src/lib/payment/tossPaymentLauncher.ts)이다 — 리다이렉트로 /pay/success · /pay/fail 에 돌아온다.
// 가짜 구현은 결제창 없이 /pay/success 복귀를 흉내 낸다.
// 결제 수단 · 동의 체크는 Q-25 확정 전이라 이 포트에 넣지 않는다.
import type { Money } from "./topUp";

// 구매자가 결제창을 닫은 경우. 오류 화면 대상이 아니다 — 화면은 결제하기 버튼을 다시 연다.
export class PaymentCanceledError extends Error {
  constructor() {
    super("구매자가 결제창을 닫았다");
    this.name = "PaymentCanceledError";
  }
}

// 결제창에 넘기는 서버 주문 값. TopUpOrder · DirectPaymentOrder 둘 다 이 구조에 맞는다 (DIRECT-PAY)
export type PaymentOrder = Readonly<{
  orderId: string;
  orderName: string;
  amount: Money;
}>;

// 결제창에서 돌아올 곳. 충전은 실패 복귀에 상품 코드를 붙이고(TOPUP-RETRY-URL), 직접결제는 flow=direct 와 돌아갈 앞 화면 경로를 붙인다 (DIRECT-PAY)
export type PaymentReturnRoute =
  | Readonly<{ kind: "top_up"; retryProductCode: string }>
  | Readonly<{ kind: "direct"; returnPath: string }>;

export type PaymentLauncher = {
  // 서버 주문 값(orderId · amount)만 쓴다. 금액을 계산하지 않는다 (P-08 · P-09)
  requestPayment(order: PaymentOrder, route: PaymentReturnRoute): Promise<void>;
};
