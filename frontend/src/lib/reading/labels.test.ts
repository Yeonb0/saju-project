import { describe, expect, it } from "vitest";
import { PREPARATION_CATEGORIES } from "@/lib/ports/reading";
import {
  EXAM_PERIOD_LABELS,
  labelOf,
  PREPARATION_GROUPS,
  SECTION_TITLES,
  suneungSummaryTitle,
} from "./labels";

describe("labelOf (RESULT-GUIDE-MODEL)", () => {
  it("아는 코드는 이름, 모르는 코드는 null", () => {
    expect(labelOf(EXAM_PERIOD_LABELS, "KOREAN")).toBe("1교시 국어");
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

describe("수능 요약 제목 · 준비물 묶음 (FUNCTIONAL_SPEC 8장 v0.4)", () => {
  it("요약 제목에 인물 이름이 들어간다", () => {
    // 픽스처일 뿐이며 실제 이름과 무관하다
    expect(suneungSummaryTitle("FIXTURE")).toBe("FIXTURE님의 수능 운세");
  });

  it("준비물 묶음 제목이 PD 순서 그대로다", () => {
    expect(PREPARATION_GROUPS.map((group) => group.title)).toEqual([
      "꼭 챙기기",
      "시험장 소지품",
      "도시락 · 간식",
      "옷차림",
      "그 밖에 챙길 것",
    ]);
  });

  it("알려진 분류는 모두 정확히 한 묶음에 들어 있다", () => {
    const grouped = PREPARATION_GROUPS.flatMap((group) => group.categories);
    expect([...grouped].sort()).toEqual([...PREPARATION_CATEGORIES].sort());
  });
});
