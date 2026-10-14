import { describe, expect, it } from "vitest";
import { RELATION_TYPES } from "@/lib/ports/fortune";
import { isQuestionKeyOf, QUESTION_SETS, RELATION_QUESTION } from "./questions";

// 키 순서는 FUNCTIONAL_SPEC v0.4 8장 "정보·질문" 표 · API_SPEC 8장 "허용 questionKey" 표와 같다 (리터럴로 단언)
describe("선택 질문 표 (Q-26)", () => {
  it("a. 다섯 운세의 키 목록이 표와 순서까지 같다", () => {
    const keys = (type: keyof typeof QUESTION_SETS) =>
      QUESTION_SETS[type].options.map((option) => option.key);
    expect(keys("OVERALL")).toEqual([
      "OVERALL_FLOW",
      "RELATIONSHIPS",
      "STUDY_AND_WORK",
      "WEALTH",
      "CONDITION",
    ]);
    expect(keys("WEALTH")).toEqual([
      "OVERALL_WEALTH",
      "INCOME",
      "SPENDING",
      "INVESTMENT",
      "CAREER_FINANCE",
    ]);
    expect(keys("SINSAL")).toEqual([
      "OVERALL_SINSAL",
      "RELATIONSHIPS",
      "WORK_AND_STUDY",
      "WEALTH",
    ]);
    expect(keys("LOVE")).toEqual([
      "CURRENT_RELATIONSHIP",
      "NEW_RELATIONSHIP",
      "RECONCILIATION",
      "MARRIAGE",
    ]);
    expect(keys("COMPATIBILITY")).toEqual([
      "OVERALL_MATCH",
      "COMMUNICATION",
      "CONFLICT",
      "LONG_TERM_POTENTIAL",
    ]);
    expect(RELATION_QUESTION.options.map((option) => option.key)).toEqual([
      "LOVER",
      "CRUSH",
      "FRIEND",
      "FAMILY",
      "WORK_SCHOOL",
      "OTHER",
    ]);
  });

  it("b. 운세마다 키가 겹치지 않고 라벨이 비지 않으며, 관계 키는 모두 OpenAPI relationType 이다", () => {
    for (const set of Object.values(QUESTION_SETS)) {
      const keys = set.options.map((option) => option.key);
      expect(new Set(keys).size).toBe(keys.length);
      expect(set.title).not.toBe("");
      for (const option of set.options)
        expect(option.label.trim()).not.toBe("");
    }
    const relationKeys = RELATION_QUESTION.options.map((option) => option.key);
    expect(new Set(relationKeys).size).toBe(relationKeys.length);
    expect([...relationKeys].sort()).toEqual([...RELATION_TYPES].sort());
    for (const option of RELATION_QUESTION.options)
      expect(option.label.trim()).not.toBe("");
  });

  it("c. isQuestionKeyOf 는 그 운세 표에 있는 키만 true", () => {
    expect(isQuestionKeyOf("LOVE", "CURRENT_RELATIONSHIP")).toBe(true);
    expect(isQuestionKeyOf("OVERALL", "CURRENT_RELATIONSHIP")).toBe(false);
    expect(isQuestionKeyOf("SINSAL", "WORK_AND_STUDY")).toBe(true);
    expect(isQuestionKeyOf("OVERALL", "WORK_AND_STUDY")).toBe(false);
    expect(isQuestionKeyOf("SUNEUNG", "OVERALL_FLOW")).toBe(false);
    expect(isQuestionKeyOf("UNKNOWN", "OVERALL_FLOW")).toBe(false);
  });
});
