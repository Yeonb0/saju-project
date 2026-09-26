import { describe, expect, it } from "vitest";
import { kst } from "@/lib/date";

describe("kst", () => {
  it("UTC 자정 직전을 한국 날짜로 환산한다", () => {
    // 2026-11-18T23:00Z = 2026-11-19 08:00 KST (수능 당일)
    expect(kst("2026-11-18T23:00:00Z").format("YYYY-MM-DD HH:mm")).toBe(
      "2026-11-19 08:00",
    );
  });

  it("기기 시간대와 무관하게 같은 값을 준다", () => {
    const utcInput = "2026-10-31T15:00:00Z";
    expect(kst(utcInput).format("YYYY-MM-DD")).toBe("2026-11-01");
  });
});
