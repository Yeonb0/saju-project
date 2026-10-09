import { describe, expect, it } from "vitest";
import { loginHref, onboardingHref, safeReturnTo } from "./returnTo";

describe("safeReturnTo (A-02 · 오픈 리다이렉트 방지)", () => {
  it.each([
    "/wallet",
    "/fortune/r/abc?x=1#h",
    "/me/people/new",
  ])("내부 경로는 그대로 — %s", (path) => {
    expect(safeReturnTo(path)).toBe(path);
  });

  it("접두어만 같은 다른 경로는 통과", () => {
    expect(safeReturnTo("/loginx")).toBe("/loginx");
  });

  it.each([
    ["null", null],
    ["undefined", undefined],
    ["빈 문자열", ""],
    ["슬래시 없음", "wallet"],
    ["프로토콜 상대", "//evil.example"],
    ["슬래시 + 역슬래시", "/\\evil.example"],
    ["역슬래시 둘", "\\\\evil.example"],
    ["절대 URL", "https://evil.example"],
    ["javascript:", "javascript:alert(1)"],
    ["앞 공백", " /wallet"],
    ["개행", "/wal\nlet"],
    ["/login", "/login"],
    ["/login + 쿼리", "/login?returnTo=/wallet"],
    ["/login 아래", "/login/x"],
    ["/onboarding", "/onboarding"],
    ["/api", "/api/v1/wallet"],
    ["길이 2049", `/${"a".repeat(2048)}`],
  ])("막고 / 로 — %s", (_name, raw) => {
    expect(safeReturnTo(raw)).toBe("/");
  });
});

describe("loginHref · onboardingHref", () => {
  it("returnTo 를 검사한 뒤 인코딩한다", () => {
    expect(loginHref("/wallet?x=1")).toBe("/login?returnTo=%2Fwallet%3Fx%3D1");
    expect(loginHref("//evil.example")).toBe("/login?returnTo=%2F");
    expect(onboardingHref("/wallet")).toBe("/onboarding?returnTo=%2Fwallet");
    expect(onboardingHref("//evil.example")).toBe("/onboarding?returnTo=%2F");
  });
});
