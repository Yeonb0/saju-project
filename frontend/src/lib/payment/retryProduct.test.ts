import { describe, expect, it } from "vitest";
import { readRetryProduct } from "./retryProduct";

// 픽스처일 뿐이며 실제 상품 코드와 무관하다
describe("readRetryProduct (TOPUP-RETRY-URL)", () => {
  it("대문자 · 숫자 · 밑줄 1~64자 문자열은 그대로", () => {
    expect(readRetryProduct("FIXTURE_TOP_UP_X")).toBe("FIXTURE_TOP_UP_X");
    expect(readRetryProduct("A")).toBe("A");
    expect(readRetryProduct("A".repeat(64))).toBe("A".repeat(64));
  });

  it.each([
    ["배열", ["FIXTURE_TOP_UP_X"]],
    ["빈 값", ""],
    ["undefined", undefined],
    ["소문자", "fixture_top_up_x"],
    ["기호", "A&B"],
    ["태그 모양", "x<y"],
    ["공백", "A B"],
    ["65자", "A".repeat(65)],
  ])("%s 는 null", (_name, value) => {
    expect(readRetryProduct(value)).toBeNull();
  });
});
