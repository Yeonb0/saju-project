import { describe, expect, it } from "vitest";
import { makeQueryClient } from "./queryClient";

describe("makeQueryClient", () => {
  it("mutations retry 는 0 이다", () => {
    // 회귀 테스트: 결제 · 주문 요청이 자동 재전송되면 중복 결제가 난다.
    expect(makeQueryClient().getDefaultOptions().mutations?.retry).toBe(0);
  });

  it("queries 기본값", () => {
    const queries = makeQueryClient().getDefaultOptions().queries;
    expect(queries?.retry).toBe(1);
    expect(queries?.staleTime).toBe(30000);
    expect(queries?.refetchOnWindowFocus).toBe(false);
  });

  it("부를 때마다 다른 인스턴스다", () => {
    expect(makeQueryClient()).not.toBe(makeQueryClient());
  });
});
