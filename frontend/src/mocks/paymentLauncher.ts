// 결제창 가짜 구현 — 개발 서버 · Vercel 미리보기 전용 (docs/FRONTEND.md 1-2, MOCK-PORT).
// 토스 결제 성공 복귀처럼 /pay/success?paymentKey&orderId&amount 로 보낸다.
// 페이지를 새로 불러오면 가짜 서버(메모리)가 비므로, 같은 탭 안의 이동(navigate)으로 보낸다.
// paymentKey 는 픽스처일 뿐이며 실제 결제 키 형식과 무관하다.
import type { PaymentLauncher } from "@/lib/ports/paymentLauncher";

export function createFakePaymentLauncher(
  navigate: (url: string) => void,
): PaymentLauncher {
  return {
    async requestPayment(order) {
      const params = new URLSearchParams({
        paymentKey: `fixture-payment-${order.orderId}`,
        orderId: order.orderId,
        amount: String(order.amount.amount),
      });
      navigate(`/pay/success?${params.toString()}`);
    },
  };
}
