import { describe, expect, it, vi } from "vitest";
import { ApiContractError, ApiError } from "@/lib/api/errors";
import { type CsrfSource, createApiClient } from "@/lib/api/http";
import { createIdempotencyKey } from "@/lib/api/idempotency";
import { createTopUpAdapter } from "./topUp";

// 픽스처일 뿐이며 실제 주문 · 상품 · 금액 · 잔액과 무관하다
const TRACE = "fixture-trace";
const ORDER_ID = "3f2b8c1e-6a4d-4e2f-9b7a-1c0d5e8f2a6b";
const INPUT = { productCode: "FIXTURE_PRODUCT" };

const walletData = (overrides: Record<string, unknown> = {}) => ({
  currency: "TURTLE_SHELL",
  balance: 30,
  paidBalance: 20,
  bonusBalance: 10,
  ...overrides,
});

const orderData = (overrides: Record<string, unknown> = {}) => ({
  orderId: ORDER_ID,
  productCode: "FIXTURE_PRODUCT",
  orderName: "FIXTURE 주문",
  amount: 1000,
  currency: "KRW",
  status: "PAYMENT_PENDING",
  paidShellAmount: 10,
  bonusShellAmount: 2,
  creditedShellAmount: 12,
  ...overrides,
});

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

const ok = (data: unknown, status = 200) =>
  json(status, { data, traceId: TRACE });

function setup(...responses: Response[]) {
  const fetchMock = vi.fn<typeof fetch>();
  for (const r of responses) fetchMock.mockResolvedValueOnce(r);
  const csrf: CsrfSource = {
    current: vi.fn<CsrfSource["current"]>().mockResolvedValue("FIXTURE-CSRF"),
    refresh: vi.fn<CsrfSource["refresh"]>().mockResolvedValue("FIXTURE-CSRF-2"),
  };
  const adapter = createTopUpAdapter(
    createApiClient({ csrf, fetch: fetchMock }),
  );
  const sent = (i: number) => {
    const init = fetchMock.mock.calls[i][1] ?? {};
    return {
      url: fetchMock.mock.calls[i][0],
      method: init.method,
      body: init.body,
      headers: init.headers as Record<string, string>,
    };
  };
  return { adapter, fetchMock, sent };
}

describe("충전 adapter — getWallet", () => {
  it("t1. GET /api/v1/wallet, 본문 · CSRF · 멱등 키 헤더 없음", async () => {
    const { adapter, fetchMock, sent } = setup(ok(walletData()));
    await adapter.getWallet();
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const req = sent(0);
    expect(req.url).toBe("/api/v1/wallet");
    expect(req.method).toBe("GET");
    expect(req.body).toBeUndefined();
    expect(req.headers["X-CSRF-Token"]).toBeUndefined();
    expect(req.headers["Idempotency-Key"]).toBeUndefined();
  });

  it("t2. 성공하면 { currency, balance } 만 돌려준다", async () => {
    const { adapter } = setup(ok(walletData()));
    await expect(adapter.getWallet()).resolves.toEqual({
      currency: "TURTLE_SHELL",
      balance: 30,
    });
  });

  it.each([
    ["balance 없음", walletData({ balance: undefined })],
    ["balance -1", walletData({ balance: -1 })],
    ["balance 1.5", walletData({ balance: 1.5 })],
    ["currency 빈 문자열", walletData({ currency: "" })],
    ["bonusBalance 없음", walletData({ bonusBalance: undefined })],
  ])("t3. 계약 위반: %s", async (_name, data) => {
    const { adapter } = setup(ok(data));
    await expect(adapter.getWallet()).rejects.toBeInstanceOf(ApiContractError);
  });
});

describe("충전 adapter — createOrder", () => {
  it("t4. POST /api/v1/top-up-orders, 넘긴 키 · CSRF, 본문은 { productCode } 만", async () => {
    const { adapter, sent } = setup(ok(orderData(), 201));
    const key = createIdempotencyKey();
    await adapter.createOrder(INPUT, key);
    const req = sent(0);
    expect(req.url).toBe("/api/v1/top-up-orders");
    expect(req.method).toBe("POST");
    expect(req.headers["Idempotency-Key"]).toBe(key);
    expect(req.headers["X-CSRF-Token"]).toBe("FIXTURE-CSRF");
    expect(JSON.parse(String(req.body))).toEqual({
      productCode: "FIXTURE_PRODUCT",
    });
  });

  it("t5. 같은 키 · 같은 입력으로 두 번 부르면 키와 본문 문자열이 같다 (I-05)", async () => {
    const { adapter, sent } = setup(ok(orderData(), 201), ok(orderData(), 201));
    const key = createIdempotencyKey();
    await adapter.createOrder(INPUT, key);
    await adapter.createOrder(INPUT, key);
    expect(sent(1).headers["Idempotency-Key"]).toBe(
      sent(0).headers["Idempotency-Key"],
    );
    expect(sent(1).body).toBe(sent(0).body);
  });

  it("t6. 성공(201)하면 예정 지급량 없이 포트 모델만 돌려준다", async () => {
    const { adapter } = setup(ok(orderData(), 201));
    await expect(
      adapter.createOrder(INPUT, createIdempotencyKey()),
    ).resolves.toEqual({
      orderId: ORDER_ID,
      orderName: "FIXTURE 주문",
      amount: { currency: "KRW", amount: 1000 },
      status: "PAYMENT_PENDING",
    });
  });

  it("t7. 모르는 status 는 UNKNOWN (계약 위반 아님)", async () => {
    const { adapter } = setup(ok(orderData({ status: "FIXTURE_NEW" }), 201));
    const order = await adapter.createOrder(INPUT, createIdempotencyKey());
    expect(order.status).toBe("UNKNOWN");
  });

  it.each([
    ["orderId 가 uuid 아님", orderData({ orderId: "not-a-uuid" })],
    ["amount 없음", orderData({ amount: undefined })],
    ["amount -1", orderData({ amount: -1 })],
    ["creditedShellAmount 없음", orderData({ creditedShellAmount: undefined })],
    ["orderName 빈 문자열", orderData({ orderName: "" })],
  ])("t8. 계약 위반: %s", async (_name, data) => {
    const { adapter } = setup(ok(data, 201));
    await expect(
      adapter.createOrder(INPUT, createIdempotencyKey()),
    ).rejects.toBeInstanceOf(ApiContractError);
  });

  it("t9. 409 IDEMPOTENCY_KEY_REUSED 는 ApiError 그대로", async () => {
    const { adapter } = setup(
      json(409, {
        code: "IDEMPOTENCY_KEY_REUSED",
        message: "픽스처 문구",
        traceId: TRACE,
        fieldErrors: [],
      }),
    );
    const error = await adapter
      .createOrder(INPUT, createIdempotencyKey())
      .catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(409);
  });
});

describe("충전 adapter — 계약이 없는 함수", () => {
  it("t10. fetch 를 부르지 않고 근거와 함께 던진다", async () => {
    const { adapter, fetchMock } = setup();
    await expect(adapter.listTopUpProducts()).rejects.toThrow(/Q-28/);
    await expect(
      adapter.confirm({
        paymentKey: "FIXTURE",
        orderId: ORDER_ID,
        amount: "1",
      }),
    ).rejects.toThrow(/Q-17/);
    await expect(adapter.getOrder(ORDER_ID)).rejects.toThrow(/Q-17/);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
