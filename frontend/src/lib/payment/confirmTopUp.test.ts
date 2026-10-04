import { describe, expect, it, vi } from "vitest";
import { ApiContractError, ApiError, ApiNetworkError } from "@/lib/api/errors";
import type { TopUpOrderState, TopUpPort } from "@/lib/ports/topUp";
import { createFakeTopUpPort, type FakeTopUpScenario } from "@/mocks/topUp";
import {
  confirmTopUp,
  POLL_INTERVAL_MS,
  POLL_LIMIT_MS,
  pollTopUpOrder,
  readPaymentReturn,
} from "./confirmTopUp";

// 픽스처일 뿐이며 실제 가격 · 규칙과 무관하다. 키 · 결제 키 · 주문 ID 는 임의 값이다.
const KEY = "11111111-1111-4111-8111-111111111111";
const ORDER_ID = "33333333-3333-4333-8333-333333333333";

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

async function run(scenario: FakeTopUpScenario, creditAfterPolls = 2) {
  const fake = createFakeTopUpPort({ scenario, creditAfterPolls });
  const port = {
    confirm: vi.fn(fake.confirm),
    getOrder: vi.fn(fake.getOrder),
  };
  const order = await fake.createOrder(
    { productCode: "FIXTURE_TOP_UP_B" },
    KEY,
  );
  const ret = {
    paymentKey: "fixture-payment-key",
    orderId: order.orderId,
    amount: String(order.amount.amount),
  };
  const clock = fakeClock();
  const result = await confirmTopUp(ret, { port, ...clock });
  return { result, port, clock, ret };
}

function state(status: TopUpOrderState["status"]): TopUpOrderState {
  return {
    orderId: ORDER_ID,
    status,
    processing: status === "PAID",
    walletBalance: null,
  };
}

describe("readPaymentReturn", () => {
  it("세 값을 그대로 읽는다 (변환 · 계산 없음)", () => {
    const params = new URLSearchParams(
      `paymentKey=pk&orderId=${ORDER_ID}&amount=05000`,
    );
    expect(readPaymentReturn(params)).toEqual({
      paymentKey: "pk",
      orderId: ORDER_ID,
      amount: "05000",
    });
  });

  it("하나라도 없으면 null", () => {
    expect(
      readPaymentReturn(new URLSearchParams(`orderId=${ORDER_ID}&amount=1`)),
    ).toBeNull();
  });
});

describe("confirmTopUp (TOPUP-DONE)", () => {
  it("승인 응답이 CREDITED 면 바로 완료, 조회하지 않는다", async () => {
    const { result, port } = await run("credited");
    expect(result.kind).toBe("credited");
    expect(port.getOrder).not.toHaveBeenCalled();
  });

  it("PAID(처리 중)는 완료가 아니다 — CREDITED 가 될 때까지 조회", async () => {
    const { result, port, ret } = await run("paid_then_credited", 3);
    expect(result).toMatchObject({ kind: "credited", orderId: ret.orderId });
    expect(port.getOrder).toHaveBeenCalledTimes(3);
  });

  it("승인 값은 PG 복귀 값 그대로 한 번만 보낸다", async () => {
    const { port, ret } = await run("paid_then_credited");
    expect(port.confirm).toHaveBeenCalledTimes(1);
    expect(port.confirm).toHaveBeenCalledWith(ret);
  });

  it("30초 안에 확정되지 않으면 pending — 2초 간격 15번 조회, 승인을 다시 보내지 않는다", async () => {
    const { result, port, clock } = await run("stuck_paid");
    expect(result.kind).toBe("pending");
    expect(port.getOrder).toHaveBeenCalledTimes(
      POLL_LIMIT_MS / POLL_INTERVAL_MS,
    );
    expect(port.confirm).toHaveBeenCalledTimes(1);
    expect(clock.now()).toBe(POLL_LIMIT_MS);
    for (const call of clock.sleep.mock.calls)
      expect(call[0]).toBe(POLL_INTERVAL_MS);
  });

  it("승인 응답이 끊겨도 실패로 표시하지 않고 조회로 확인한다", async () => {
    const { result, port } = await run("confirm_lost");
    expect(result.kind).toBe("credited");
    expect(port.confirm).toHaveBeenCalledTimes(1);
  });

  it("409 IDEMPOTENCY_REQUEST_PROCESSING 은 조회로 확인한다", async () => {
    const { result } = await run("processing_409");
    expect(result.kind).toBe("credited");
  });

  it("422 PAYMENT_REJECTED 는 실패, 조회하지 않는다", async () => {
    const { result, port } = await run("rejected");
    expect(result).toMatchObject({ kind: "failed", cause: "invalid_input" });
    expect(port.getOrder).not.toHaveBeenCalled();
  });

  it("401 은 login_required", async () => {
    const port = {
      confirm: vi
        .fn<TopUpPort["confirm"]>()
        .mockRejectedValue(
          new ApiError({ status: 401, code: "SESSION_EXPIRED", traceId: "t" }),
        ),
      getOrder: vi.fn<TopUpPort["getOrder"]>(),
    };
    const result = await confirmTopUp(
      { paymentKey: "pk", orderId: ORDER_ID, amount: "1" },
      { port, ...fakeClock() },
    );
    expect(result).toEqual({ kind: "login_required", orderId: ORDER_ID });
  });

  it("응답 모양이 계약과 다르면 조용히 넘어가지 않고 던진다", async () => {
    const port = {
      confirm: vi
        .fn<TopUpPort["confirm"]>()
        .mockRejectedValue(new ApiContractError(200, "fixture")),
      getOrder: vi.fn<TopUpPort["getOrder"]>(),
    };
    await expect(
      confirmTopUp(
        { paymentKey: "pk", orderId: ORDER_ID, amount: "1" },
        { port, ...fakeClock() },
      ),
    ).rejects.toBeInstanceOf(ApiContractError);
  });
});

describe("pollTopUpOrder", () => {
  it("조회 중 네트워크 · 5xx 는 계속, FAILED 는 실패로 끝낸다", async () => {
    const getOrder = vi
      .fn<TopUpPort["getOrder"]>()
      .mockRejectedValueOnce(new ApiNetworkError())
      .mockRejectedValueOnce(
        new ApiError({ status: 503, code: null, traceId: null }),
      )
      .mockResolvedValueOnce(state("PAID"))
      .mockResolvedValueOnce(state("FAILED"));
    const result = await pollTopUpOrder(ORDER_ID, {
      port: { confirm: vi.fn(), getOrder },
      ...fakeClock(),
    });
    expect(result).toEqual({
      kind: "failed",
      orderId: ORDER_ID,
      cause: "order_failed",
    });
    expect(getOrder).toHaveBeenCalledTimes(4);
  });

  it("processing:false 인 PAID 도 완료로 보지 않는다", async () => {
    const getOrder = vi
      .fn<TopUpPort["getOrder"]>()
      .mockResolvedValue({ ...state("PAID"), processing: false });
    const result = await pollTopUpOrder(ORDER_ID, {
      port: { confirm: vi.fn(), getOrder },
      ...fakeClock(),
    });
    expect(result.kind).toBe("pending");
  });

  it("모르는 상태(UNKNOWN)는 완료가 아니라 계속 조회", async () => {
    const getOrder = vi
      .fn<TopUpPort["getOrder"]>()
      .mockResolvedValueOnce(state("UNKNOWN"))
      .mockResolvedValueOnce({ ...state("CREDITED"), walletBalance: 9 });
    const result = await pollTopUpOrder(ORDER_ID, {
      port: { confirm: vi.fn(), getOrder },
      ...fakeClock(),
    });
    expect(result).toEqual({
      kind: "credited",
      orderId: ORDER_ID,
      walletBalance: 9,
    });
  });

  it("중단 신호가 오면 멈춘다", async () => {
    const controller = new AbortController();
    controller.abort(new DOMException("aborted", "AbortError"));
    await expect(
      pollTopUpOrder(ORDER_ID, {
        port: { confirm: vi.fn(), getOrder: vi.fn() },
        signal: controller.signal,
      }),
    ).rejects.toMatchObject({ name: "AbortError" });
  });
});
