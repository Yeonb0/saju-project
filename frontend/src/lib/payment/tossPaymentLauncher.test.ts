import { describe, expect, it, vi } from "vitest";
import { PaymentCanceledError } from "@/lib/ports/paymentLauncher";
import type { TopUpOrder } from "@/lib/ports/topUp";
import {
  createTossPaymentLauncher,
  type TossSdkModule,
} from "./tossPaymentLauncher";

// 키 · 주문 · 금액은 모두 픽스처일 뿐이며 실제 키 · 가격 · 규칙과 무관하다.
const CLIENT_KEY = "test_gck_fixture";
const ORIGIN = "https://fixture.example";
const ORDER: TopUpOrder = {
  orderId: "33333333-3333-4333-8333-333333333333",
  orderName: "FIXTURE 주문",
  amount: { currency: "KRW", amount: 2222 },
  status: "PAYMENT_PENDING",
};

type Handlers = {
  paymentRequest?: () => Promise<void>;
  cancel?: () => Promise<void>;
};

// 호출 순서를 calls 에 기록하는 가짜 SDK
function fakeSdk(
  options: {
    requestPayment?: () => Promise<unknown>;
    destroy?: () => Promise<void>;
    renderPaymentWindow?: () => Promise<void>;
  } = {},
) {
  const calls: string[] = [];
  const handlers: Handlers = {};
  const args: Record<string, unknown> = {};
  const destroy = vi.fn(async () => {
    calls.push("destroy");
    await options.destroy?.();
  });
  const requestPayment = vi.fn(async (request: unknown) => {
    calls.push("requestPayment");
    args.requestPayment = request;
    return options.requestPayment?.();
  });
  const paymentWindow = {
    on(eventName: "paymentRequest" | "cancel", callback: () => Promise<void>) {
      handlers[eventName] = callback;
    },
    destroy,
  };
  const widgets = {
    async setAmount(amount: unknown) {
      calls.push("setAmount");
      args.setAmount = amount;
    },
    async renderPaymentWindow(params: unknown) {
      calls.push("renderPaymentWindow");
      args.renderPaymentWindow = params;
      await options.renderPaymentWindow?.();
      return paymentWindow;
    },
    requestPayment,
  };
  const loadTossPayments = vi.fn(async (clientKey: string) => {
    calls.push("loadTossPayments");
    args.loadTossPayments = clientKey;
    return {
      widgets(params: { customerKey: string }) {
        calls.push("widgets");
        args.widgets = params;
        return widgets;
      },
    };
  });
  const sdk: TossSdkModule = {
    ANONYMOUS: "@@FIXTURE_ANONYMOUS",
    loadTossPayments,
  };
  const loadSdk = vi.fn(async () => sdk);
  return { calls, handlers, args, destroy, requestPayment, loadSdk };
}

function launcher(
  loadSdk: () => Promise<TossSdkModule>,
  clientKey: string | undefined,
) {
  return createTossPaymentLauncher({
    clientKey,
    origin: () => ORIGIN,
    loadSdk,
  });
}

// 이벤트를 걸 때까지 기다린다
async function waitForHandlers(handlers: Handlers) {
  await vi.waitFor(() => {
    expect(handlers.paymentRequest).toBeDefined();
    expect(handlers.cancel).toBeDefined();
  });
}

describe("토스 결제창 (TOSS-SDK)", () => {
  it("a. 호출 순서: loadTossPayments → widgets(ANONYMOUS) → setAmount(KRW, 서버 금액) → renderPaymentWindow(orderName)", async () => {
    const sdk = fakeSdk();
    void launcher(sdk.loadSdk, CLIENT_KEY).requestPayment(ORDER);
    await waitForHandlers(sdk.handlers);
    expect(sdk.calls).toEqual([
      "loadTossPayments",
      "widgets",
      "setAmount",
      "renderPaymentWindow",
    ]);
    expect(sdk.args.loadTossPayments).toBe(CLIENT_KEY);
    expect(sdk.args.widgets).toEqual({ customerKey: "@@FIXTURE_ANONYMOUS" });
    expect(sdk.args.setAmount).toEqual({ currency: "KRW", value: 2222 });
    expect(sdk.args.renderPaymentWindow).toEqual({ orderName: "FIXTURE 주문" });
  });

  it("b. paymentRequest 이벤트에서 정확한 인자로 requestPayment", async () => {
    const sdk = fakeSdk();
    void launcher(sdk.loadSdk, CLIENT_KEY).requestPayment(ORDER);
    await waitForHandlers(sdk.handlers);
    await sdk.handlers.paymentRequest?.();
    expect(sdk.requestPayment).toHaveBeenCalledTimes(1);
    expect(sdk.args.requestPayment).toEqual({
      orderId: ORDER.orderId,
      orderName: "FIXTURE 주문",
      successUrl: `${ORIGIN}/pay/success`,
      failUrl: `${ORIGIN}/pay/fail`,
    });
    expect(Object.keys(sdk.args.requestPayment as object).sort()).toEqual([
      "failUrl",
      "orderId",
      "orderName",
      "successUrl",
    ]);
  });

  it("b. 결제 요청이 리다이렉트되면 Promise 는 끝나지 않는다", async () => {
    const sdk = fakeSdk();
    const done = vi.fn();
    launcher(sdk.loadSdk, CLIENT_KEY).requestPayment(ORDER).then(done, done);
    await waitForHandlers(sdk.handlers);
    await sdk.handlers.paymentRequest?.();
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(done).not.toHaveBeenCalled();
  });

  it("c. cancel 이벤트면 destroy 를 부르고 PaymentCanceledError 로 reject", async () => {
    const sdk = fakeSdk();
    const result = launcher(sdk.loadSdk, CLIENT_KEY).requestPayment(ORDER);
    const settled = result.catch((error: unknown) => error);
    await waitForHandlers(sdk.handlers);
    await sdk.handlers.cancel?.();
    const error = await settled;
    expect(sdk.destroy).toHaveBeenCalledTimes(1);
    expect(error).toBeInstanceOf(PaymentCanceledError);
    expect((error as Error).name).toBe("PaymentCanceledError");
  });

  it("c. cancel 중 destroy 가 던지면 그 오류로 reject (삼키지 않는다)", async () => {
    const failure = new Error("fixture destroy failure");
    const sdk = fakeSdk({
      destroy: async () => {
        throw failure;
      },
    });
    const settled = launcher(sdk.loadSdk, CLIENT_KEY)
      .requestPayment(ORDER)
      .catch((error: unknown) => error);
    await waitForHandlers(sdk.handlers);
    await sdk.handlers.cancel?.();
    expect(await settled).toBe(failure);
  });

  it("d. requestPayment 가 거부되면 destroy 를 부르고 그 오류로 reject", async () => {
    const failure = new Error("fixture request failure");
    const sdk = fakeSdk({
      requestPayment: async () => {
        throw failure;
      },
    });
    const settled = launcher(sdk.loadSdk, CLIENT_KEY)
      .requestPayment(ORDER)
      .catch((error: unknown) => error);
    await waitForHandlers(sdk.handlers);
    await sdk.handlers.paymentRequest?.();
    expect(await settled).toBe(failure);
    expect(sdk.destroy).toHaveBeenCalledTimes(1);
  });

  it.each([
    ["undefined", undefined],
    ["빈 문자열", ""],
    ["ck 키", "test_ck_fixture"],
    ["시크릿 키", "test_gsk_fixture"],
  ])("e. clientKey %s 이면 SDK 를 부르지 않고 던지며 메시지에 키가 없다", async (_name, clientKey) => {
    const sdk = fakeSdk();
    const error = await launcher(sdk.loadSdk, clientKey)
      .requestPayment(ORDER)
      .catch((e: unknown) => e);
    expect(error).toBeInstanceOf(Error);
    expect(sdk.loadSdk).not.toHaveBeenCalled();
    if (clientKey) expect((error as Error).message).not.toContain(clientKey);
  });

  it("f. 통화가 KRW 가 아니면 SDK 를 부르지 않고 던진다", async () => {
    const sdk = fakeSdk();
    await expect(
      launcher(sdk.loadSdk, CLIENT_KEY).requestPayment({
        ...ORDER,
        amount: { currency: "TURTLE_SHELL", amount: 2222 },
      }),
    ).rejects.toThrow();
    expect(sdk.loadSdk).not.toHaveBeenCalled();
  });

  it("g. renderPaymentWindow 가 던지면 그 오류로 reject", async () => {
    const failure = new Error("fixture render failure");
    const sdk = fakeSdk({
      renderPaymentWindow: async () => {
        throw failure;
      },
    });
    await expect(
      launcher(sdk.loadSdk, CLIENT_KEY).requestPayment(ORDER),
    ).rejects.toBe(failure);
  });
});
