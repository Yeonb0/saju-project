import { afterEach, describe, expect, it, vi } from "vitest";
import {
  loadPurchaseSelection,
  savePurchaseSelection,
} from "@/lib/purchase/restore";
import { logout } from "./logout";

afterEach(() => sessionStorage.clear());

// 픽스처일 뿐이며 실제 상품 · 규칙과 무관하다
const save = () =>
  savePurchaseSelection({
    returnPath: "/suneung",
    quoteId: "fixture-quote",
    selection: {
      productCode: "FIXTURE_SUNEUNG_READING_WITH_TALISMAN",
      personId: "66666666-6666-4666-8666-666666666666",
      counterpartPersonId: null,
    },
  });

describe("logout (PURCHASE-RESTORE)", () => {
  it("세션 로그아웃을 부르고 구매 선택을 지운다", async () => {
    save();
    const port = { logout: vi.fn(async () => {}) };
    await logout(port);
    expect(port.logout).toHaveBeenCalledTimes(1);
    expect(loadPurchaseSelection()).toBeNull();
  });

  it("서버 로그아웃이 실패해도 구매 선택은 이미 지워져 있다", async () => {
    save();
    const port = { logout: vi.fn(async () => Promise.reject(new Error("x"))) };
    await expect(logout(port)).rejects.toThrow();
    expect(loadPurchaseSelection()).toBeNull();
  });
});
