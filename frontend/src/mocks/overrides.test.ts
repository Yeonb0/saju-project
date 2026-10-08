// @vitest-environment jsdom
// 브라우저 저장소(localStorage)를 쓰는 테스트라 jsdom 에서 돈다 (vitest.config.mts — .ts 는 기본 node).
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  clearOverridesFor,
  MOCK_OVERRIDES_KEY,
  readOverridesFor,
  writeOverridesFor,
} from "./overrides";

afterEach(() => {
  localStorage.clear();
  vi.restoreAllMocks();
});

describe("mockOverrides (MOCK-PANEL)", () => {
  it("a. 가짜 모드가 아니면 읽기는 {} 이고 쓰기 · 지우기는 아무것도 하지 않는다", () => {
    localStorage.setItem(
      MOCK_OVERRIDES_KEY,
      JSON.stringify({ v: 1, session: "signed_in" }),
    );
    expect(readOverridesFor("real")).toEqual({});
    writeOverridesFor("real", { topUp: "rejected" });
    clearOverridesFor("real");
    // 진짜 모드의 쓰기 · 지우기는 저장소를 건드리지 않았다
    expect(
      JSON.parse(localStorage.getItem(MOCK_OVERRIDES_KEY) ?? "null"),
    ).toEqual({ v: 1, session: "signed_in" });
    localStorage.clear();
    writeOverridesFor("real", { topUp: "rejected" });
    expect(localStorage.getItem(MOCK_OVERRIDES_KEY)).toBeNull();
  });

  it("b. 올바른 값을 쓰고 읽는다", () => {
    writeOverridesFor("mock", {
      session: "signed_in",
      topUp: "stuck_paid",
      fortune: "quote_expired",
      balance: 100,
    });
    expect(readOverridesFor("mock")).toEqual({
      session: "signed_in",
      topUp: "stuck_paid",
      fortune: "quote_expired",
      balance: 100,
    });
    clearOverridesFor("mock");
    expect(readOverridesFor("mock")).toEqual({});
    expect(localStorage.getItem(MOCK_OVERRIDES_KEY)).toBeNull();
  });

  it("c. 모르는 시나리오 값 · 깨진 JSON 은 키를 지우고 {} 를 준다", () => {
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {});
    localStorage.setItem(
      MOCK_OVERRIDES_KEY,
      JSON.stringify({ v: 1, topUp: "nope" }),
    );
    expect(readOverridesFor("mock")).toEqual({});
    expect(localStorage.getItem(MOCK_OVERRIDES_KEY)).toBeNull();

    localStorage.setItem(MOCK_OVERRIDES_KEY, "{깨진");
    expect(readOverridesFor("mock")).toEqual({});
    expect(localStorage.getItem(MOCK_OVERRIDES_KEY)).toBeNull();
    expect(warn).toHaveBeenCalledTimes(2);
  });

  it("d. 시작 잔액이 허용 목록(0 · 7 · 100) 밖이면 {} 를 주고 키를 지운다", () => {
    vi.spyOn(console, "warn").mockImplementation(() => {});
    localStorage.setItem(
      MOCK_OVERRIDES_KEY,
      JSON.stringify({ v: 1, balance: 50 }),
    );
    expect(readOverridesFor("mock")).toEqual({});
    expect(localStorage.getItem(MOCK_OVERRIDES_KEY)).toBeNull();
  });
});
