import { describe, expect, it } from "vitest";
import { resolveApiMode } from "./mode";

describe("resolveApiMode (MOCK-PORT)", () => {
  it("비어 있거나 real 이면 진짜", () => {
    expect(resolveApiMode(undefined, undefined)).toBe("real");
    expect(resolveApiMode("", "preview")).toBe("real");
    expect(resolveApiMode("real", "production")).toBe("real");
  });

  it("mock 은 로컬 · 미리보기에서만", () => {
    expect(resolveApiMode("mock", undefined)).toBe("mock");
    expect(resolveApiMode("mock", "development")).toBe("mock");
    expect(resolveApiMode("mock", "preview")).toBe("mock");
  });

  it("운영 배포의 mock 은 던진다", () => {
    expect(() => resolveApiMode("mock", "production")).toThrow();
  });

  it("알 수 없는 값은 기본값으로 덮지 않고 던진다", () => {
    expect(() => resolveApiMode("Mock", undefined)).toThrow();
    expect(() => resolveApiMode("fake", undefined)).toThrow();
  });
});
