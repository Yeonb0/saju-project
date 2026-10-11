import { describe, expect, it, vi } from "vitest";
import { ApiContractError, ApiError, classifyApiError } from "@/lib/api/errors";
import { type CsrfSource, createApiClient } from "@/lib/api/http";
import type { FortuneSelection, FortuneType } from "@/lib/ports/fortune";
import { createFortuneAdapter } from "./fortune";

// 이 파일의 ID · 금액 · 이름은 픽스처일 뿐이며 실제 가격 · 규칙과 무관하다.
const PERSON_ID = "66666666-6666-4666-8666-666666666666";
const OTHER_ID = "99999999-9999-4999-8999-999999999999";
const QUOTE_ID = "77777777-7777-4777-8777-777777777777";
const PURCHASE_ID = "88888888-8888-4888-8888-888888888888";
const READING_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
const KEY = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
const TRACE = "fixture-trace";

const SUNEUNG: FortuneSelection = {
  fortuneType: "SUNEUNG",
  productCode: "FIXTURE_SUNEUNG",
  personId: PERSON_ID,
  counterpartPersonId: null,
};

const quoteData = (overrides: Record<string, unknown> = {}) => ({
  quoteId: QUOTE_ID,
  productCode: "FIXTURE_SUNEUNG",
  // 픽스처일 뿐 — 실제 상품 이름 · 정가와 무관하다
  productName: "FIXTURE_PRODUCT_NAME",
  regularPrice: { currency: "TURTLE_SHELL", amount: 20 },
  charged: { currency: "TURTLE_SHELL", amount: 13 },
  expiresAt: "2099-01-01T00:00:00Z",
  walletBalance: 20,
  shortage: 0,
  balanceAfter: 7,
  recommendedTopUp: null,
  event: { type: "FIXTURE_EVENT", date: "2099-01-01" },
  // 포트에 넣지 않는 값이 더 와도 성공이어야 한다
  extra: "FIXTURE",
  ...overrides,
});

const purchaseData = (overrides: Record<string, unknown> = {}) => ({
  purchaseId: PURCHASE_ID,
  readingId: READING_ID,
  status: "FULFILLED",
  charged: { currency: "TURTLE_SHELL", amount: 13 },
  balance: 7,
  // 포트에 넣지 않는 값 (Q-37)
  reused: false,
  generationVersion: "FIXTURE",
  ...overrides,
});

function without(data: Record<string, unknown>, key: string) {
  const { [key]: _removed, ...rest } = data;
  return rest;
}

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

const ok = (data: unknown, status = 200) =>
  json(status, { data, traceId: TRACE });
const failure = (status: number, code: string) =>
  json(status, {
    code,
    message: "픽스처 문구",
    traceId: TRACE,
    fieldErrors: [],
  });

function setup(...responses: Response[]) {
  const fetchMock = vi.fn<typeof fetch>();
  for (const response of responses) fetchMock.mockResolvedValueOnce(response);
  const csrf: CsrfSource = {
    current: vi.fn<CsrfSource["current"]>().mockResolvedValue("FIXTURE-CSRF"),
    refresh: vi.fn<CsrfSource["refresh"]>().mockResolvedValue("FIXTURE-CSRF-2"),
  };
  const adapter = createFortuneAdapter(
    createApiClient({ csrf, fetch: fetchMock }),
  );
  return { adapter, fetchMock };
}

const headersOf = (init: RequestInit | undefined) =>
  init?.headers as Record<string, string>;

const NON_SUNEUNG: readonly Exclude<FortuneType, "UNKNOWN" | "SUNEUNG">[] = [
  "OVERALL",
  "LOVE",
  "WEALTH",
  "COMPATIBILITY",
  "SINSAL",
];

describe("운세 adapter — 견적 (createQuote)", () => {
  it("a. POST /api/v1/quotes/fortune 에 본문이 정확히 {personId}, 멱등 키 없음, CSRF 있음", async () => {
    const { adapter, fetchMock } = setup(ok(quoteData()));
    await adapter.createQuote(SUNEUNG);

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/v1/quotes/fortune");
    expect(init?.method).toBe("POST");
    expect(init?.body).toBe(JSON.stringify({ personId: PERSON_ID }));
    expect(headersOf(init)["Idempotency-Key"]).toBeUndefined();
    expect(headersOf(init)["X-CSRF-Token"]).toBe("FIXTURE-CSRF");
  });

  it("a. 응답을 포트 모델로 옮긴다 — charged 그대로, productName 은 null, balanceAfter · recommendedTopUp 의 null 은 그대로", async () => {
    const { adapter } = setup(
      ok(
        quoteData({ balanceAfter: null, recommendedTopUp: null, shortage: 5 }),
      ),
    );
    const quote = await adapter.createQuote(SUNEUNG);
    expect(quote).toEqual({
      quoteId: QUOTE_ID,
      productCode: "FIXTURE_SUNEUNG",
      productName: "FIXTURE_PRODUCT_NAME",
      charged: { currency: "TURTLE_SHELL", amount: 13 },
      walletBalance: 20,
      balanceAfter: null,
      shortage: 5,
      recommendedTopUp: null,
      expiresAt: "2099-01-01T00:00:00Z",
    });
  });

  it("a. 값이 있는 balanceAfter · recommendedTopUp 도 그대로 옮긴다", async () => {
    const { adapter } = setup(
      ok(quoteData({ recommendedTopUp: "FIXTURE_TOP_UP", balanceAfter: 0 })),
    );
    const quote = await adapter.createQuote(SUNEUNG);
    expect(quote.recommendedTopUp).toBe("FIXTURE_TOP_UP");
    expect(quote.balanceAfter).toBe(0);
  });

  it.each(NON_SUNEUNG)("b. %s 는 요청하지 않고 던진다", async (fortuneType) => {
    const { adapter, fetchMock } = setup();
    await expect(
      adapter.createQuote({ ...SUNEUNG, fortuneType }),
    ).rejects.toThrow();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("b. SUNEUNG 이어도 counterpartPersonId 가 있으면 요청하지 않고 던진다", async () => {
    const { adapter, fetchMock } = setup();
    await expect(
      adapter.createQuote({ ...SUNEUNG, counterpartPersonId: OTHER_ID }),
    ).rejects.toThrow();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it.each([
    ["charged 없음", () => without(quoteData(), "charged")],
    [
      "charged.amount 음수",
      () => quoteData({ charged: { currency: "TURTLE_SHELL", amount: -1 } }),
    ],
    ["quoteId 가 uuid 아님", () => quoteData({ quoteId: "not-a-uuid" })],
    ["balanceAfter 키 없음", () => without(quoteData(), "balanceAfter")],
    [
      "recommendedTopUp 키 없음",
      () => without(quoteData(), "recommendedTopUp"),
    ],
    ["expiresAt 이 날짜가 아님", () => quoteData({ expiresAt: "내일" })],
    ["POST 응답에 event 없음", () => without(quoteData(), "event")],
    ["productName 키 없음", () => without(quoteData(), "productName")],
    ["productName 빈 문자열", () => quoteData({ productName: "" })],
    ["productName null", () => quoteData({ productName: null })],
    ["regularPrice 키 없음", () => without(quoteData(), "regularPrice")],
    [
      "regularPrice.amount 음수",
      () =>
        quoteData({ regularPrice: { currency: "TURTLE_SHELL", amount: -1 } }),
    ],
  ])("c. 응답 위반(%s)은 ApiContractError", async (_name, make) => {
    const { adapter } = setup(ok(make()));
    await expect(adapter.createQuote(SUNEUNG)).rejects.toBeInstanceOf(
      ApiContractError,
    );
  });
});

describe("운세 adapter — 견적 productName · regularPrice (PG-1)", () => {
  it.each([
    ["regularPrice null", null],
    ["regularPrice 객체", { currency: "TURTLE_SHELL", amount: 20 }],
  ])("%s 도 통과하고 포트 결과에는 regularPrice 키가 없다", async (_name, regularPrice) => {
    const { adapter } = setup(
      ok(quoteData({ regularPrice })),
      ok(quoteData({ regularPrice })),
    );
    const created = await adapter.createQuote(SUNEUNG);
    const fetched = await adapter.getQuote(QUOTE_ID);
    expect(created.productName).toBe("FIXTURE_PRODUCT_NAME");
    expect(fetched.productName).toBe("FIXTURE_PRODUCT_NAME");
    expect("regularPrice" in created).toBe(false);
    expect("regularPrice" in fetched).toBe(false);
  });
});

describe("운세 adapter — regularPrice 통화 (PG-1)", () => {
  it("e. regularPrice.currency 가 처음 보는 값이어도 createQuote · getQuote 가 통과한다", async () => {
    // 픽스처일 뿐 — 실제 통화 코드가 아니다
    const regularPrice = { currency: "FIXTURE_UNKNOWN_CURRENCY", amount: 20 };
    const { adapter } = setup(
      ok(quoteData({ regularPrice })),
      ok(quoteData({ regularPrice })),
    );
    await expect(adapter.createQuote(SUNEUNG)).resolves.toBeDefined();
    await expect(adapter.getQuote(QUOTE_ID)).resolves.toBeDefined();
  });
});

describe("운세 adapter — 견적 재확인 (getQuote)", () => {
  it("d. GET /api/v1/quotes/<id>, 본문 없음, event 가 없는 응답도 통과", async () => {
    const { adapter, fetchMock } = setup(ok(without(quoteData(), "event")));
    const quote = await adapter.getQuote(QUOTE_ID);

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`/api/v1/quotes/${QUOTE_ID}`);
    expect(init?.method).toBe("GET");
    expect(init?.body).toBeUndefined();
    expect(quote.quoteId).toBe(QUOTE_ID);
    expect(quote.productName).toBe("FIXTURE_PRODUCT_NAME");
  });

  it("d. quoteId 는 encodeURIComponent 로 경로에 넣는다", async () => {
    const { adapter, fetchMock } = setup(ok(quoteData()));
    await adapter.getQuote("a/b");
    expect(fetchMock.mock.calls[0][0]).toBe("/api/v1/quotes/a%2Fb");
  });

  it("d. 응답 위반은 ApiContractError", async () => {
    const { adapter } = setup(ok(without(quoteData(), "charged")));
    await expect(adapter.getQuote(QUOTE_ID)).rejects.toBeInstanceOf(
      ApiContractError,
    );
  });

  it.each([
    ["productName 키 없음", () => without(quoteData(), "productName")],
    ["productName 빈 문자열", () => quoteData({ productName: "" })],
    ["productName null", () => quoteData({ productName: null })],
    ["regularPrice 키 없음", () => without(quoteData(), "regularPrice")],
    [
      "regularPrice.amount 음수",
      () =>
        quoteData({ regularPrice: { currency: "TURTLE_SHELL", amount: -1 } }),
    ],
  ])("d. 응답 위반(%s)은 ApiContractError", async (_name, make) => {
    const { adapter } = setup(ok(make()));
    await expect(adapter.getQuote(QUOTE_ID)).rejects.toBeInstanceOf(
      ApiContractError,
    );
  });

  it("d. 409 QUOTE_EXPIRED 는 quote_expired 로 분류된다", async () => {
    const { adapter } = setup(failure(409, "QUOTE_EXPIRED"));
    const error = await adapter.getQuote(QUOTE_ID).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(classifyApiError(error)).toBe("quote_expired");
  });
});

describe("운세 adapter — 구매 (purchase)", () => {
  it("e. POST /api/v1/reading-purchases, 키는 받은 것, 본문이 정확히 {quoteId, personId}", async () => {
    const { adapter, fetchMock } = setup(ok(purchaseData(), 201));
    await adapter.purchase({ quoteId: QUOTE_ID, selection: SUNEUNG }, KEY);

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/v1/reading-purchases");
    expect(init?.method).toBe("POST");
    expect(headersOf(init)["Idempotency-Key"]).toBe(KEY);
    expect(headersOf(init)["X-CSRF-Token"]).toBe("FIXTURE-CSRF");
    expect(init?.body).toBe(
      JSON.stringify({ quoteId: QUOTE_ID, personId: PERSON_ID }),
    );
  });

  it("e. 201 FULFILLED 를 그대로 옮긴다", async () => {
    const { adapter } = setup(ok(purchaseData(), 201));
    const result = await adapter.purchase(
      { quoteId: QUOTE_ID, selection: SUNEUNG },
      KEY,
    );
    expect(result).toEqual({
      purchaseId: PURCHASE_ID,
      readingId: READING_ID,
      status: "FULFILLED",
      charged: { currency: "TURTLE_SHELL", amount: 13 },
      balance: 7,
    });
  });

  it("e. 202 GENERATING 의 readingId · balance · charged null 은 null 로 남는다 (0 이 아님)", async () => {
    const { adapter } = setup(
      ok(
        purchaseData({
          status: "GENERATING",
          readingId: null,
          balance: null,
          charged: null,
        }),
        202,
      ),
    );
    const result = await adapter.purchase(
      { quoteId: QUOTE_ID, selection: SUNEUNG },
      KEY,
    );
    expect(result.status).toBe("GENERATING");
    expect(result.readingId).toBeNull();
    expect(result.balance).toBeNull();
    expect(result.charged).toBeNull();
  });

  it("e. 모르는 status 는 UNKNOWN", async () => {
    const { adapter } = setup(ok(purchaseData({ status: "PAUSED" })));
    const result = await adapter.purchase(
      { quoteId: QUOTE_ID, selection: SUNEUNG },
      KEY,
    );
    expect(result.status).toBe("UNKNOWN");
  });

  it.each([
    ["purchaseId 없음", () => without(purchaseData(), "purchaseId")],
    ["readingId 키 없음", () => without(purchaseData(), "readingId")],
    ["balance 키 없음", () => without(purchaseData(), "balance")],
  ])("f. 응답 위반(%s)은 ApiContractError", async (_name, make) => {
    const { adapter } = setup(ok(make()));
    await expect(
      adapter.purchase({ quoteId: QUOTE_ID, selection: SUNEUNG }, KEY),
    ).rejects.toBeInstanceOf(ApiContractError);
  });

  it("g. 403 CSRF_FAILED 뒤 성공하면 두 요청의 Idempotency-Key · 본문 문자열이 같다", async () => {
    const { adapter, fetchMock } = setup(
      failure(403, "CSRF_FAILED"),
      ok(purchaseData(), 201),
    );
    await adapter.purchase({ quoteId: QUOTE_ID, selection: SUNEUNG }, KEY);

    expect(fetchMock).toHaveBeenCalledTimes(2);
    const [, first] = fetchMock.mock.calls[0];
    const [, second] = fetchMock.mock.calls[1];
    expect(headersOf(first)["Idempotency-Key"]).toBe(KEY);
    expect(headersOf(second)["Idempotency-Key"]).toBe(KEY);
    expect(first?.body).toBe(second?.body);
    expect(headersOf(second)["X-CSRF-Token"]).toBe("FIXTURE-CSRF-2");
  });

  it.each(NON_SUNEUNG)("h. %s 는 요청하지 않고 던진다", async (fortuneType) => {
    const { adapter, fetchMock } = setup();
    await expect(
      adapter.purchase(
        { quoteId: QUOTE_ID, selection: { ...SUNEUNG, fortuneType } },
        KEY,
      ),
    ).rejects.toThrow();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("h. SUNEUNG + counterpartPersonId 도 요청하지 않고 던진다", async () => {
    const { adapter, fetchMock } = setup();
    await expect(
      adapter.purchase(
        {
          quoteId: QUOTE_ID,
          selection: { ...SUNEUNG, counterpartPersonId: OTHER_ID },
        },
        KEY,
      ),
    ).rejects.toThrow();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("h. 키가 UUID 가 아니면 요청하지 않고 던진다 (I-05)", async () => {
    const { adapter, fetchMock } = setup();
    await expect(
      adapter.purchase(
        { quoteId: QUOTE_ID, selection: SUNEUNG },
        "not-a-uuid-key",
      ),
    ).rejects.toThrow();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("i. 503 READING_FULFILLMENT_UNAVAILABLE 는 ApiError 로 code 가 그대로다", async () => {
    const { adapter } = setup(failure(503, "READING_FULFILLMENT_UNAVAILABLE"));
    const error = await adapter
      .purchase({ quoteId: QUOTE_ID, selection: SUNEUNG }, KEY)
      .catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).code).toBe("READING_FULFILLMENT_UNAVAILABLE");
  });
});

describe("운세 adapter — 상품 목록", () => {
  it("h. listProducts 는 요청하지 않고 던진다 (Q-28)", async () => {
    const { adapter, fetchMock } = setup();
    await expect(adapter.listProducts("SUNEUNG")).rejects.toThrow();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
