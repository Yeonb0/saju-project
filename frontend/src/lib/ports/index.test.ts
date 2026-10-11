// @vitest-environment jsdom
// MOCK-PANEL 저장값(localStorage)을 읽는 경로를 확인하는 테스트가 있어 jsdom 에서 돈다 (vitest.config.mts — .ts 는 기본 node).
import { afterEach, describe, expect, it, vi } from "vitest";
import { createFakeAccount } from "@/mocks/account";
import { createFakeReadings } from "@/mocks/reading";
import { createFakeWallet } from "@/mocks/wallet";
import {
  resolveFortuneScenario,
  resolveSessionScenario,
  resolveTopUpScenario,
  selectBasicSajuPort,
  selectFortunePort,
  selectPaymentLauncher,
  selectPersonPort,
  selectReadingPort,
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

describe("오행분석 포트 선택 (MOCK-PORT)", () => {
  it("진짜 모드는 구현이 없어 던진다", () => {
    expect(() =>
      selectBasicSajuPort("real", createFakeAccount("signed_in")),
    ).toThrow("MOCK-PORT");
  });

  it("가짜 모드는 가짜 오행 값을 준다", async () => {
    const account = createFakeAccount("signed_in");
    const result = await selectBasicSajuPort("mock", account).getBasicSaju(
      account.getSelf()?.personId ?? "",
    );
    // 픽스처일 뿐이며 실제 계산 · 규칙과 무관하다
    expect(result.fiveElements.map((e) => e.count)).toEqual([2, 1, 3, 0, 2]);
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

describe("결과 포트 선택 (MOCK-PORT)", () => {
  it("진짜 모드는 구현이 없어 던진다", () => {
    expect(() => selectReadingPort("real", createFakeReadings())).toThrow();
  });

  it("가짜 구매가 만든 결과를 가짜 결과 포트로 다시 연다", async () => {
    const readings = createFakeReadings();
    const fortune = selectFortunePort(
      "mock",
      undefined,
      createFakeWallet(100),
      readings,
    );
    const [product] = await fortune.listProducts("SUNEUNG");
    const selection = {
      productCode: product.code,
      // 픽스처일 뿐이며 실제 사용자와 무관하다
      personId: "66666666-6666-4666-8666-666666666666",
      counterpartPersonId: null,
    };
    const { quoteId } = await fortune.createQuote(selection);
    const result = await fortune.purchase({ quoteId, selection }, "key-1");
    expect(result.status).toBe("FULFILLED");
    if (result.readingId === null)
      throw new Error("FIXTURE: 가짜 구매 결과에 readingId 가 없다");
    const reading = await selectReadingPort("mock", readings).getReading(
      result.readingId,
    );
    expect(reading.fortuneType).toBe("SUNEUNG");
  });
});

// MOCK-PANEL: 가짜 모드에서 저장값 → 환경 변수 → 기본값 순서로 고른다.
// API_MODE 는 모듈 상수라 모드를 "mock" 으로 바꾼 채 index 를 새로 불러온다 (싱글턴도 새로 만들어진다).
describe("MOCK-PANEL 저장값 우선순위", () => {
  afterEach(() => {
    localStorage.clear();
    vi.unstubAllEnvs();
    vi.doUnmock("@/lib/ports/mode");
    vi.resetModules();
  });

  async function loadMockPorts() {
    vi.resetModules();
    vi.doMock("@/lib/ports/mode", async (importOriginal) => ({
      ...(await importOriginal<typeof import("./mode")>()),
      API_MODE: "mock",
    }));
    return import("./index");
  }

  it("e. 저장값이 환경 변수보다 우선하고, 저장값이 없으면 환경 변수를 쓴다", async () => {
    vi.stubEnv("NEXT_PUBLIC_MOCK_SESSION_SCENARIO", "signed_in");
    localStorage.setItem(
      "mockOverrides",
      JSON.stringify({ v: 1, session: "signed_out" }),
    );
    const withStored = await loadMockPorts();
    await expect(withStored.getSessionPort().getSession()).resolves.toEqual({
      status: "signed_out",
    });

    localStorage.clear();
    const withEnvOnly = await loadMockPorts();
    await expect(
      withEnvOnly.getSessionPort().getSession(),
    ).resolves.toMatchObject({ status: "signed_in" });
  });

  it("f. 저장값 balance 100 이면 가짜 지갑 시작 잔액이 100", async () => {
    localStorage.setItem(
      "mockOverrides",
      JSON.stringify({ v: 1, balance: 100 }),
    );
    const ports = await loadMockPorts();
    const quote = await ports.getFortunePort().createQuote({
      // 픽스처일 뿐이며 실제 상품 · 사용자와 무관하다
      productCode: "FIXTURE_SUNEUNG_READING_WITH_TALISMAN",
      personId: "66666666-6666-4666-8666-666666666666",
      counterpartPersonId: null,
    });
    expect(quote.walletBalance).toBe(100);
  });
});
