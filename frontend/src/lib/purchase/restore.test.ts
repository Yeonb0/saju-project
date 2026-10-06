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
    productCode: "FIXTURE_SUNEUNG_READING_WITH_TALISMAN",
    personId: "66666666-6666-4666-8666-666666666666",
    counterpartPersonId: null,
  },
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
