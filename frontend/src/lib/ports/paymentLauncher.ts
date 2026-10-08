// 결제창 열기 포트. 근거: docs/FRONTEND.md 1-2 (MOCK-PORT), PG-3.
// 진짜 구현은 토스 SDK(@tosspayments/tosspayments-sdk, TOSS-SDK) 결제창을 연다 — 테스트 클라이언트 키(R-09) 수령 후.
// 가짜 구현은 결제창 없이 /pay/success 복귀를 흉내 낸다.
// 결제 수단 · 동의 체크는 Q-25 확정 전이라 이 포트에 넣지 않는다.
import type { TopUpOrder } from "./topUp";

export type PaymentLauncher = {
  // 서버 주문 값(orderId · amount)만 쓴다. 금액을 계산하지 않는다 (P-08 · P-09)
  requestPayment(order: TopUpOrder): Promise<void>;
};
