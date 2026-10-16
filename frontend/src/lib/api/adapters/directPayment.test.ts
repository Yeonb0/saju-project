import { describe, expect, it, vi } from "vitest";
import { ApiContractError, ApiError } from "@/lib/api/errors";
import { type CsrfSource, createApiClient } from "@/lib/api/http";
import { createIdempotencyKey } from "@/lib/api/idempotency";
import type { FortuneSelection } from "@/lib/ports/fortune";
import { createDirectPaymentAdapter } from "./directPayment";

// 픽스처일 뿐이며 실제 주문 · 상품 · 금액 · 결제 키와 무관하다 (DIRECT-PAY).
// TODO(OPENAPI-FE-SYNC): 응답 모양은 PR #49 c2a1392 컨트롤러 기준이다.
const TRACE = "fixture-trace";
const ORDER_ID = "3f2b8c1e-6a4d-4e2f-9b7a-1c0d5e8f2a6b";
const QUOTE_ID = "11111111-1111-4111-8111-111111111111";
const PERSON_ID = "66666666-6666-4666-8666-666666666666";
const OTHER_ID = "99999999-9999-4999-8999-999999999999";
const PURCHASE_ID = "22222222-2222-4222-8222-222222222222";
const READING_ID = "44444444-4444-4444-8444-444444444444";

const orderData = (overrides: Record<string, unknown> = {}) => ({
  orderId: ORDER_ID,
  productCode: "FIXTURE_PRODUCT",
  orderName: "FIXTURE 주문",
  amount: 1234,
  currency: "KRW",
  status: "PAYMENT_PENDING",
  createdAt: "2026-10-16T00:00:00Z",
  ...overrides,
});

const fulfillmentData = (overrides: Record<string, unknown> = {}) => ({
  purchaseId: PURCHASE_ID,
  readingId: READING_ID,
  status: "FULFILLED",
  reused: false,
  ...overrides,
});

const RETURN = {
  paymentKey: "fixture-payment-key",
  orderId: ORDER_ID,
  amount: "1234",
};

const OVERALL: FortuneSelection = {
  fortuneType: "OVERALL",
  productCode: "FIXTURE_OVERALL_READING_ONLY",
  personId: PERSON_ID,
  counterpartPersonId: null,
  productOption: "READING_ONLY",
  questionKey: "OVERALL_FLOW",
  relationType: null,
};
const COMPAT: FortuneSelection = {
  fortuneType: "COMPATIBILITY",
  productCode: "FIXTURE_COMPATIBILITY_READING_ONLY",
  personId: PERSON_ID,
  counterpartPersonId: OTHER_ID,
  productOption: "READING_ONLY",
  questionKey: "OVERALL_MATCH",
  relationType: "CRUSH",
};
const SUNEUNG: FortuneSelection = {
  fortuneType: "SUNEUNG",
  productCode: "FIXTURE_SUNEUNG_READING_WITH_TALISMAN",
  personId: PERSON_ID,
  counterpartPersonId: null,
  productOption: null,
  questionKey: null,
  relationType: null,
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
  const adapter = createDirectPaymentAdapter(
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

describe("직접결제 adapter — createOrder", () => {
  it("D1. POST /api/v1/direct-payment-batches, 넘긴 키, 본문은 정확히 {quoteId, quantity: 1}, 응답은 포트 모델", async () => {
    const { adapter, sent } = setup(ok(orderData(), 201));
    const key = createIdempotencyKey();
    const order = await adapter.createOrder({ quoteId: QUOTE_ID }, key);
    const req = sent(0);
    expect(req.url).toBe("/api/v1/direct-payment-batches");
    expect(req.method).toBe("POST");
    expect(req.headers["Idempotency-Key"]).toBe(key);
    expect(req.headers["X-CSRF-Token"]).toBe("FIXTURE-CSRF");
    expect(req.body).toBe(`{"quoteId":"${QUOTE_ID}","quantity":1}`);
    expect(order).toEqual({
      orderId: ORDER_ID,
      productCode: "FIXTURE_PRODUCT",
      orderName: "FIXTURE 주문",
      amount: { currency: "KRW", amount: 1234 },
      status: "PAYMENT_PENDING",
    });
  });

  it.each([
    ["currency TURTLE_SHELL", { currency: "TURTLE_SHELL" }],
    ["amount 0", { amount: 0 }],
    ["orderId 가 uuid 아님", { orderId: "not-a-uuid" }],
    ["createdAt 날짜 아님", { createdAt: "not-a-date" }],
    ["orderName 빈 문자열", { orderName: "" }],
  ])("D2. 계약 위반 (%s) 은 ApiContractError", async (_name, overrides) => {
    const { adapter } = setup(ok(orderData(overrides), 201));
    await expect(
      adapter.createOrder({ quoteId: QUOTE_ID }, createIdempotencyKey()),
    ).rejects.toBeInstanceOf(ApiContractError);
  });

  it("D2. 모르는 status 는 UNKNOWN (계약 위반 아님)", async () => {
    const { adapter } = setup(ok(orderData({ status: "FIXTURE_NEW" }), 201));
    const order = await adapter.createOrder(
      { quoteId: QUOTE_ID },
      createIdempotencyKey(),
    );
    expect(order.status).toBe("UNKNOWN");
  });
});

describe("직접결제 adapter — confirm", () => {
  it("D3. Idempotency-Key = orderId, 본문 {paymentKey, orderId, amount: 숫자}, 202 PAYMENT_PENDING 은 그대로", async () => {
    const { adapter, sent } = setup(ok(orderData(), 202));
    const order = await adapter.confirm(RETURN);
    const req = sent(0);
    expect(req.url).toBe(`/api/v1/direct-payment-batches/${ORDER_ID}/confirm`);
    expect(req.method).toBe("POST");
    expect(req.headers["Idempotency-Key"]).toBe(ORDER_ID);
    expect(req.body).toBe(
      `{"paymentKey":"fixture-payment-key","orderId":"${ORDER_ID}","amount":1234}`,
    );
    expect(order.status).toBe("PAYMENT_PENDING");
  });

  it("D3. 200 PAID 도 같은 방식으로 포트 모델이 된다", async () => {
    const { adapter } = setup(ok(orderData({ status: "PAID" })));
    expect((await adapter.confirm(RETURN)).status).toBe("PAID");
  });

  it.each([
    "0",
    "1.5",
    "abc",
  ])("D3. amount %j 는 요청 없이 던진다", async (amount) => {
    const { adapter, fetchMock } = setup();
    await expect(adapter.confirm({ ...RETURN, amount })).rejects.toThrow();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe("직접결제 adapter — getOrder", () => {
  it("D4. GET 경로, 본문 없음, 응답은 포트 모델", async () => {
    const { adapter, sent } = setup(ok(orderData({ status: "PAID" })));
    const order = await adapter.getOrder(ORDER_ID);
    const req = sent(0);
    expect(req.url).toBe(`/api/v1/direct-payment-batches/${ORDER_ID}`);
    expect(req.method).toBe("GET");
    expect(req.body).toBeUndefined();
    expect(order.status).toBe("PAID");
  });

  it("D4. 404 응답은 ApiError(404) 그대로", async () => {
    const { adapter } = setup(
      json(404, {
        code: "RESOURCE_NOT_FOUND",
        message: "픽스처 문구",
        traceId: TRACE,
        fieldErrors: [],
      }),
    );
    const error = await adapter.getOrder(ORDER_ID).catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(404);
  });
});

describe("직접결제 adapter — fulfillReading", () => {
  it("D5. 일반 운세(OVERALL) 본문과 경로, FULFILLED · readingId 그대로", async () => {
    const { adapter, sent } = setup(ok(fulfillmentData(), 201));
    const key = createIdempotencyKey();
    const result = await adapter.fulfillReading(
      { orderId: ORDER_ID, quoteId: QUOTE_ID, selection: OVERALL },
      key,
    );
    const req = sent(0);
    expect(req.url).toBe(
      `/api/v1/direct-payment-batches/${ORDER_ID}/reading-fulfillment`,
    );
    expect(req.method).toBe("POST");
    expect(req.headers["Idempotency-Key"]).toBe(key);
    expect(JSON.parse(String(req.body))).toEqual({
      quoteId: QUOTE_ID,
      personId: PERSON_ID,
      fortuneType: "OVERALL",
      productOption: "READING_ONLY",
      questionKey: "OVERALL_FLOW",
      counterpartPersonId: null,
      relationType: null,
      personalizedConcern: null,
    });
    expect(result).toEqual({
      purchaseId: PURCHASE_ID,
      readingId: READING_ID,
      status: "FULFILLED",
    });
  });

  it("D5. 궁합 본문은 상대 · 관계 값을 담는다", async () => {
    const { adapter, sent } = setup(ok(fulfillmentData(), 201));
    await adapter.fulfillReading(
      { orderId: ORDER_ID, quoteId: QUOTE_ID, selection: COMPAT },
      createIdempotencyKey(),
    );
    expect(JSON.parse(String(sent(0).body))).toEqual({
      quoteId: QUOTE_ID,
      personId: PERSON_ID,
      fortuneType: "COMPATIBILITY",
      productOption: "READING_ONLY",
      questionKey: "OVERALL_MATCH",
      counterpartPersonId: OTHER_ID,
      relationType: "CRUSH",
      personalizedConcern: null,
    });
  });

  it("D5. 같은 키 · 같은 입력이면 본문 문자열이 같다 (I-05)", async () => {
    const { adapter, sent } = setup(
      ok(fulfillmentData(), 201),
      ok(fulfillmentData({ reused: true }), 201),
    );
    const key = createIdempotencyKey();
    const input = { orderId: ORDER_ID, quoteId: QUOTE_ID, selection: OVERALL };
    await adapter.fulfillReading(input, key);
    await adapter.fulfillReading(input, key);
    expect(sent(1).body).toBe(sent(0).body);
    expect(sent(1).headers["Idempotency-Key"]).toBe(key);
  });

  it("D5. GENERATING + readingId null 은 null 그대로 (완료로 바꾸지 않는다)", async () => {
    const { adapter } = setup(
      ok(fulfillmentData({ status: "GENERATING", readingId: null }), 202),
    );
    await expect(
      adapter.fulfillReading(
        { orderId: ORDER_ID, quoteId: QUOTE_ID, selection: OVERALL },
        createIdempotencyKey(),
      ),
    ).resolves.toEqual({
      purchaseId: PURCHASE_ID,
      readingId: null,
      status: "GENERATING",
    });
  });

  it("D5. 응답 계약 위반(reused 없음)은 ApiContractError", async () => {
    const { reused: _removed, ...rest } = fulfillmentData();
    const { adapter } = setup(ok(rest, 201));
    await expect(
      adapter.fulfillReading(
        { orderId: ORDER_ID, quoteId: QUOTE_ID, selection: OVERALL },
        createIdempotencyKey(),
      ),
    ).rejects.toBeInstanceOf(ApiContractError);
  });

  it("D6. 수능운 선택이면 Q-64 오류로 던지고 fetch 는 한 번도 불리지 않는다", async () => {
    const { adapter, fetchMock } = setup();
    await expect(
      adapter.fulfillReading(
        { orderId: ORDER_ID, quoteId: QUOTE_ID, selection: SUNEUNG },
        createIdempotencyKey(),
      ),
    ).rejects.toThrow("Q-64");
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe("직접결제 adapter — 서버 오류", () => {
  it("D7. 409 PAYMENT_AMOUNT_MISMATCH 면 code 가 그대로인 ApiError", async () => {
    const { adapter } = setup(
      json(409, {
        code: "PAYMENT_AMOUNT_MISMATCH",
        message: "픽스처 문구",
        traceId: TRACE,
        fieldErrors: [],
      }),
    );
    const error = await adapter.confirm(RETURN).catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(409);
    expect((error as ApiError).code).toBe("PAYMENT_AMOUNT_MISMATCH");
  });
});
