import { describe, expect, it } from "vitest";
import { CANONICAL_ORIGIN, WWW_HOST, wwwRedirects } from "./siteDomain";

// SITE-DOMAIN · HOSTING-AMPLIFY. www → 기준 주소 영구 이동 규칙.

describe("wwwRedirects", () => {
  it("1. 규칙 정확히 2개 — host 조건 하나, x-forwarded-host 조건 하나, 둘 다 value 는 WWW_HOST", () => {
    const rules = wwwRedirects();
    expect(rules).toHaveLength(2);
    expect(rules.map((rule) => rule.has)).toEqual([
      [{ type: "host", value: WWW_HOST }],
      [{ type: "header", key: "x-forwarded-host", value: WWW_HOST }],
    ]);
  });

  it("2. 모든 규칙의 destination 은 https://ppugi-saju.kr/:path* 이고 statusCode 301 (permanent 키 없음)", () => {
    for (const rule of wwwRedirects()) {
      expect(rule.destination).toBe("https://ppugi-saju.kr/:path*");
      expect(rule.statusCode).toBe(301);
      expect(rule).not.toHaveProperty("permanent");
    }
  });

  it("3. 모든 규칙의 source 는 /:path* (경로 · 쿼리 보존)", () => {
    for (const rule of wwwRedirects()) {
      expect(rule.source).toBe("/:path*");
    }
  });

  it("4. 기준 주소는 슬래시로 끝나지 않고 www 가 들어 있지 않다", () => {
    expect(CANONICAL_ORIGIN.endsWith("/")).toBe(false);
    expect(CANONICAL_ORIGIN).not.toContain("www");
  });
});
