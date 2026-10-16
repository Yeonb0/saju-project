import { describe, expect, it, vi } from "vitest";
import { ApiContractError, ApiError, ApiNetworkError } from "@/lib/api/errors";
import type {
  DirectPaymentOrder,
  DirectPaymentPort,
} from "@/lib/ports/directPayment";
import {
  createFakeDirectPaymentPort,
  type FakeDirectPaymentScenario,
} from "@/mocks/directPayment";
import { createFakeFortunePort } from "@/mocks/fortune";
import { createFakeWallet } from "@/mocks/wallet";
import { confirmDirectPayment } from "./confirmDirectPayment";
import { POLL_INTERVAL_MS, POLL_LIMIT_MS } from "./confirmTopUp";

// 픽스처일 뿐이며 실제 가격 · 규칙과 무관하다. 결제 키 · 주문 ID 는 임의 값이다 (DIRECT-PAY).
const ORDER_ID = "33333333-3333-4333-8333-333333333333";
const SELECTION = {
  fortuneType: "LOVE" as const,
  productCode: "FIXTURE_LOVE_READING_ONLY",
  personId: "66666666-6666-4666-8666-666666666666",
  counterpartPersonId: null,
  productOption: "READING_ONLY" as const,
  questionKey: "CURRENT_RELATIONSHIP",
  relationType: null,
};

// 실제로 기다리지 않는 시계 — sleep 이 시간을 앞으로 민다
function fakeClock() {
  let t = 0;
  return {
    now: () => t,
    sleep: vi.fn(async (ms: number) => {
      t += ms;
    }),
  };
}

async function run(scenario: FakeDirectPaymentScenario, paidAfterPolls = 2) {
  const fortune = createFakeFortunePort({
    wallet: createFakeWallet(100),
    priceCurrency: "KRW",
  });
  const fake = createFakeDirectPaymentPort({
    fortune,
    scenario,
    paidAfterPolls,
  });
  const port = { confirm: vi.fn(fake.confirm), getOrder: vi.fn(fake.getOrder) };
  const quote = await fortune.createQuote(SELECTION);
  const order = await fake.createOrder({ quoteId: quote.quoteId }, "k-1");
  const ret = {
    paymentKey: "fixture-payment-key",
    orderId: order.orderId,
    amount: String(order.amount.amount),
  };
  const clock = fakeClock();
  const result = await confirmDirectPayment(ret, { port, ...clock });
  return { result, port, clock, ret };
}

function order(status: DirectPaymentOrder["status"]): DirectPaymentOrder {
  return {
    orderId: ORDER_ID,
    productCode: "FIXTURE",
    orderName: "FIXTURE",
    amount: { currency: "KRW", amount: 1 },
    status,
  };
}

const RET = { paymentKey: "pk", orderId: ORDER_ID, amount: "1" };

function stubPort(
  confirm: DirectPaymentPort["confirm"],
  getOrder: DirectPaymentPort["getOrder"] = async () =>
    order("PAYMENT_PENDING"),
) {
  return { confirm: vi.fn(confirm), getOrder: vi.fn(getOrder) };
}

describe("confirmDirectPayment (DIRECT-PAY · CONFIRM-KEY)", () => {
  it("A1. 승인 즉시 PAID → paid, 조회 없음", async () => {
    const { result, port, clock, ret } = await run("paid");
    expect(result).toEqual({ kind: "paid", orderId: ret.orderId });
    expect(port.confirm).toHaveBeenCalledWith(ret);
    expect(port.getOrder).not.toHaveBeenCalled();
    expect(clock.sleep).not.toHaveBeenCalled();
  });

  it("A2. PAYMENT_PENDING 뒤 조회 2번째에 PAID → paid", async () => {
    const { result, port, clock } = await run("pending_then_paid");
    expect(result.kind).toBe("paid");
    expect(port.getOrder).toHaveBeenCalledTimes(2);
    expect(clock.sleep).toHaveBeenCalledTimes(2);
    expect(clock.sleep).toHaveBeenCalledWith(POLL_INTERVAL_MS, undefined);
  });

  it("A3. 계속 PENDING → 30초 뒤 pending (sleep 호출 수로 확인)", async () => {
    const { result, clock } = await run("stuck_pending");
    expect(result.kind).toBe("pending");
    expect(clock.sleep).toHaveBeenCalledTimes(POLL_LIMIT_MS / POLL_INTERVAL_MS);
  });

  it("A4. FAILED · CANCELED → failed", async () => {
    expect((await run("rejected")).result.kind).toBe("failed");
    const port = stubPort(async () => order("CANCELED"));
    expect(
      (await confirmDirectPayment(RET, { port, ...fakeClock() })).kind,
    ).toBe("failed");
  });

  it("A5. REVIEW_REQUIRED · UNKNOWN → review", async () => {
    expect((await run("review_required")).result.kind).toBe("review");
    const port = stubPort(async () => order("UNKNOWN"));
    expect(
      (await confirmDirectPayment(RET, { port, ...fakeClock() })).kind,
    ).toBe("review");
  });

  it("A6. 승인이 ApiNetworkError 로 끊겨도 조회로 확인해 paid", async () => {
    const { result, port } = await run("confirm_lost");
    expect(result.kind).toBe("paid");
    expect(port.confirm).toHaveBeenCalledTimes(1);
    expect(port.getOrder).toHaveBeenCalledTimes(1);
  });

  it("A7. 승인이 401 → login_required", async () => {
    const port = stubPort(async () => {
      throw new ApiError({
        status: 401,
        code: "SESSION_EXPIRED",
        traceId: null,
      });
    });
    expect(await confirmDirectPayment(RET, { port, ...fakeClock() })).toEqual({
      kind: "login_required",
      orderId: ORDER_ID,
    });
  });

  it("A8. ApiContractError 는 던진다", async () => {
    const port = stubPort(async () => {
      throw new ApiContractError(200, "FIXTURE");
    });
    await expect(
      confirmDirectPayment(RET, { port, ...fakeClock() }),
    ).rejects.toBeInstanceOf(ApiContractError);
  });

  it("A9. 조회가 404 → review (다시 결제를 권하지 않는다)", async () => {
    const port = stubPort(
      async () => {
        throw new ApiNetworkError();
      },
      async () => {
        throw new ApiError({
          status: 404,
          code: "RESOURCE_NOT_FOUND",
          traceId: null,
        });
      },
    );
    expect(await confirmDirectPayment(RET, { port, ...fakeClock() })).toEqual({
      kind: "review",
      orderId: ORDER_ID,
    });
  });
});
