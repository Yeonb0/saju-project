import { describe, expect, it, vi } from "vitest";
import { createFakeAccount } from "@/mocks/account";
import {
  resolveFortuneScenario,
  resolveSessionScenario,
  resolveTopUpScenario,
  selectFortunePort,
  selectPaymentLauncher,
  selectPersonPort,
  selectSessionPort,
  selectTopUpPort,
} from "./index";

describe("포트 선택 (MOCK-PORT)", () => {
  it("진짜 모드는 구현이 없어 던진다 — 조용히 가짜로 바꾸지 않는다", () => {
    expect(() => selectTopUpPort("real", undefined)).toThrow();
    expect(() => selectPaymentLauncher("real", vi.fn())).toThrow();
  });

  it("가짜 모드는 가짜 구현을 준다", async () => {
    const port = selectTopUpPort("mock", undefined);
    await expect(port.listTopUpProducts()).resolves.not.toHaveLength(0);
  });

  it("시나리오: 비우면 credited, 모르는 값은 던진다", () => {
    expect(resolveTopUpScenario(undefined)).toBe("credited");
    expect(resolveTopUpScenario("")).toBe("credited");
    expect(resolveTopUpScenario("stuck_paid")).toBe("stuck_paid");
    expect(() => resolveTopUpScenario("stuck")).toThrow();
  });

  it("가짜 결제창은 서버 주문 값 그대로 /pay/success 로 보낸다", async () => {
    const navigate = vi.fn();
    await selectPaymentLauncher("mock", navigate).requestPayment({
      // 픽스처일 뿐이며 실제 가격 · 규칙과 무관하다
      orderId: "33333333-3333-4333-8333-333333333333",
      orderName: "FIXTURE",
      amount: { currency: "KRW", amount: 1111 },
      status: "PAYMENT_PENDING",
    });
    const url = new URL(navigate.mock.calls[0][0], "http://localhost");
    expect(url.pathname).toBe("/pay/success");
    expect(url.searchParams.get("orderId")).toBe(
      "33333333-3333-4333-8333-333333333333",
    );
    expect(url.searchParams.get("amount")).toBe("1111");
    expect(url.searchParams.get("paymentKey")).toBeTruthy();
  });
});

describe("세션 포트 선택 (MOCK-PORT)", () => {
  it("진짜 모드는 구현이 없어 던진다", () => {
    expect(() => selectSessionPort("real", undefined)).toThrow();
  });

  it("시나리오: 비우면 signed_out, 모르는 값은 던진다", () => {
    expect(resolveSessionScenario(undefined)).toBe("signed_out");
    expect(resolveSessionScenario("")).toBe("signed_out");
    expect(resolveSessionScenario("new_user")).toBe("new_user");
    expect(() => resolveSessionScenario("guest")).toThrow();
  });
});

describe("인물 포트 선택 (MOCK-PORT)", () => {
  it("진짜 모드는 구현이 없어 던진다", () => {
    expect(() =>
      selectPersonPort("real", createFakeAccount("signed_in")),
    ).toThrow();
  });
});

describe("운세 구매 포트 선택 (MOCK-PORT)", () => {
  it("진짜 모드는 구현이 없어 던진다", () => {
    expect(() => selectFortunePort("real", undefined)).toThrow();
  });

  it("시나리오: 비우면 fulfilled, 모르는 값은 던진다", () => {
    expect(resolveFortuneScenario(undefined)).toBe("fulfilled");
    expect(resolveFortuneScenario("")).toBe("fulfilled");
    expect(resolveFortuneScenario("quote_expired")).toBe("quote_expired");
    expect(() => resolveFortuneScenario("expired")).toThrow();
  });
});
