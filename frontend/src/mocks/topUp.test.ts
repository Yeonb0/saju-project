import { describe, expect, it } from "vitest";
import { ApiError, ApiNetworkError } from "@/lib/api/errors";
import { createFakeTopUpPort } from "./topUp";

// 픽스처일 뿐이며 실제 가격 · 규칙과 무관하다. 키 · 결제 키는 임의 값이다.
const KEY_1 = "11111111-1111-4111-8111-111111111111";
const KEY_2 = "22222222-2222-4222-8222-222222222222";

async function orderAndReturn(
  scenario?: Parameters<typeof createFakeTopUpPort>[0],
) {
  const port = createFakeTopUpPort(scenario);
  const order = await port.createOrder(
    { productCode: "FIXTURE_TOP_UP_B" },
    KEY_1,
  );
  const ret = {
    paymentKey: "fixture-payment-key",
    orderId: order.orderId,
    amount: String(order.amount.amount),
  };
  return { port, order, ret };
}

describe("가짜 충전 포트", () => {
  it("같은 키는 같은 주문, 다른 키는 새 주문", async () => {
    const port = createFakeTopUpPort();
    const a = await port.createOrder(
      { productCode: "FIXTURE_TOP_UP_A" },
      KEY_1,
    );
    const again = await port.createOrder(
      { productCode: "FIXTURE_TOP_UP_A" },
      KEY_1,
    );
    const b = await port.createOrder(
      { productCode: "FIXTURE_TOP_UP_A" },
      KEY_2,
    );
    expect(again.orderId).toBe(a.orderId);
    expect(b.orderId).not.toBe(a.orderId);
  });

  it("같은 키에 다른 상품이면 409 IDEMPOTENCY_KEY_REUSED", async () => {
    const port = createFakeTopUpPort();
    await port.createOrder({ productCode: "FIXTURE_TOP_UP_A" }, KEY_1);
    await expect(
      port.createOrder({ productCode: "FIXTURE_TOP_UP_B" }, KEY_1),
    ).rejects.toMatchObject({ status: 409, code: "IDEMPOTENCY_KEY_REUSED" });
  });

  it("비활성 상품은 422", async () => {
    const port = createFakeTopUpPort();
    await expect(
      port.createOrder({ productCode: "FIXTURE_TOP_UP_INACTIVE" }, KEY_1),
    ).rejects.toMatchObject({ status: 422 });
  });

  it("PG 복귀 금액이 주문 금액과 다르면 400 PAYMENT_AMOUNT_MISMATCH", async () => {
    const { port, ret } = await orderAndReturn();
    await expect(port.confirm({ ...ret, amount: "1" })).rejects.toMatchObject({
      status: 400,
      code: "PAYMENT_AMOUNT_MISMATCH",
    });
  });

  it("credited: 승인 응답이 CREDITED 이고 잔액은 서버(가짜) 값", async () => {
    const { port, ret } = await orderAndReturn();
    const state = await port.confirm(ret);
    expect(state.status).toBe("CREDITED");
    expect(state.walletBalance).toBe((await port.getWallet()).balance);
  });

  it("같은 주문을 다시 승인하면 처음 결과 — 두 번 지급하지 않는다", async () => {
    const { port, ret } = await orderAndReturn();
    const first = await port.confirm(ret);
    const second = await port.confirm(ret);
    expect(second).toEqual(first);
  });

  it("paid_then_credited: PAID 뒤 조회 N번에 CREDITED", async () => {
    const { port, ret } = await orderAndReturn({
      scenario: "paid_then_credited",
      creditAfterPolls: 2,
    });
    expect((await port.confirm(ret)).status).toBe("PAID");
    expect((await port.getOrder(ret.orderId)).status).toBe("PAID");
    expect((await port.getOrder(ret.orderId)).status).toBe("CREDITED");
  });

  it("confirm_lost: 응답은 네트워크 오류지만 조회하면 CREDITED", async () => {
    const { port, ret } = await orderAndReturn({ scenario: "confirm_lost" });
    await expect(port.confirm(ret)).rejects.toBeInstanceOf(ApiNetworkError);
    expect((await port.getOrder(ret.orderId)).status).toBe("CREDITED");
  });

  it("rejected: 422 PAYMENT_REJECTED, 주문 FAILED", async () => {
    const { port, ret } = await orderAndReturn({ scenario: "rejected" });
    const error = await port.confirm(ret).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((await port.getOrder(ret.orderId)).status).toBe("FAILED");
  });

  it("없는 주문 조회는 404", async () => {
    const port = createFakeTopUpPort();
    await expect(port.getOrder(KEY_2)).rejects.toMatchObject({ status: 404 });
  });
});
