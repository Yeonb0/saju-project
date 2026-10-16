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
  productOption: null,
  questionKey: null,
  relationType: null,
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
  compensationStatus: "NOT_REQUIRED",
  nextAction: "VIEW_READING",
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

  it.each(
    NON_SUNEUNG,
  )("b. 질문 값이 없는 %s 는 요청하지 않고 던진다", async (fortuneType) => {
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
      compensationStatus: "NOT_REQUIRED",
      nextAction: "VIEW_READING",
      charged: { currency: "TURTLE_SHELL", amount: 13 },
      balance: 7,
    });
  });

  it("e. 응답의 compensationStatus · nextAction 이 결과에 들어간다", async () => {
    const { adapter } = setup(
      ok(
        purchaseData({
          status: "FAILED",
          readingId: null,
          compensationStatus: "REVIEW_REQUIRED",
          nextAction: "CONTACT_SUPPORT",
        }),
      ),
    );
    const result = await adapter.purchase(
      { quoteId: QUOTE_ID, selection: SUNEUNG },
      KEY,
    );
    expect(result.compensationStatus).toBe("REVIEW_REQUIRED");
    expect(result.nextAction).toBe("CONTACT_SUPPORT");
  });

  it("e. 모르는 compensationStatus · nextAction 은 UNKNOWN", async () => {
    const { adapter } = setup(
      ok(purchaseData({ compensationStatus: "LATER", nextAction: "DANCE" })),
    );
    const result = await adapter.purchase(
      { quoteId: QUOTE_ID, selection: SUNEUNG },
      KEY,
    );
    expect(result.compensationStatus).toBe("UNKNOWN");
    expect(result.nextAction).toBe("UNKNOWN");
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
    [
      "compensationStatus 키 없음",
      () => without(purchaseData(), "compensationStatus"),
    ],
    ["nextAction 키 없음", () => without(purchaseData(), "nextAction")],
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

  it.each(
    NON_SUNEUNG,
  )("h. 질문 값이 없는 %s 는 요청하지 않고 던진다", async (fortuneType) => {
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
  const productData = (overrides: Record<string, unknown> = {}) => ({
    code: "FIXTURE_PRODUCT",
    category: "FORTUNE",
    fortuneType: "SUNEUNG",
    option: "READING_ONLY",
    productName: "FIXTURE_PRODUCT_NAME",
    price: { currency: "KRW", amount: 13 },
    regularPrice: { currency: "TURTLE_SHELL", amount: 20 },
    paidAmount: null,
    bonusAmount: null,
    creditedAmount: null,
    active: true,
    saleEndsAt: null,
    ...overrides,
  });

  it.each([
    "SUNEUNG",
    "LOVE",
  ] as const)("%s 요청 URL 이 정확하고 GET, CSRF · 멱등 키 헤더 없음", async (fortuneType) => {
    const { adapter, fetchMock } = setup(
      ok({ items: [productData({ fortuneType })] }),
    );
    await adapter.listProducts(fortuneType);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(
      `/api/v1/products?category=FORTUNE&fortuneType=${fortuneType}`,
    );
    expect(init?.method).toBe("GET");
    expect(headersOf(init)["X-CSRF-Token"]).toBeUndefined();
    expect(headersOf(init)["Idempotency-Key"]).toBeUndefined();
  });

  it("항목 두 개를 포트 모델로 옮기고 서버 순서를 지킨다 — productName · regularPrice · 지급량은 없다", async () => {
    const { adapter } = setup(
      ok({
        items: [
          productData({ code: "FIXTURE_B" }),
          productData({
            code: "FIXTURE_A",
            option: "READING_WITH_TALISMAN",
            active: false,
            saleEndsAt: "2099-01-01T00:00:00Z",
            regularPrice: null,
          }),
        ],
      }),
    );
    await expect(adapter.listProducts("SUNEUNG")).resolves.toEqual([
      {
        code: "FIXTURE_B",
        fortuneType: "SUNEUNG",
        option: "READING_ONLY",
        price: { currency: "KRW", amount: 13 },
        active: true,
        saleEndsAt: null,
      },
      {
        code: "FIXTURE_A",
        fortuneType: "SUNEUNG",
        option: "READING_WITH_TALISMAN",
        price: { currency: "KRW", amount: 13 },
        active: false,
        saleEndsAt: "2099-01-01T00:00:00Z",
      },
    ]);
  });

  it("모르는 option 은 UNKNOWN 으로 남고 성공", async () => {
    const { adapter } = setup(
      ok({ items: [productData({ option: "FIXTURE_NEW_OPTION" })] }),
    );
    const [product] = await adapter.listProducts("SUNEUNG");
    expect(product.option).toBe("UNKNOWN");
  });

  it("items 가 빈 배열이면 []", async () => {
    const { adapter } = setup(ok({ items: [] }));
    await expect(adapter.listProducts("LOVE")).resolves.toEqual([]);
  });

  it("UNKNOWN 인자는 던지고 fetch 안 함", async () => {
    const { adapter, fetchMock } = setup();
    await expect(adapter.listProducts("UNKNOWN")).rejects.toThrow();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it.each([
    ["category TOP_UP", { items: [productData({ category: "TOP_UP" })] }],
    [
      "fortuneType 이 요청과 다름",
      { items: [productData({ fortuneType: "LOVE" })] },
    ],
    [
      "price.currency TURTLE_SHELL (DIRECT-PAY: 등껍질 가격은 계약 위반)",
      {
        items: [
          productData({ price: { currency: "TURTLE_SHELL", amount: 13 } }),
        ],
      },
    ],
    [
      "price.amount 0",
      {
        items: [productData({ price: { currency: "KRW", amount: 0 } })],
      },
    ],
    ["paidAmount 가 숫자", { items: [productData({ paidAmount: 10 })] }],
    ["productName 빈 문자열", { items: [productData({ productName: "" })] }],
    [
      "saleEndsAt 날짜 아님",
      { items: [productData({ saleEndsAt: "not-a-date" })] },
    ],
    ["items 없음", {}],
  ])("계약 위반 (%s) 은 ApiContractError", async (_name, data) => {
    const { adapter } = setup(ok(data));
    await expect(adapter.listProducts("SUNEUNG")).rejects.toBeInstanceOf(
      ApiContractError,
    );
  });
});

describe("운세 adapter — 구매 상태 (getPurchaseStatus)", () => {
  // 픽스처일 뿐이며 실제 서버 응답과 무관하다
  const statusData = (overrides: Record<string, unknown> = {}) => ({
    purchaseId: PURCHASE_ID,
    quoteId: QUOTE_ID,
    readingId: READING_ID,
    status: "FULFILLED",
    compensationStatus: "NOT_REQUIRED",
    nextAction: "VIEW_READING",
    // 포트에 넣지 않는 값 (TODO(Q-38 c))
    failureCode: null,
    createdAt: "2099-01-01T00:00:00Z",
    fulfilledAt: "2099-01-01T00:00:05Z",
    ...overrides,
  });

  it("경로 · 인코딩 · GET · CSRF · 멱등 키 헤더 없음", async () => {
    const { adapter, fetchMock } = setup(ok(statusData({ quoteId: "a/b c" })));
    await adapter.getPurchaseStatus("a/b c").catch(() => undefined);

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/v1/reading-purchases/by-quote/a%2Fb%20c");
    expect(init?.method).toBe("GET");
    expect(init?.body).toBeUndefined();
    expect(headersOf(init)["Idempotency-Key"]).toBeUndefined();
  });

  it("CSRF 토큰을 읽기 요청에는 붙이지 않는다 — 공통 클라이언트 규칙 그대로", async () => {
    const { adapter, fetchMock } = setup(ok(statusData()));
    await adapter.getPurchaseStatus(QUOTE_ID);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`/api/v1/reading-purchases/by-quote/${QUOTE_ID}`);
    expect(headersOf(init)["X-CSRF-Token"]).toBeUndefined();
  });

  it("상태 전체를 옮긴다 — failureCode · createdAt · fulfilledAt 은 없다", async () => {
    const { adapter } = setup(ok(statusData()));
    expect(await adapter.getPurchaseStatus(QUOTE_ID)).toEqual({
      purchaseId: PURCHASE_ID,
      readingId: READING_ID,
      status: "FULFILLED",
      compensationStatus: "NOT_REQUIRED",
      nextAction: "VIEW_READING",
    });
  });

  it("readingId null 은 null 로 남는다", async () => {
    const { adapter } = setup(
      ok(
        statusData({
          readingId: null,
          status: "GENERATING",
          nextAction: "WAIT",
          fulfilledAt: null,
        }),
      ),
    );
    const state = await adapter.getPurchaseStatus(QUOTE_ID);
    expect(state?.readingId).toBeNull();
    expect(state?.nextAction).toBe("WAIT");
  });

  it("모르는 status · compensationStatus · nextAction 은 UNKNOWN", async () => {
    const { adapter } = setup(
      ok(
        statusData({
          status: "PAUSED",
          compensationStatus: "LATER",
          nextAction: "DANCE",
        }),
      ),
    );
    expect(await adapter.getPurchaseStatus(QUOTE_ID)).toMatchObject({
      status: "UNKNOWN",
      compensationStatus: "UNKNOWN",
      nextAction: "UNKNOWN",
    });
  });

  it("404 PURCHASE_NOT_FOUND 는 null", async () => {
    const { adapter } = setup(failure(404, "PURCHASE_NOT_FOUND"));
    expect(await adapter.getPurchaseStatus(QUOTE_ID)).toBeNull();
  });

  it("404 RESOURCE_NOT_FOUND 는 null 이 아니라 ApiError 로 던진다", async () => {
    const { adapter } = setup(failure(404, "RESOURCE_NOT_FOUND"));
    const error = await adapter
      .getPurchaseStatus(QUOTE_ID)
      .catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).code).toBe("RESOURCE_NOT_FOUND");
  });

  it("500 은 ApiError", async () => {
    const { adapter } = setup(failure(500, "INTERNAL_ERROR"));
    const error = await adapter
      .getPurchaseStatus(QUOTE_ID)
      .catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(500);
  });

  it.each([
    ["quoteId 가 요청과 다름", () => statusData({ quoteId: OTHER_ID })],
    ["purchaseId 가 uuid 아님", () => statusData({ purchaseId: "not-uuid" })],
    ["readingId 키 없음", () => without(statusData(), "readingId")],
    ["status 키 없음", () => without(statusData(), "status")],
    [
      "compensationStatus 키 없음",
      () => without(statusData(), "compensationStatus"),
    ],
    ["nextAction 키 없음", () => without(statusData(), "nextAction")],
  ])("계약 위반 (%s) 은 ApiContractError", async (_name, make) => {
    const { adapter } = setup(ok(make()));
    await expect(adapter.getPurchaseStatus(QUOTE_ID)).rejects.toBeInstanceOf(
      ApiContractError,
    );
  });
});

// ---- 일반 운세 5종 (Q-26) ----------------------------------------------------------------
// 아래 질문 키 · 금액 · ID 는 픽스처일 뿐이며 실제 가격 · 규칙과 무관하다 (키는 API_SPEC 8장 표의 값을 쓴다).
type General = Exclude<FortuneType, "UNKNOWN" | "SUNEUNG">;

const STANDARD: readonly (readonly [General, string, string])[] = [
  ["OVERALL", "overall", "OVERALL_FLOW"],
  ["LOVE", "love", "CURRENT_RELATIONSHIP"],
  ["WEALTH", "wealth", "OVERALL_WEALTH"],
  ["SINSAL", "sinsal", "OVERALL_SINSAL"],
];

const generalSelection = (
  fortuneType: General,
  questionKey: string,
): FortuneSelection => ({
  fortuneType,
  productCode: "FIXTURE_GENERAL",
  personId: PERSON_ID,
  counterpartPersonId: null,
  productOption: "READING_WITH_TALISMAN",
  questionKey,
  relationType: null,
});

const COMPAT: FortuneSelection = {
  ...generalSelection("COMPATIBILITY", "COMMUNICATION"),
  counterpartPersonId: OTHER_ID,
  relationType: "FRIEND",
};

// 일반 운세 견적 응답 — event 는 없고 productOption · questionKey 가 있다
const generalQuoteData = (
  selection: FortuneSelection,
  overrides: Record<string, unknown> = {},
) => ({
  ...without(quoteData(), "event"),
  productCode: "FIXTURE_GENERAL",
  productOption: selection.productOption,
  questionKey: selection.questionKey,
  ...overrides,
});

const generalPurchaseData = (overrides: Record<string, unknown> = {}) => ({
  ...purchaseData(),
  // 포트에 넣지 않는 값 — 있어도 성공이어야 한다 (Q-37)
  reading: { summary: "FIXTURE 결과 본문" },
  ...overrides,
});

const GENERAL_PURCHASE_KEYS = [
  "counterpartPersonId",
  "personId",
  "productOption",
  "questionKey",
  "quoteId",
  "relationType",
];

describe("운세 adapter — 일반 운세 견적 (createQuote)", () => {
  it.each(
    STANDARD,
  )("a. %s: POST /api/v1/quotes/fortune/%s 1번, 본문이 정확히 {personId, productOption, questionKey}, 멱등 키 없음", async (fortuneType, path, questionKey) => {
    const selection = generalSelection(fortuneType, questionKey);
    const { adapter, fetchMock } = setup(ok(generalQuoteData(selection)));
    const quote = await adapter.createQuote(selection);

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`/api/v1/quotes/fortune/${path}`);
    expect(init?.method).toBe("POST");
    const body = JSON.parse(init?.body as string);
    // 키 집합까지 — fortuneType · productCode · counterpartPersonId · relationType 은 보내지 않는다
    expect(Object.keys(body).sort()).toEqual([
      "personId",
      "productOption",
      "questionKey",
    ]);
    expect(body).toEqual({
      personId: PERSON_ID,
      productOption: "READING_WITH_TALISMAN",
      questionKey,
    });
    expect(headersOf(init)["Idempotency-Key"]).toBeUndefined();
    expect(headersOf(init)["X-CSRF-Token"]).toBe("FIXTURE-CSRF");
    // 응답을 포트 모델로 — 옵션 · 질문은 포트에 넣지 않는다
    expect(quote).toEqual({
      quoteId: QUOTE_ID,
      productCode: "FIXTURE_GENERAL",
      productName: "FIXTURE_PRODUCT_NAME",
      charged: { currency: "TURTLE_SHELL", amount: 13 },
      walletBalance: 20,
      balanceAfter: 7,
      shortage: 0,
      recommendedTopUp: null,
      expiresAt: "2099-01-01T00:00:00Z",
    });
  });

  it("b. COMPATIBILITY: POST /api/v1/quotes/fortune/compatibility, 본문이 정확히 {personId, counterpartPersonId, relationType, productOption, questionKey}", async () => {
    const { adapter, fetchMock } = setup(ok(generalQuoteData(COMPAT)));
    await adapter.createQuote(COMPAT);

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/v1/quotes/fortune/compatibility");
    const body = JSON.parse(init?.body as string);
    expect(Object.keys(body).sort()).toEqual([
      "counterpartPersonId",
      "personId",
      "productOption",
      "questionKey",
      "relationType",
    ]);
    expect(body).toEqual({
      personId: PERSON_ID,
      counterpartPersonId: OTHER_ID,
      relationType: "FRIEND",
      productOption: "READING_WITH_TALISMAN",
      questionKey: "COMMUNICATION",
    });
    expect(headersOf(init)["Idempotency-Key"]).toBeUndefined();
  });

  const LOVE = generalSelection("LOVE", "CURRENT_RELATIONSHIP");
  it.each([
    [
      "questionKey 키 없음",
      () => without(generalQuoteData(LOVE), "questionKey"),
    ],
    [
      "productOption 키 없음",
      () => without(generalQuoteData(LOVE), "productOption"),
    ],
    [
      "questionKey 가 보낸 값과 다름",
      () => generalQuoteData(LOVE, { questionKey: "MARRIAGE" }),
    ],
    [
      "productOption 이 보낸 값과 다름",
      () => generalQuoteData(LOVE, { productOption: "READING_ONLY" }),
    ],
  ])("c. 응답 위반(%s)은 ApiContractError", async (_name, make) => {
    const { adapter } = setup(ok(make()));
    await expect(adapter.createQuote(LOVE)).rejects.toBeInstanceOf(
      ApiContractError,
    );
  });
});

describe("운세 adapter — 일반 운세 구매 (purchase)", () => {
  it.each([
    ...STANDARD,
    ["COMPATIBILITY", "compatibility", "COMMUNICATION"] as const,
  ])("d. %s: POST /api/v1/reading-purchases/%s, Idempotency-Key 는 받은 것, 본문 키 집합이 규칙과 같다", async (fortuneType, path, questionKey) => {
    const selection =
      fortuneType === "COMPATIBILITY"
        ? COMPAT
        : generalSelection(fortuneType, questionKey);
    const { adapter, fetchMock } = setup(ok(generalPurchaseData(), 201));
    await adapter.purchase({ quoteId: QUOTE_ID, selection }, KEY);

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe(`/api/v1/reading-purchases/${path}`);
    expect(init?.method).toBe("POST");
    expect(headersOf(init)["Idempotency-Key"]).toBe(KEY);
    expect(headersOf(init)["X-CSRF-Token"]).toBe("FIXTURE-CSRF");
    const body = JSON.parse(init?.body as string);
    const expected =
      fortuneType === "COMPATIBILITY"
        ? GENERAL_PURCHASE_KEYS
        : GENERAL_PURCHASE_KEYS.filter(
            (key) => key !== "counterpartPersonId" && key !== "relationType",
          );
    expect(Object.keys(body).sort()).toEqual(expected);
    expect(body.quoteId).toBe(QUOTE_ID);
    expect(body.questionKey).toBe(selection.questionKey);
    expect(body.productOption).toBe("READING_WITH_TALISMAN");
  });

  it("e. 201 FULFILLED 는 그대로 옮기고, reading 객체가 있어도 성공한다", async () => {
    const { adapter } = setup(ok(generalPurchaseData(), 201));
    const result = await adapter.purchase(
      {
        quoteId: QUOTE_ID,
        selection: generalSelection("LOVE", "CURRENT_RELATIONSHIP"),
      },
      KEY,
    );
    expect(result).toEqual({
      purchaseId: PURCHASE_ID,
      readingId: READING_ID,
      status: "FULFILLED",
      compensationStatus: "NOT_REQUIRED",
      nextAction: "VIEW_READING",
      charged: { currency: "TURTLE_SHELL", amount: 13 },
      balance: 7,
    });
  });

  it("e. 202 GENERATING (readingId · balance · charged null) 은 null 로 남는다 (0 이 아님)", async () => {
    const { adapter } = setup(
      ok(
        generalPurchaseData({
          readingId: null,
          balance: null,
          charged: null,
          status: "GENERATING",
          nextAction: "WAIT",
          reading: undefined,
        }),
        202,
      ),
    );
    const result = await adapter.purchase(
      { quoteId: QUOTE_ID, selection: COMPAT },
      KEY,
    );
    expect(result.readingId).toBeNull();
    expect(result.balance).toBeNull();
    expect(result.charged).toBeNull();
    expect(result.status).toBe("GENERATING");
    expect(result.nextAction).toBe("WAIT");
  });

  it("g. 403 CSRF_FAILED 뒤 성공하면 두 요청의 Idempotency-Key · 본문 문자열이 같다", async () => {
    const { adapter, fetchMock } = setup(
      failure(403, "CSRF_FAILED"),
      ok(generalPurchaseData(), 201),
    );
    await adapter.purchase({ quoteId: QUOTE_ID, selection: COMPAT }, KEY);

    expect(fetchMock).toHaveBeenCalledTimes(2);
    const [, first] = fetchMock.mock.calls[0];
    const [, second] = fetchMock.mock.calls[1];
    expect(headersOf(first)["Idempotency-Key"]).toBe(KEY);
    expect(headersOf(second)["Idempotency-Key"]).toBe(KEY);
    expect(first?.body).toBe(second?.body);
    expect(headersOf(second)["X-CSRF-Token"]).toBe("FIXTURE-CSRF-2");
  });

  it.each([
    ["INVALID_QUESTION_KEY"],
    ["RELATION_TYPE_REQUIRED"],
  ])("h. 422 %s 는 ApiError 로 code 가 그대로다 (삼키지 않는다)", async (code) => {
    const { adapter } = setup(failure(422, code));
    const error = await adapter
      .purchase({ quoteId: QUOTE_ID, selection: COMPAT }, KEY)
      .catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).code).toBe(code);
    expect((error as ApiError).status).toBe(422);
  });

  it.each([
    ["INVALID_QUESTION_KEY"],
    ["RELATION_TYPE_REQUIRED"],
  ])("h. 견적 422 %s 도 ApiError 로 code 가 그대로다", async (code) => {
    const { adapter } = setup(failure(422, code));
    const error = await adapter.createQuote(COMPAT).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).code).toBe(code);
  });
});

describe("운세 adapter — 요청 전 선택 검사 (일반 운세 · 수능운)", () => {
  const LOVE = generalSelection("LOVE", "CURRENT_RELATIONSHIP");
  const BAD: readonly (readonly [string, FortuneSelection])[] = [
    ["일반 + questionKey null", { ...LOVE, questionKey: null }],
    [
      "OVERALL + 다른 운세의 키(CURRENT_RELATIONSHIP)",
      generalSelection("OVERALL", "CURRENT_RELATIONSHIP"),
    ],
    ["일반 + productOption null", { ...LOVE, productOption: null }],
    ["COMPATIBILITY + relationType null", { ...COMPAT, relationType: null }],
    [
      "COMPATIBILITY + counterpartPersonId null",
      { ...COMPAT, counterpartPersonId: null },
    ],
    ["LOVE + relationType 있음", { ...LOVE, relationType: "LOVER" }],
    [
      "LOVE + counterpartPersonId 있음",
      { ...LOVE, counterpartPersonId: OTHER_ID },
    ],
    ["SUNEUNG + questionKey 있음", { ...SUNEUNG, questionKey: "OVERALL_FLOW" }],
    [
      "SUNEUNG + productOption 있음",
      { ...SUNEUNG, productOption: "READING_WITH_TALISMAN" },
    ],
  ];

  it.each(
    BAD,
  )("f. %s: createQuote 는 fetch 0번으로 던진다", async (_name, selection) => {
    const { adapter, fetchMock } = setup();
    await expect(adapter.createQuote(selection)).rejects.toThrow();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it.each(
    BAD,
  )("f. %s: purchase 는 fetch 0번으로 던진다", async (_name, selection) => {
    const { adapter, fetchMock } = setup();
    await expect(
      adapter.purchase({ quoteId: QUOTE_ID, selection }, KEY),
    ).rejects.toThrow();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
