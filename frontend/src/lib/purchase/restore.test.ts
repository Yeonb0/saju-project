// @vitest-environment jsdom
// 브라우저 저장소(sessionStorage · localStorage)를 쓰는 테스트라 jsdom 에서 돈다 (vitest.config.mts — .ts 는 기본 node).
import { afterEach, describe, expect, it } from "vitest";
import {
  clearPurchaseSelection,
  loadPurchaseSelection,
  PURCHASE_SELECTION_KEY,
  PURCHASE_SELECTION_TTL_MS,
  type PurchaseSelection,
  savePurchaseSelection,
} from "./restore";

// 픽스처일 뿐이며 실제 상품 · 규칙과 무관하다
const VALUE: PurchaseSelection = {
  returnPath: "/suneung",
  quoteId: "77777777-7777-4777-8777-777777777777",
  selection: {
    fortuneType: "SUNEUNG",
    productCode: "FIXTURE_SUNEUNG_READING_WITH_TALISMAN",
    personId: "66666666-6666-4666-8666-666666666666",
    counterpartPersonId: null,
    productOption: null,
    questionKey: null,
    relationType: null,
  },
  orderKey: null,
  orderId: null,
  fulfillmentKey: null,
};
// 픽스처 시각
const T0 = 1_000_000;

afterEach(() => sessionStorage.clear());

describe("구매 선택 복원 (PURCHASE-RESTORE)", () => {
  it("저장한 값을 그대로 읽는다", () => {
    savePurchaseSelection(VALUE, T0);
    expect(loadPurchaseSelection(T0 + 1)).toEqual(VALUE);
  });

  it("인물 ID · 최소 선택값만 저장한다 — 섞여 들어온 값은 버린다", () => {
    savePurchaseSelection(
      {
        ...VALUE,
        // 호출하는 쪽 실수로 섞인 값 (픽스처)
        selection: { ...VALUE.selection, birthDate: "2008-01-01" },
        price: 13,
      } as unknown as PurchaseSelection,
      T0,
    );
    const raw = sessionStorage.getItem(PURCHASE_SELECTION_KEY) ?? "";
    expect(raw).not.toContain("birthDate");
    expect(raw).not.toContain("price");
  });

  it("마지막 변경 후 24시간이 지나면 읽지 않고 지운다", () => {
    savePurchaseSelection(VALUE, T0);
    expect(loadPurchaseSelection(T0 + PURCHASE_SELECTION_TTL_MS - 1)).toEqual(
      VALUE,
    );
    expect(loadPurchaseSelection(T0 + PURCHASE_SELECTION_TTL_MS)).toBeNull();
    expect(sessionStorage.getItem(PURCHASE_SELECTION_KEY)).toBeNull();
  });

  it("다시 저장하면 만료 기준이 새로 시작한다", () => {
    savePurchaseSelection(VALUE, T0);
    savePurchaseSelection(VALUE, T0 + PURCHASE_SELECTION_TTL_MS - 10);
    expect(loadPurchaseSelection(T0 + PURCHASE_SELECTION_TTL_MS + 10)).toEqual(
      VALUE,
    );
  });

  it("모양이 다르거나 JSON 이 아니면 지우고 null", () => {
    sessionStorage.setItem(PURCHASE_SELECTION_KEY, "{not json");
    expect(loadPurchaseSelection(T0)).toBeNull();
    expect(sessionStorage.getItem(PURCHASE_SELECTION_KEY)).toBeNull();

    sessionStorage.setItem(
      PURCHASE_SELECTION_KEY,
      JSON.stringify({ v: 2, savedAt: T0 }),
    );
    expect(loadPurchaseSelection(T0)).toBeNull();
    expect(sessionStorage.getItem(PURCHASE_SELECTION_KEY)).toBeNull();
  });

  it("미래 시각에 저장된 값은 믿지 않는다", () => {
    savePurchaseSelection(VALUE, T0 + 1000);
    expect(loadPurchaseSelection(T0)).toBeNull();
  });

  it("돌아갈 경로는 내부 경로만 — 바깥 주소는 / 로", () => {
    savePurchaseSelection({ ...VALUE, returnPath: "https://evil.example" }, T0);
    expect(loadPurchaseSelection(T0)?.returnPath).toBe("/");
  });

  it("clear 하면 지워진다", () => {
    savePurchaseSelection(VALUE, T0);
    clearPurchaseSelection();
    expect(loadPurchaseSelection(T0)).toBeNull();
  });
});

describe("구매 선택 복원 — fortuneType (v: 2)", () => {
  const rawOf = (v: number, selection: Record<string, unknown>) =>
    JSON.stringify({
      v,
      savedAt: T0,
      returnPath: "/suneung",
      quoteId: VALUE.quoteId,
      selection,
    });
  const { fortuneType: _ft, ...WITHOUT_TYPE } = VALUE.selection;

  it("n. fortuneType 을 넣어 저장하고 다시 읽으면 같다", () => {
    savePurchaseSelection(VALUE, T0);
    expect(loadPurchaseSelection(T0)?.selection.fortuneType).toBe("SUNEUNG");
    expect(loadPurchaseSelection(T0)).toEqual(VALUE);
  });

  it("o. v: 1 모양 저장값은 null 이고 지운다", () => {
    sessionStorage.setItem(
      PURCHASE_SELECTION_KEY,
      rawOf(1, { ...WITHOUT_TYPE }),
    );
    expect(loadPurchaseSelection(T0)).toBeNull();
    expect(sessionStorage.getItem(PURCHASE_SELECTION_KEY)).toBeNull();
  });

  it("p. fortuneType 이 UNKNOWN 이거나 없으면 null 이다", () => {
    sessionStorage.setItem(
      PURCHASE_SELECTION_KEY,
      rawOf(2, { ...WITHOUT_TYPE, fortuneType: "UNKNOWN" }),
    );
    expect(loadPurchaseSelection(T0)).toBeNull();
    sessionStorage.setItem(PURCHASE_SELECTION_KEY, rawOf(2, WITHOUT_TYPE));
    expect(loadPurchaseSelection(T0)).toBeNull();
  });
});

describe("직접결제 주문 키 저장 (DIRECT-PAY, v: 5)", () => {
  // 픽스처일 뿐이다
  const KEY = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";

  it("R1. orderKey · orderId · fulfillmentKey 를 넣어 저장하면 그대로 읽힌다", () => {
    const full = {
      ...VALUE,
      orderKey: KEY,
      orderId: "cccccccc-cccc-4ccc-8ccc-cccccccccccc",
      fulfillmentKey: "dddddddd-dddd-4ddd-8ddd-dddddddddddd",
    };
    savePurchaseSelection(full, T0);
    expect(loadPurchaseSelection(T0 + 1)).toEqual(full);
  });

  it("R1. 키를 생략하면 null 로 저장 · 읽힌다", () => {
    const {
      orderKey: _a,
      orderId: _b,
      fulfillmentKey: _c,
      ...withoutKeys
    } = VALUE;
    savePurchaseSelection(withoutKeys, T0);
    expect(loadPurchaseSelection(T0 + 1)).toMatchObject({
      orderKey: null,
      orderId: null,
      fulfillmentKey: null,
    });
    expect(
      JSON.parse(sessionStorage.getItem(PURCHASE_SELECTION_KEY) ?? "{}"),
    ).toMatchObject({
      v: 5,
      orderKey: null,
      orderId: null,
      fulfillmentKey: null,
    });
  });

  it("R2. v: 4 저장값(purchaseKey)은 읽을 때 지운다", () => {
    sessionStorage.setItem(
      PURCHASE_SELECTION_KEY,
      JSON.stringify({
        v: 4,
        savedAt: T0,
        returnPath: "/suneung",
        quoteId: VALUE.quoteId,
        selection: VALUE.selection,
        purchaseKey: KEY,
      }),
    );
    expect(loadPurchaseSelection(T0 + 1)).toBeNull();
    expect(sessionStorage.getItem(PURCHASE_SELECTION_KEY)).toBeNull();
  });

  it("빈 문자열 키는 저장값 무효 — 읽을 때 지운다", () => {
    sessionStorage.setItem(
      PURCHASE_SELECTION_KEY,
      JSON.stringify({
        v: 5,
        savedAt: T0,
        returnPath: "/suneung",
        quoteId: VALUE.quoteId,
        selection: VALUE.selection,
        orderKey: "",
        orderId: null,
        fulfillmentKey: null,
      }),
    );
    expect(loadPurchaseSelection(T0 + 1)).toBeNull();
    expect(sessionStorage.getItem(PURCHASE_SELECTION_KEY)).toBeNull();
  });

  it("v: 2 저장값은 질문이 없어 되살릴 수 없다 — 읽을 때 지운다 (배포 전 충전 복귀 값은 24시간 안에 사라진다)", () => {
    sessionStorage.setItem(
      PURCHASE_SELECTION_KEY,
      JSON.stringify({
        v: 2,
        savedAt: T0,
        returnPath: "/suneung",
        quoteId: VALUE.quoteId,
        selection: VALUE.selection,
      }),
    );
    expect(loadPurchaseSelection(T0 + 1)).toBeNull();
    expect(sessionStorage.getItem(PURCHASE_SELECTION_KEY)).toBeNull();
  });

  it("24시간 만료는 키가 있어도 같다", () => {
    savePurchaseSelection({ ...VALUE, orderKey: KEY }, T0);
    expect(
      loadPurchaseSelection(T0 + PURCHASE_SELECTION_TTL_MS - 1)?.orderKey,
    ).toBe(KEY);
    expect(loadPurchaseSelection(T0 + PURCHASE_SELECTION_TTL_MS)).toBeNull();
    expect(sessionStorage.getItem(PURCHASE_SELECTION_KEY)).toBeNull();
  });
});

describe("구매 선택 복원 — 질문 선택 (v: 4, Q-26)", () => {
  // 픽스처일 뿐이며 실제 상품 · 규칙과 무관하다
  const PERSON = "66666666-6666-4666-8666-666666666666";
  const OTHER = "99999999-9999-4999-8999-999999999999";
  const general = (
    selection: Partial<PurchaseSelection["selection"]>,
  ): PurchaseSelection => ({
    returnPath: "/fortune/love/questions",
    quoteId: VALUE.quoteId,
    selection: {
      fortuneType: "LOVE",
      productCode: "FIXTURE_LOVE_READING_WITH_TALISMAN",
      personId: PERSON,
      counterpartPersonId: null,
      productOption: "READING_WITH_TALISMAN",
      questionKey: "CURRENT_RELATIONSHIP",
      relationType: null,
      ...selection,
    },
    orderKey: null,
    orderId: null,
    fulfillmentKey: null,
  });
  const compat = general({
    fortuneType: "COMPATIBILITY",
    productCode: "FIXTURE_COMPATIBILITY_READING_ONLY",
    counterpartPersonId: OTHER,
    productOption: "READING_ONLY",
    questionKey: "OVERALL_MATCH",
    relationType: "CRUSH",
  });

  it("d. 일반 운세 · 궁합 · 수능운 각각 세 필드가 저장 · 읽기에서 그대로 돌아온다", () => {
    for (const value of [general({}), compat, VALUE]) {
      savePurchaseSelection(value, T0);
      expect(loadPurchaseSelection(T0 + 1)).toEqual(value);
    }
  });

  const rawOf = (v: number, selection: Record<string, unknown>) =>
    JSON.stringify({
      v,
      savedAt: T0,
      returnPath: "/suneung",
      quoteId: VALUE.quoteId,
      selection,
      ...(v >= 5
        ? { orderKey: null, orderId: null, fulfillmentKey: null }
        : v >= 3
          ? { purchaseKey: null }
          : {}),
    });

  it.each([
    [
      "수능운 + questionKey 있음",
      { ...VALUE.selection, questionKey: "OVERALL_FLOW" },
    ],
    [
      "일반 + questionKey null",
      { ...general({}).selection, questionKey: null },
    ],
    [
      "일반 + 다른 운세의 questionKey",
      { ...general({}).selection, questionKey: "OVERALL_FLOW" },
    ],
    ["궁합 + relationType null", { ...compat.selection, relationType: null }],
    [
      "궁합 아님 + relationType 있음",
      { ...general({}).selection, relationType: "LOVER" },
    ],
    [
      "productOption UNKNOWN",
      { ...general({}).selection, productOption: "UNKNOWN" },
    ],
    [
      "일반 + productOption null",
      { ...general({}).selection, productOption: null },
    ],
  ])("e. %s 는 읽으면 null 이고 저장소에서 사라진다", (_name, selection) => {
    sessionStorage.setItem(PURCHASE_SELECTION_KEY, rawOf(5, selection));
    expect(loadPurchaseSelection(T0 + 1)).toBeNull();
    expect(sessionStorage.getItem(PURCHASE_SELECTION_KEY)).toBeNull();
  });

  it.each([
    4, 3, 2,
  ])("e. v: %i 원문은 읽으면 null 이고 저장소에서 사라진다", (v) => {
    sessionStorage.setItem(PURCHASE_SELECTION_KEY, rawOf(v, VALUE.selection));
    expect(loadPurchaseSelection(T0 + 1)).toBeNull();
    expect(sessionStorage.getItem(PURCHASE_SELECTION_KEY)).toBeNull();
  });
});
