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
  // 승인 · 조회 응답 필드 (Q-17 해결) — 픽스처일 뿐
  processing: false,
  walletBalance: null,
  grantedShellAmount: null,
  nextAction: "NONE",
  failureCode: null,
  ...overrides,
});

const creditedData = (overrides: Record<string, unknown> = {}) =>
  orderData({
    status: "CREDITED",
    walletBalance: 40,
    grantedShellAmount: 12,
    nextAction: "VIEW_WALLET",
    ...overrides,
  });

const refundData = (overrides: Record<string, unknown> = {}) => ({
  refundId: "9b7a1c0d-5e8f-4a6b-8c1e-3f2b8c1e6a4d",
  orderId: ORDER_ID,
  state: "RESERVED",
  amount: 1000,
  nextAction: "POLL",
  completedAt: null,
  ...overrides,
});

const productData = (overrides: Record<string, unknown> = {}) => ({
  code: "FIXTURE_TOP_UP",
  category: "TOP_UP",
  productName: "FIXTURE 상품",
  price: { currency: "KRW", amount: 1000 },
  paidAmount: 10,
  bonusAmount: 2,
  creditedAmount: 12,
  active: true,
  regularPrice: null,
  fortuneType: null,
  option: null,
  saleEndsAt: null,
  ...overrides,
});

function without(data: Record<string, unknown>, key: string) {
  const { [key]: _removed, ...rest } = data;
  return rest;
}

const RETURN = {
  paymentKey: "fixture-payment-key",
  orderId: ORDER_ID,
  amount: "5000",
};

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

describe("충전 adapter — confirm", () => {
  it("a. POST 경로 · Idempotency-Key = orderId · 본문은 amount 가 숫자", async () => {
    const { adapter, sent } = setup(ok(creditedData()));
    await adapter.confirm(RETURN);
    const req = sent(0);
    expect(req.url).toBe(`/api/v1/top-up-orders/${ORDER_ID}/confirm`);
    expect(req.method).toBe("POST");
    expect(req.headers["Idempotency-Key"]).toBe(ORDER_ID);
    expect(req.body).toBe(
      `{"paymentKey":"fixture-payment-key","orderId":"${ORDER_ID}","amount":5000}`,
    );
  });

  it("b. 403 CSRF_FAILED 뒤 성공하면 두 요청의 키 · 본문 문자열이 같다", async () => {
    const { adapter, fetchMock, sent } = setup(
      json(403, {
        code: "CSRF_FAILED",
        message: "픽스처 문구",
        traceId: TRACE,
        fieldErrors: [],
      }),
      ok(creditedData()),
    );
    await adapter.confirm(RETURN);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(sent(1).headers["Idempotency-Key"]).toBe(
      sent(0).headers["Idempotency-Key"],
    );
    expect(sent(1).body).toBe(sent(0).body);
  });

  it.each([
    "abc",
    "0",
    "-1",
    "1.5",
  ])("c. amount %s 는 요청하지 않고 던진다", async (amount) => {
    const { adapter, fetchMock } = setup();
    await expect(adapter.confirm({ ...RETURN, amount })).rejects.toThrow();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("d. 200 CREDITED 는 서버 값 그대로, nextAction VIEW_WALLET", async () => {
    const { adapter } = setup(ok(creditedData()));
    await expect(adapter.confirm(RETURN)).resolves.toEqual({
      orderId: ORDER_ID,
      status: "CREDITED",
      processing: false,
      walletBalance: 40,
      grantedShellAmount: 12,
      nextAction: "VIEW_WALLET",
    });
  });

  it("e. 202 PAYMENT_PENDING 의 null 은 0 이 아니라 null, nextAction POLL", async () => {
    const { adapter } = setup(
      ok(
        orderData({
          status: "PAYMENT_PENDING",
          processing: true,
          nextAction: "POLL",
        }),
        202,
      ),
    );
    const state = await adapter.confirm(RETURN);
    expect(state.status).toBe("PAYMENT_PENDING");
    expect(state.processing).toBe(true);
    expect(state.walletBalance).toBeNull();
    expect(state.grantedShellAmount).toBeNull();
    expect(state.nextAction).toBe("POLL");
  });

  it("f. 모르는 nextAction 은 UNKNOWN", async () => {
    const { adapter } = setup(ok(orderData({ nextAction: "FIXTURE_NEXT" })));
    const state = await adapter.confirm(RETURN);
    expect(state.nextAction).toBe("UNKNOWN");
  });

  it.each([
    ["processing 키 없음", () => without(creditedData(), "processing")],
    [
      "grantedShellAmount 키 없음",
      () => without(creditedData(), "grantedShellAmount"),
    ],
    [
      "CREDITED 인데 grantedShellAmount null",
      () => creditedData({ grantedShellAmount: null }),
    ],
    [
      "CREDITED 인데 walletBalance null",
      () => creditedData({ walletBalance: null }),
    ],
  ])("g. 계약 위반(%s)은 ApiContractError", async (_name, make) => {
    const { adapter } = setup(ok(make()));
    await expect(adapter.confirm(RETURN)).rejects.toBeInstanceOf(
      ApiContractError,
    );
  });
});

describe("충전 adapter — getOrder", () => {
  it("h. GET, 본문 없음, orderId 는 encodeURIComponent 로 경로에 넣는다", async () => {
    const { adapter, sent } = setup(ok(orderData()), ok(orderData()));
    await adapter.getOrder(ORDER_ID);
    expect(sent(0).url).toBe(`/api/v1/top-up-orders/${ORDER_ID}`);
    expect(sent(0).method).toBe("GET");
    expect(sent(0).body).toBeUndefined();
    await adapter.getOrder("a/b");
    expect(sent(1).url).toBe("/api/v1/top-up-orders/a%2Fb");
  });

  it("h. 같은 변환 — CREDITED 서버 값 그대로", async () => {
    const { adapter } = setup(ok(creditedData()));
    const state = await adapter.getOrder(ORDER_ID);
    expect(state.grantedShellAmount).toBe(12);
    expect(state.walletBalance).toBe(40);
  });

  it("REFUND_PENDING은 UNKNOWN으로 낮추지 않고 처리 중 상태로 보존한다", async () => {
    const { adapter } = setup(
      ok(orderData({ status: "REFUND_PENDING", nextAction: "POLL" })),
    );
    await expect(adapter.getOrder(ORDER_ID)).resolves.toMatchObject({
      status: "REFUND_PENDING",
      nextAction: "POLL",
    });
  });
});

describe("충전 adapter — listTopUpProducts", () => {
  it("i. GET /api/v1/products?category=TOP_UP, CSRF 헤더 없음, 포트 모양(productName · regularPrice 없음)", async () => {
    const { adapter, sent } = setup(
      ok({
        items: [
          productData({ regularPrice: { currency: "KRW", amount: 1200 } }),
        ],
      }),
    );
    const products = await adapter.listTopUpProducts();
    const req = sent(0);
    expect(req.url).toBe("/api/v1/products?category=TOP_UP");
    expect(req.method).toBe("GET");
    expect(req.headers["X-CSRF-Token"]).toBeUndefined();
    expect(products).toEqual([
      {
        code: "FIXTURE_TOP_UP",
        price: { currency: "KRW", amount: 1000 },
        paidAmount: 10,
        bonusAmount: 2,
        creditedAmount: 12,
        active: true,
      },
    ]);
    expect("productName" in products[0]).toBe(false);
    expect("regularPrice" in products[0]).toBe(false);
  });

  it("i. 빈 items 는 빈 배열 그대로", async () => {
    const { adapter } = setup(ok({ items: [] }));
    await expect(adapter.listTopUpProducts()).resolves.toEqual([]);
  });

  it.each([
    ["items 키 없음", () => ({})],
    [
      "category FORTUNE",
      () => ({ items: [productData({ category: "FORTUNE" })] }),
    ],
    ["paidAmount null", () => ({ items: [productData({ paidAmount: null })] })],
    [
      "price.currency TURTLE_SHELL",
      () => ({
        items: [
          productData({ price: { currency: "TURTLE_SHELL", amount: 1000 } }),
        ],
      }),
    ],
  ])("j. 계약 위반(%s)은 ApiContractError", async (_name, make) => {
    const { adapter } = setup(ok(make()));
    await expect(adapter.listTopUpProducts()).rejects.toBeInstanceOf(
      ApiContractError,
    );
  });
});

describe("충전 adapter — cash refund", () => {
  it("신청은 CSRF와 같은 멱등 키를 보내고 202 상태를 보존한다", async () => {
    const key = createIdempotencyKey();
    const { adapter, sent } = setup(ok(refundData(), 202));
    await expect(adapter.requestRefund(ORDER_ID, key)).resolves.toEqual({
      ...refundData(),
      state: "RESERVED",
      nextAction: "POLL",
    });
    expect(sent(0)).toMatchObject({
      url: `/api/v1/top-up-orders/${ORDER_ID}/refund`,
      method: "POST",
      body: undefined,
    });
    expect(sent(0).headers["Idempotency-Key"]).toBe(key);
    expect(sent(0).headers["X-CSRF-Token"]).toBe("FIXTURE-CSRF");
  });

  it("조회는 본문·CSRF 없이 완료 시각을 그대로 돌려준다", async () => {
    const completedAt = "2026-10-13T03:00:00Z";
    const { adapter, sent } = setup(
      ok(refundData({ state: "REFUNDED", nextAction: "NONE", completedAt })),
    );
    const refund = await adapter.getRefund(ORDER_ID);
    expect(refund).toMatchObject({ state: "REFUNDED", completedAt });
    expect(sent(0).method).toBe("GET");
    expect(sent(0).body).toBeUndefined();
    expect(sent(0).headers["X-CSRF-Token"]).toBeUndefined();
  });

  it("모르는 상태·행동은 UNKNOWN이고 잘못된 완료 시각은 계약 오류다", async () => {
    const unknown = setup(
      ok(refundData({ state: "NEW_STATE", nextAction: "NEW_ACTION" })),
    );
    await expect(unknown.adapter.getRefund(ORDER_ID)).resolves.toMatchObject({
      state: "UNKNOWN",
      nextAction: "UNKNOWN",
    });

    const invalid = setup(ok(refundData({ completedAt: "not-a-date" })));
    await expect(invalid.adapter.getRefund(ORDER_ID)).rejects.toBeInstanceOf(
      ApiContractError,
    );
  });
});
