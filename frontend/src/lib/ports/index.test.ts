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

// 진짜 adapter 가 전역 fetch 로 요청하는지 본다 — 테스트마다 되돌린다
function stubFetch(data: unknown) {
  const fetchMock = vi.fn<typeof fetch>().mockImplementation(
    async () =>
      new Response(JSON.stringify({ data, traceId: "fixture-trace" }), {
        status: 200,
        headers: { "content-type": "application/json" },
      }),
  );
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("포트 선택 (MOCK-PORT)", () => {
  it("진짜 충전 포트는 서버 adapter를 쓰고, 미구현 결제창은 조용히 가짜로 바꾸지 않는다", () => {
    expect(selectTopUpPort("real", undefined)).toMatchObject({
      getWallet: expect.any(Function),
      createOrder: expect.any(Function),
    });
  });

  describe("결제창 선택 (TOSS-LAUNCHER-SWITCH)", () => {
    const ORDER = {
      // 픽스처일 뿐이며 실제 가격 · 규칙과 무관하다
      orderId: "33333333-3333-4333-8333-333333333333",
      orderName: "FIXTURE",
      amount: { currency: "KRW", amount: 1111 },
      status: "PAYMENT_PENDING" as const,
    };

    // 픽스처일 뿐이며 실제 상품 코드와 무관하다
    const OPTIONS = { retryProductCode: "FIXTURE_TOP_UP_X" };

    // 토스 결제창은 키가 없으면 SDK 를 부르기 전에 던진다 — 가짜 결제창과 구별하는 데 쓴다
    async function opensToss(
      launcher: ReturnType<typeof selectPaymentLauncher>,
    ) {
      const error = await launcher
        .requestPayment(ORDER, OPTIONS)
        .catch((e: unknown) => e);
      return error instanceof Error && error.message.includes("TOSS-SDK");
    }

    it.each([
      undefined,
      "",
      "fake",
    ])("h. 가짜 모드 %j 는 가짜 결제창 — navigate 로 /pay/success", async (raw) => {
      const navigate = vi.fn();
      await selectPaymentLauncher("mock", navigate, raw).requestPayment(
        ORDER,
        OPTIONS,
      );
      expect(navigate.mock.calls[0][0]).toContain("/pay/success");
    });

    it("h. 가짜 모드 toss 는 토스 결제창", async () => {
      const navigate = vi.fn();
      expect(
        await opensToss(selectPaymentLauncher("mock", navigate, "toss")),
      ).toBe(true);
      expect(navigate).not.toHaveBeenCalled();
    });

    it("h. 가짜 모드 bogus 는 던진다", () => {
      expect(() => selectPaymentLauncher("mock", vi.fn(), "bogus")).toThrow();
    });

    it("i. 진짜 모드는 토스 결제창 — 선택할 때는 던지지 않는다", async () => {
      const navigate = vi.fn();
      const launcher = selectPaymentLauncher("real", navigate);
      expect(await opensToss(launcher)).toBe(true);
      expect(navigate).not.toHaveBeenCalled();
    });
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
    await selectPaymentLauncher("mock", navigate).requestPayment(
      {
        // 픽스처일 뿐이며 실제 가격 · 규칙과 무관하다
        orderId: "33333333-3333-4333-8333-333333333333",
        orderName: "FIXTURE",
        amount: { currency: "KRW", amount: 1111 },
        status: "PAYMENT_PENDING",
      },
      { retryProductCode: "FIXTURE_TOP_UP_X" },
    );
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
  it("진짜 모드는 서버 세션 adapter를 준다", () => {
    expect(selectSessionPort("real", undefined)).toMatchObject({
      getSession: expect.any(Function),
      startLogin: expect.any(Function),
      logout: expect.any(Function),
    });
  });

  it("진짜 세션은 한 번만 만든다 — 세션 · 충전 · 인물 adapter 가 같은 CSRF 토큰을 본다", () => {
    expect(selectSessionPort("real", undefined)).toBe(
      selectSessionPort("real", undefined),
    );
  });

  it("시나리오: 비우면 signed_out, 모르는 값은 던진다", () => {
    expect(resolveSessionScenario(undefined)).toBe("signed_out");
    expect(resolveSessionScenario("")).toBe("signed_out");
    expect(resolveSessionScenario("new_user")).toBe("new_user");
    expect(() => resolveSessionScenario("guest")).toThrow();
  });
});

describe("인물 포트 선택 (MOCK-PORT)", () => {
  it("진짜 모드는 서버 adapter를 준다", () => {
    expect(
      selectPersonPort("real", createFakeAccount("signed_in")),
    ).toMatchObject({
      list: expect.any(Function),
      createSelf: expect.any(Function),
      createOther: expect.any(Function),
    });
  });
});

describe("오행분석 포트 선택 (MOCK-PORT)", () => {
  it("진짜 모드는 서버로 요청한다 — 가짜로 바꾸지 않는다", async () => {
    const fetchMock = stubFetch({ items: [] });
    await selectBasicSajuPort("real", createFakeAccount("signed_in"))
      .getBasicSaju("66666666-6666-4666-8666-666666666666")
      .catch(() => {});
    expect(fetchMock.mock.calls[0]?.[0]).toBe("/api/v1/fortune/basic");
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
  it("진짜 모드는 서버로 요청한다 — 가짜로 바꾸지 않는다", async () => {
    const fetchMock = stubFetch({ items: [] });
    await expect(
      selectFortunePort("real", undefined).listProducts("SUNEUNG"),
    ).resolves.toEqual([]);
    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      "/api/v1/products?category=FORTUNE&fortuneType=SUNEUNG",
    );
  });

  it("시나리오: 비우면 fulfilled, 모르는 값은 던진다", () => {
    expect(resolveFortuneScenario(undefined)).toBe("fulfilled");
    expect(resolveFortuneScenario("")).toBe("fulfilled");
    expect(resolveFortuneScenario("quote_expired")).toBe("quote_expired");
    expect(() => resolveFortuneScenario("expired")).toThrow();
  });
});

describe("결과 포트 선택 (MOCK-PORT)", () => {
  it("진짜 모드는 서버로 요청한다 — 가짜로 바꾸지 않는다", async () => {
    const fetchMock = stubFetch({});
    await selectReadingPort("real", createFakeReadings())
      .getReading("66666666-6666-4666-8666-666666666666")
      .catch(() => {});
    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      "/api/v1/readings/66666666-6666-4666-8666-666666666666",
    );
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
      fortuneType: "SUNEUNG" as const,
      productCode: product.code,
      // 픽스처일 뿐이며 실제 사용자와 무관하다
      personId: "66666666-6666-4666-8666-666666666666",
      counterpartPersonId: null,
      productOption: null,
      questionKey: null,
      relationType: null,
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
      fortuneType: "SUNEUNG" as const,
      productCode: "FIXTURE_SUNEUNG_READING_WITH_TALISMAN",
      personId: "66666666-6666-4666-8666-666666666666",
      counterpartPersonId: null,
      productOption: null,
      questionKey: null,
      relationType: null,
    });
    expect(quote.walletBalance).toBe(100);
  });
});
