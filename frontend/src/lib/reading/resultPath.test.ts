import { describe, expect, it } from "vitest";
import { readingResultPath } from "./resultPath";

describe("readingResultPath (DIRECT-PAY)", () => {
  it("SUNEUNG 은 /suneung/r/<id>", () => {
    expect(readingResultPath("SUNEUNG", "FIXTURE-1")).toBe(
      "/suneung/r/FIXTURE-1",
    );
  });

  it.each([
    "OVERALL",
    "LOVE",
    "WEALTH",
    "COMPATIBILITY",
    "SINSAL",
  ] as const)("%s 는 /fortune/r/<id>", (type) => {
    expect(readingResultPath(type, "FIXTURE-2")).toBe("/fortune/r/FIXTURE-2");
  });

  it("id 는 인코딩한다", () => {
    expect(readingResultPath("LOVE", "a/b c")).toBe("/fortune/r/a%2Fb%20c");
    expect(readingResultPath("SUNEUNG", "a/b c")).toBe("/suneung/r/a%2Fb%20c");
  });
});
