// 토스 결제창 진짜 구현 — V2 결제창형 (widgets → renderPaymentWindow). 근거: TOSS-SDK (@tosspayments/tosspayments-sdk),
// TOSS-ANONYMOUS (비회원 키 ANONYMOUS — 구매자 식별 정보를 SDK 에 넘기지 않는다), TOSS-LAUNCHER-SWITCH (진짜 모드 또는 가짜 모드의 스위치로 고른다, ports/index.ts),
// P-08 · P-09 (orderId · 금액은 서버 주문 값 그대로 — 계산하지 않는다), Q-25 (결제수단 · variantKey 는 지정하지 않는다).
// 키 짝 규칙 (backend/docs/FE_TOP_UP_DELIVERY_20261011.md): FE 는 gck 클라이언트 키만 쓴다. ck · 시크릿 키는 받지 않는다. 키 값은 오류 메시지에 넣지 않는다.
// 개인정보: customerEmail · customerName · customerMobilePhone 을 보내지 않는다. windowTarget 도 지정하지 않는다.
// SDK 는 브라우저에서만 쓴다 — 모듈 맨 위에서 import 하지 않고 requestPayment 안에서 동적 import 한다 (정적 렌더 · 빌드 보호). 테스트는 loadSdk 로 주입한다.
// 결제 요청은 리다이렉트라 성공하면 이 Promise 는 끝나지 않는다 — 구매자는 /pay/success · /pay/fail 로 돌아온다.
import {
  PaymentCanceledError,
  type PaymentLauncher,
} from "@/lib/ports/paymentLauncher";

// 이 파일이 쓰는 SDK 부분만 적은 모양 — 진짜 모듈이 이 모양을 만족해야 typecheck 가 통과한다
type PaymentWindowLike = {
  on(eventName: "paymentRequest", callback: () => Promise<void>): void;
  on(eventName: "cancel", callback: () => Promise<void>): void;
  destroy(): Promise<void>;
};

type WidgetsLike = {
  setAmount(amount: { currency: "KRW"; value: number }): Promise<void>;
  renderPaymentWindow(params: {
    orderName: string;
  }): Promise<PaymentWindowLike>;
  requestPayment(request: {
    orderId: string;
    orderName: string;
    successUrl: string;
    failUrl: string;
  }): Promise<unknown>;
};

export type TossSdkModule = {
  ANONYMOUS: string;
  loadTossPayments(clientKey: string): Promise<{
    widgets(params: { customerKey: string }): WidgetsLike;
  }>;
};

const CLIENT_KEY_PATTERN = /^(test|live)_gck_/;

// 형 변환(as) 없이 대입한다 — 실제 SDK 모듈이 TossSdkModule 모양을 만족하지 않으면 typecheck 가 깨진다
const loadRealSdk = async (): Promise<TossSdkModule> => {
  const sdk = await import("@tosspayments/tosspayments-sdk");
  return sdk;
};

export function createTossPaymentLauncher(deps: {
  clientKey: string | undefined;
  origin: () => string;
  loadSdk?: () => Promise<TossSdkModule>;
}): PaymentLauncher {
  const loadSdk = deps.loadSdk ?? loadRealSdk;
  return {
    async requestPayment(order) {
      const { clientKey } = deps;
      // 키 값은 메시지에 넣지 않는다
      if (clientKey === undefined || !CLIENT_KEY_PATTERN.test(clientKey)) {
        throw new Error(
          "토스 클라이언트 키(gck)가 없거나 형식이 다르다 (TOSS-SDK)",
        );
      }
      if (order.amount.currency !== "KRW") {
        throw new Error("토스 일반결제는 KRW 주문만 연다 (TOSS-SDK)");
      }

      const { loadTossPayments, ANONYMOUS } = await loadSdk();
      const toss = await loadTossPayments(clientKey);
      const widgets = toss.widgets({ customerKey: ANONYMOUS });
      // 금액은 서버 주문 값 그대로, renderPaymentWindow 전에 설정한다 (P-08 · P-09)
      await widgets.setAmount({
        currency: "KRW",
        value: order.amount.amount,
      });
      const paymentWindow = await widgets.renderPaymentWindow({
        orderName: order.orderName,
      });

      const origin = deps.origin();
      return new Promise<void>((_resolve, reject) => {
        paymentWindow.on("paymentRequest", async () => {
          try {
            await widgets.requestPayment({
              orderId: order.orderId,
              orderName: order.orderName,
              successUrl: `${origin}/pay/success`,
              failUrl: `${origin}/pay/fail`,
            });
          } catch (error) {
            // 정리 실패보다 결제 요청 실패 원인이 더 중요하다 — 원래 오류로 끝낸다
            try {
              await paymentWindow.destroy();
            } catch {
              // 원래 오류를 우선한다
            }
            reject(error);
          }
        });
        paymentWindow.on("cancel", async () => {
          try {
            await paymentWindow.destroy();
          } catch (error) {
            // destroy 오류는 삼키지 않는다
            reject(error);
            return;
          }
          reject(new PaymentCanceledError());
        });
      });
    },
  };
}
