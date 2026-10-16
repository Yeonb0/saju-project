import { describe, expect, it, vi } from "vitest";
import { createFakePaymentLauncher } from "./paymentLauncher";

// 픽스처일 뿐이며 실제 주문 · 가격과 무관하다
const ORDER = {
  orderId: "33333333-3333-4333-8333-333333333333",
  orderName: "FIXTURE",
  amount: { currency: "KRW", amount: 1111 },
};

describe("가짜 결제창 (MOCK-PORT · DIRECT-PAY)", () => {
  it("P1. direct: /pay/success?flow=direct& 로 시작하고 paymentKey · orderId · amount 를 서버 주문 값 그대로 담는다", async () => {
    const navigate = vi.fn();
    await createFakePaymentLauncher(navigate).requestPayment(ORDER, {
      kind: "direct",
      returnPath: "/suneung",
    });
    const url: string = navigate.mock.calls[0][0];
    expect(url.startsWith("/pay/success?flow=direct&")).toBe(true);
    const params = new URL(url, "http://localhost").searchParams;
    expect(params.get("flow")).toBe("direct");
    expect(params.get("paymentKey")).toBeTruthy();
    expect(params.get("orderId")).toBe(ORDER.orderId);
    expect(params.get("amount")).toBe("1111");
  });

  it("P2. top_up: 지금과 같은 주소 (flow 없음)", async () => {
    const navigate = vi.fn();
    await createFakePaymentLauncher(navigate).requestPayment(ORDER, {
      kind: "top_up",
      retryProductCode: "FIXTURE_TOP_UP_X",
    });
    const url: string = navigate.mock.calls[0][0];
    expect(url.startsWith("/pay/success?paymentKey=")).toBe(true);
    const params = new URL(url, "http://localhost").searchParams;
    expect(params.has("flow")).toBe(false);
    expect(params.get("orderId")).toBe(ORDER.orderId);
    expect(params.get("amount")).toBe("1111");
  });
});
