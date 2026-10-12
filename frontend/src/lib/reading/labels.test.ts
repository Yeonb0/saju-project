import { describe, expect, it } from "vitest";
import { EXAM_PERIOD_LABELS, labelOf, SECTION_TITLES } from "./labels";

describe("labelOf (RESULT-GUIDE-MODEL)", () => {
  it("아는 코드는 이름, 모르는 코드는 null", () => {
    expect(labelOf(EXAM_PERIOD_LABELS, "KOREAN")).toBe("국어");
    expect(labelOf(EXAM_PERIOD_LABELS, "FIXTURE_PERIOD")).toBeNull();
  });

  it.each([
    "constructor",
    "toString",
    "__proto__",
    "hasOwnProperty",
  ])("객체 기본 키 %s 는 이름으로 착각하지 않는다", (key) => {
    expect(labelOf(EXAM_PERIOD_LABELS, key)).toBeNull();
    expect(labelOf(SECTION_TITLES, key)).toBeNull();
  });
});
