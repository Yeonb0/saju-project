// 결제창 열기 포트. 근거: docs/FRONTEND.md 1-2 (MOCK-PORT), PG-3.
// 진짜 구현은 토스 V2 결제창형(TOSS-SDK, src/lib/payment/tossPaymentLauncher.ts)이다 — 리다이렉트로 /pay/success · /pay/fail 에 돌아온다.
// 가짜 구현은 결제창 없이 /pay/success 복귀를 흉내 낸다.
// 결제 수단 · 동의 체크는 Q-25 확정 전이라 이 포트에 넣지 않는다.
import type { TopUpOrder } from "./topUp";

// 구매자가 결제창을 닫은 경우. 오류 화면 대상이 아니다 — 화면은 결제하기 버튼을 다시 연다.
export class PaymentCanceledError extends Error {
  constructor() {
    super("구매자가 결제창을 닫았다");
    this.name = "PaymentCanceledError";
  }
}

export type PaymentLauncher = {
  // 서버 주문 값(orderId · amount)만 쓴다. 금액을 계산하지 않는다 (P-08 · P-09)
  // retryProductCode: 주문을 만든 상품 코드 — 실패 복귀(failUrl)에만 붙여 다시 결제하기가 상품을 유지한다 (TOPUP-RETRY-URL)
  requestPayment(
    order: TopUpOrder,
    options: Readonly<{ retryProductCode: string }>,
  ): Promise<void>;
};
