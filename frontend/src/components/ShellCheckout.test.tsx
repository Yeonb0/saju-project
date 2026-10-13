import { QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Component, type ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiContractError, ApiError, ApiNetworkError } from "@/lib/api/errors";
import { loginHref } from "@/lib/auth/returnTo";
import type {
  FortunePort,
  FortuneQuote,
  FortuneSelection,
  ReadingPurchaseState,
} from "@/lib/ports/fortune";
import type { TopUpPort } from "@/lib/ports/topUp";
import {
  loadPurchaseSelection,
  savePurchaseSelection,
} from "@/lib/purchase/restore";
import { makeQueryClient } from "@/lib/queryClient";
import { WALLET_QUERY_KEY } from "@/lib/wallet/query";
import {
  createFakeFortunePort,
  type FakeFortuneScenario,
} from "@/mocks/fortune";
import { createFakeTopUpPort } from "@/mocks/topUp";
import { createFakeWallet } from "@/mocks/wallet";
import { ShellCheckout } from "./ShellCheckout";

const push = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));

afterEach(() => {
  cleanup();
  push.mockReset();
  sessionStorage.clear();
});

// 픽스처일 뿐이며 실제 가격 · 규칙과 무관하다. 상품 · 금액은 src/mocks/fortune.ts 의 픽스처다.
const SELECTION: FortuneSelection = {
  fortuneType: "SUNEUNG",
  productCode: "FIXTURE_SUNEUNG_READING_WITH_TALISMAN",
  personId: "66666666-6666-4666-8666-666666666666",
  counterpartPersonId: null,
};

function setup(
  options: {
    balance?: number;
    scenario?: FakeFortuneScenario;
    port?: FortunePort;
    topUpPort?: TopUpPort;
    resumeQuoteId?: string | null;
    resumePurchaseKey?: string | null;
  } = {},
) {
  const wallet = createFakeWallet(options.balance ?? 100);
  const port =
    options.port ??
    createFakeFortunePort({ wallet, scenario: options.scenario });
  const purchase = vi.spyOn(port, "purchase");
  const createQuote = vi.spyOn(port, "createQuote");
  const getQuote = vi.spyOn(port, "getQuote");
  const onPurchased = vi.fn();
  const onOpenChange = vi.fn();
  const topUpPort = options.topUpPort ?? createFakeTopUpPort();
  const listTopUpProducts = vi.spyOn(topUpPort, "listTopUpProducts");
  const reportMismatch = vi.fn();
  const client = makeQueryClient();
  // 잔액 캐시를 심어 둔다 — 구매 뒤 무효화됐는지 본다 (픽스처일 뿐이며 실제 잔액과 무관하다)
  client.setQueryData(WALLET_QUERY_KEY, { balance: 100 });
  render(
    <QueryClientProvider client={client}>
      <ShellCheckout
        open
        onOpenChange={onOpenChange}
        selection={SELECTION}
        targetName="FIXTURE"
        optionLabel="FIXTURE 옵션"
        resumeQuoteId={options.resumeQuoteId}
        resumePurchaseKey={options.resumePurchaseKey}
        returnPath="/suneung"
        onPurchased={onPurchased}
        port={port}
        topUpPort={topUpPort}
        reportMismatch={reportMismatch}
      />
    </QueryClientProvider>,
  );
  return {
    client,
    wallet,
    port,
    purchase,
    createQuote,
    getQuote,
    onPurchased,
    onOpenChange,
    listTopUpProducts,
    reportMismatch,
  };
}

const useButton = () => screen.findByRole("button", { name: "사용하기" });

// 픽스처일 뿐이며 실제 서버 응답과 무관하다
const purchaseState = (
  overrides: Partial<ReadingPurchaseState> = {},
): ReadingPurchaseState => ({
  purchaseId: "FIXTURE",
  readingId: null,
  status: "FAILED",
  compensationStatus: "NOT_REQUIRED",
  nextAction: "RETRY_PURCHASE",
  ...overrides,
});
const purchaseResult = (overrides: Partial<ReadingPurchaseState> = {}) => ({
  ...purchaseState(overrides),
  charged: { currency: "TURTLE_SHELL", amount: 0 },
  balance: null,
});
const REFUND_TEXT = "사용한 등껍질은 돌려드렸습니다";
const SUPPORT_TEXT = "고객센터로 문의해 주세요";

describe("ShellCheckout — 등껍질 차감 확인 (CHECKOUT-POPUP)", () => {
  it("서버 견적 값(상품 · 대상 · 옵션 · 보유 · 사용 · 구매 후)을 그대로 보인다", async () => {
    setup({ balance: 100 });
    await useButton();
    expect(
      screen.getByText("FIXTURE FIXTURE_SUNEUNG_READING_WITH_TALISMAN"),
    ).toBeInTheDocument();
    expect(screen.getByText("FIXTURE 옵션")).toBeInTheDocument();
    expect(screen.getByText("100")).toBeInTheDocument();
    expect(screen.getByText("13 TURTLE_SHELL")).toBeInTheDocument();
    expect(screen.getByText("87")).toBeInTheDocument();
  });

  it("정보 줄의 값(dd)은 긴 값이 상자 안에서 줄바꿈되는 클래스를 갖는다 (LAYOUT-FIGMA)", async () => {
    setup({ balance: 100 });
    await useButton();
    const value = screen.getByText("FIXTURE 옵션");
    expect(value.tagName).toBe("DD");
    expect(value).toHaveClass("[overflow-wrap:anywhere]");
  });

  it("사용하기를 두 번 눌러도 구매 명령은 한 번, 성공하면 결과를 넘기고 복원 값을 지운다", async () => {
    const user = userEvent.setup();
    savePurchaseSelection({
      returnPath: "/suneung",
      quoteId: "fixture",
      selection: SELECTION,
    });
    const { purchase, onPurchased } = setup({ balance: 100 });
    const button = await useButton();
    await user.dblClick(button);
    await waitFor(() => expect(onPurchased).toHaveBeenCalledTimes(1));
    expect(purchase).toHaveBeenCalledTimes(1);
    expect(onPurchased).toHaveBeenCalledWith({
      readingId: expect.any(String),
    });
    expect(loadPurchaseSelection()).toBeNull();
  });

  it("잔액이 모자라면 보유 · 부족(서버 값)과 충전하기 — 선택을 저장하고 /wallet 으로", async () => {
    const user = userEvent.setup();
    setup({ balance: 1 });
    const topUp = await screen.findByRole("button", { name: "충전하기" });
    expect(screen.queryByRole("button", { name: "사용하기" })).toBeNull();
    expect(screen.getByText("1")).toBeInTheDocument();
    expect(screen.getByText("12")).toBeInTheDocument();
    await user.click(topUp);
    expect(push).toHaveBeenCalledWith("/wallet");
    const saved = loadPurchaseSelection();
    expect(saved?.returnPath).toBe("/suneung");
    expect(saved?.selection).toEqual(SELECTION);
  });

  it("충전 후 복귀면 저장한 견적을 재확인하고 새로 만들지 않는다", async () => {
    const wallet = createFakeWallet(1);
    const port = createFakeFortunePort({ wallet });
    const { quoteId } = await port.createQuote(SELECTION);
    wallet.credit(100);
    const { createQuote, getQuote } = setup({ port, resumeQuoteId: quoteId });
    await useButton();
    expect(getQuote).toHaveBeenCalledWith(quoteId);
    expect(createQuote).not.toHaveBeenCalled();
    // 재확인한 서버 잔액
    expect(screen.getByText("101")).toBeInTheDocument();
  });

  it("복귀한 견적이 만료됐으면 선택을 유지한 채 새 견적을 받는다", async () => {
    const port = createFakeFortunePort({ wallet: createFakeWallet(100) });
    vi.spyOn(port, "getQuote").mockRejectedValue(
      new ApiError({ status: 409, code: "QUOTE_EXPIRED", traceId: null }),
    );
    const { createQuote } = setup({ port, resumeQuoteId: "expired" });
    await useButton();
    expect(createQuote).toHaveBeenCalledWith(SELECTION);
  });

  it("구매가 409 QUOTE_EXPIRED 면 새 견적으로 다시 확인받고, 새 견적은 새 키", async () => {
    const user = userEvent.setup();
    const { purchase, createQuote, onPurchased } = setup({
      scenario: "quote_expired",
    });
    await user.click(await useButton());
    await screen.findByRole("alert");
    expect(createQuote).toHaveBeenCalledTimes(2);
    await user.click(await useButton());
    await waitFor(() => expect(onPurchased).toHaveBeenCalledTimes(1));
    expect(purchase).toHaveBeenCalledTimes(2);
    expect(purchase.mock.calls[1][1]).not.toBe(purchase.mock.calls[0][1]);
    expect(purchase.mock.calls[1][0].quoteId).not.toBe(
      purchase.mock.calls[0][0].quoteId,
    );
  });

  it("구매가 409 INSUFFICIENT_BALANCE 면 새 견적의 부족 화면으로", async () => {
    const user = userEvent.setup();
    const port = createFakeFortunePort({ wallet: createFakeWallet(100) });
    vi.spyOn(port, "purchase").mockRejectedValueOnce(
      new ApiError({
        status: 409,
        code: "INSUFFICIENT_BALANCE",
        traceId: null,
      }),
    );
    // 다음 견적은 잔액 부족 (픽스처)
    const createQuote = vi.spyOn(port, "createQuote");
    setup({ port });
    const button = await useButton();
    createQuote.mockResolvedValueOnce({
      quoteId: "fixture-quote",
      productCode: SELECTION.productCode,
      productName: "FIXTURE",
      charged: { currency: "TURTLE_SHELL", amount: 13 },
      walletBalance: 2,
      balanceAfter: null,
      shortage: 11,
      // 추천 없음 — 추천 상품 값(예: 받는 수 11)이 부족분 "11" 단언과 겹치지 않게 (추천 줄은 아래 g~j 에서 본다)
      recommendedTopUp: null,
      expiresAt: "2099-01-01T00:00:00Z",
    });
    await user.click(button);
    expect(
      await screen.findByRole("button", { name: "충전하기" }),
    ).toBeInTheDocument();
    expect(screen.getByText("11")).toBeInTheDocument();
  });

  it("409 IDEMPOTENCY_REQUEST_PROCESSING 이면 사용하기를 다시 열지 않고, 결과 확인은 같은 키로", async () => {
    const user = userEvent.setup();
    const { purchase, onPurchased } = setup({ scenario: "processing_409" });
    await user.click(await useButton());
    const recheck = await screen.findByRole("button", { name: "결과 확인" });
    expect(screen.getByRole("button", { name: "사용하기" })).toBeDisabled();
    await user.click(recheck);
    await waitFor(() => expect(onPurchased).toHaveBeenCalledTimes(1));
    expect(purchase.mock.calls[1][1]).toBe(purchase.mock.calls[0][1]);
    expect(purchase.mock.calls[1][0]).toEqual(purchase.mock.calls[0][0]);
  });

  it("실패 후 같은 견적으로 다시 누르면 같은 키", async () => {
    const user = userEvent.setup();
    const port = createFakeFortunePort({ wallet: createFakeWallet(100) });
    const purchase = vi
      .spyOn(port, "purchase")
      .mockRejectedValueOnce(
        new ApiError({ status: 500, code: "INTERNAL_ERROR", traceId: null }),
      );
    setup({ port });
    await user.click(await useButton());
    await screen.findByRole("alert");
    await user.click(screen.getByRole("button", { name: "사용하기" }));
    await waitFor(() => expect(purchase).toHaveBeenCalledTimes(2));
    expect(purchase.mock.calls[1][1]).toBe(purchase.mock.calls[0][1]);
  });

  it("409 WALLET_REVIEW_REQUIRED 는 다시 시도 없이 문의 안내만 보이고 사용하기가 잠긴다 (Q-41 5)", async () => {
    const user = userEvent.setup();
    const port = createFakeFortunePort({ wallet: createFakeWallet(100) });
    const purchase = vi.spyOn(port, "purchase").mockRejectedValueOnce(
      new ApiError({
        status: 409,
        code: "WALLET_REVIEW_REQUIRED",
        traceId: null,
      }),
    );
    const lookup = vi.spyOn(port, "getPurchaseStatus");
    setup({ port });
    await user.click(await useButton());
    expect(
      await screen.findByText(
        "확인이 필요한 결제가 있어 지금은 사용할 수 없습니다. 고객센터로 문의해 주세요",
      ),
    ).toBeVisible();
    expect(screen.queryByText("구매하지 못했습니다")).toBeNull();
    expect(screen.getByRole("button", { name: "사용하기" })).toBeDisabled();
    expect(purchase).toHaveBeenCalledTimes(1);
    expect(lookup).not.toHaveBeenCalled();
    expect(loadPurchaseSelection()).toBeNull();
  });

  it("생성 실패(FAILED)는 앞 화면에 넘기지 않고 실패 · 다시 시도 안내, 환급 문장 없음", async () => {
    const user = userEvent.setup();
    const { onPurchased, purchase, createQuote } = setup({
      scenario: "generation_failed",
    });
    await user.click(await useButton());
    expect(await screen.findByText("결과를 만들지 못했습니다")).toBeVisible();
    expect(screen.queryByText("사용한 등껍질은 돌려드렸습니다")).toBeNull();
    expect(onPurchased).not.toHaveBeenCalled();

    // 다시 시도는 새 견적 · 새 키
    await user.click(screen.getByRole("button", { name: "다시 시도" }));
    await user.click(await useButton());
    await waitFor(() => expect(purchase).toHaveBeenCalledTimes(2));
    expect(createQuote).toHaveBeenCalledTimes(2);
    expect(purchase.mock.calls[1][1]).not.toBe(purchase.mock.calls[0][1]);
  });

  it("500 READING_GENERATION_FAILED 도 같은 안내", async () => {
    const user = userEvent.setup();
    const port = createFakeFortunePort({ wallet: createFakeWallet(100) });
    vi.spyOn(port, "purchase").mockRejectedValueOnce(
      new ApiError({
        status: 500,
        code: "READING_GENERATION_FAILED",
        traceId: null,
      }),
    );
    // 오류 자체로는 실패 화면을 그리지 않는다 — 자동 상태 조회 결과(FAILED · RETRY_PURCHASE)로 그린다
    vi.spyOn(port, "getPurchaseStatus").mockResolvedValueOnce(
      purchaseState({ status: "FAILED", nextAction: "RETRY_PURCHASE" }),
    );
    setup({ port });
    await user.click(await useButton());
    expect(await screen.findByText("결과를 만들지 못했습니다")).toBeVisible();
    expect(screen.queryByText("구매하지 못했습니다")).toBeNull();
    expect(screen.queryByText("사용한 등껍질은 돌려드렸습니다")).toBeNull();
  });

  it("f. 응답이 REFUNDED 면 실패 · 환급 문장이 보이고 앞 화면에 넘기지 않는다", async () => {
    const user = userEvent.setup();
    const port = createFakeFortunePort({ wallet: createFakeWallet(100) });
    // 픽스처일 뿐이며 실제 구매 응답과 무관하다
    vi.spyOn(port, "purchase").mockResolvedValueOnce({
      purchaseId: "FIXTURE",
      readingId: null,
      status: "REFUNDED",
      compensationStatus: "COMPLETED",
      nextAction: "RETRY_PURCHASE",
      charged: { currency: "TURTLE_SHELL", amount: 0 },
      balance: null,
    });
    const { onPurchased } = setup({ port });
    await user.click(await useButton());
    expect(await screen.findByText("결과를 만들지 못했습니다")).toBeVisible();
    expect(screen.getByText("사용한 등껍질은 돌려드렸습니다")).toBeVisible();
    expect(onPurchased).not.toHaveBeenCalled();
  });

  it("g. GENERATING 응답은 결과를 아직 모름 — 처리 중 · 선택 유지, 결과 확인은 같은 키로 재요청", async () => {
    const user = userEvent.setup();
    const port = createFakeFortunePort({ wallet: createFakeWallet(100) });
    // 픽스처일 뿐이며 실제 구매 응답과 무관하다
    const purchaseSpy = vi.spyOn(port, "purchase").mockResolvedValueOnce({
      purchaseId: "FIXTURE",
      readingId: null,
      status: "GENERATING",
      compensationStatus: "NOT_REQUIRED",
      nextAction: "WAIT",
      charged: { currency: "TURTLE_SHELL", amount: 0 },
      balance: null,
    });
    savePurchaseSelection({
      returnPath: "/suneung",
      quoteId: "FIXTURE-QUOTE",
      selection: SELECTION,
    });
    const { onPurchased } = setup({ port });
    await user.click(await useButton());
    expect(await screen.findByText("처리 중입니다")).toBeVisible();
    expect(onPurchased).not.toHaveBeenCalled();
    expect(loadPurchaseSelection()).not.toBeNull();

    // 두 번째 응답은 가짜 포트의 실제 구현 (FULFILLED + readingId)
    await user.click(screen.getByRole("button", { name: "결과 확인" }));
    await waitFor(() => expect(onPurchased).toHaveBeenCalledTimes(1));
    expect(purchaseSpy.mock.calls[1][1]).toBe(purchaseSpy.mock.calls[0][1]);
    expect(onPurchased.mock.calls[0][0].readingId).toEqual(expect.any(String));
  });

  it("h. FULFILLED 인데 readingId 가 null 이면 넘기지 않고 처리 중", async () => {
    const user = userEvent.setup();
    const port = createFakeFortunePort({ wallet: createFakeWallet(100) });
    // 픽스처일 뿐이며 실제 구매 응답과 무관하다
    vi.spyOn(port, "purchase").mockResolvedValueOnce({
      purchaseId: "FIXTURE",
      readingId: null,
      status: "FULFILLED",
      compensationStatus: "NOT_REQUIRED",
      nextAction: "VIEW_READING",
      charged: { currency: "TURTLE_SHELL", amount: 0 },
      balance: null,
    });
    const { onPurchased } = setup({ port });
    await user.click(await useButton());
    expect(await screen.findByText("처리 중입니다")).toBeVisible();
    expect(onPurchased).not.toHaveBeenCalled();
  });

  it("g2. GENERATING 인데 readingId 가 있어도 넘기지 않고 처리 중", async () => {
    const user = userEvent.setup();
    const port = createFakeFortunePort({ wallet: createFakeWallet(100) });
    // 픽스처일 뿐이며 실제 구매 응답과 무관하다
    vi.spyOn(port, "purchase").mockResolvedValueOnce({
      purchaseId: "FIXTURE",
      readingId: "FIXTURE-READING",
      status: "GENERATING",
      compensationStatus: "NOT_REQUIRED",
      nextAction: "WAIT",
      charged: { currency: "TURTLE_SHELL", amount: 0 },
      balance: null,
    });
    const { onPurchased } = setup({ port });
    await user.click(await useButton());
    expect(await screen.findByText("처리 중입니다")).toBeVisible();
    expect(onPurchased).not.toHaveBeenCalled();
  });

  it("d. 구매가 성공하면 잔액 조회가 무효화된다 (P-09 · Q-22)", async () => {
    const user = userEvent.setup();
    const { client, onPurchased } = setup();
    expect(client.getQueryState(WALLET_QUERY_KEY)?.isInvalidated).toBe(false);
    await user.click(await useButton());
    await waitFor(() => expect(onPurchased).toHaveBeenCalledTimes(1));
    expect(client.getQueryState(WALLET_QUERY_KEY)?.isInvalidated).toBe(true);
  });

  it("e. 생성 실패(환급) 응답 뒤에도 잔액 조회가 무효화된다", async () => {
    const user = userEvent.setup();
    const { client } = setup({ scenario: "generation_failed" });
    await user.click(await useButton());
    await screen.findByText("결과를 만들지 못했습니다");
    expect(client.getQueryState(WALLET_QUERY_KEY)?.isInvalidated).toBe(true);
  });

  it("f. 409 INSUFFICIENT_BALANCE 오류 뒤에는 잔액 조회를 무효화하지 않는다", async () => {
    const user = userEvent.setup();
    const port = createFakeFortunePort({ wallet: createFakeWallet(100) });
    vi.spyOn(port, "purchase").mockRejectedValueOnce(
      new ApiError({
        status: 409,
        code: "INSUFFICIENT_BALANCE",
        traceId: null,
      }),
    );
    const { client } = setup({ port });
    await user.click(await useButton());
    // 새 견적으로 다시 확인받는 안내가 뜰 때까지 기다린다
    await screen.findByRole("alert");
    expect(client.getQueryState(WALLET_QUERY_KEY)?.isInvalidated).toBe(false);
  });

  it("구매 요청이 걸린 동안 생성 대기 장면", async () => {
    const user = userEvent.setup();
    const port = createFakeFortunePort({ wallet: createFakeWallet(100) });
    let release: () => void = () => {};
    const real = port.purchase.bind(port);
    vi.spyOn(port, "purchase").mockImplementationOnce(
      (input, key) =>
        new Promise((resolve) => {
          release = () => resolve(real(input, key));
        }),
    );
    const { onPurchased } = setup({ port });
    await user.click(await useButton());
    expect(await screen.findByRole("status")).toHaveTextContent(
      "결과를 만들고 있습니다",
    );
    expect(screen.getByRole("dialog")).toHaveAttribute("data-layout", "screen");
    release();
    await waitFor(() => expect(onPurchased).toHaveBeenCalledTimes(1));
  });

  it("e. 생성 실패로 끝나면 dialog 배치가 popup 으로 돌아온다", async () => {
    const user = userEvent.setup();
    const port = createFakeFortunePort({ wallet: createFakeWallet(100) });
    let fail: () => void = () => {};
    vi.spyOn(port, "purchase").mockImplementationOnce(
      () =>
        new Promise((_resolve, reject) => {
          fail = () =>
            reject(
              new ApiError({
                status: 500,
                code: "READING_GENERATION_FAILED",
                traceId: null,
              }),
            );
        }),
    );
    // 생성 실패 오류 뒤 자동 상태 조회가 실패 상태를 알려 준다 (PURCHASE-STATUS-UI)
    vi.spyOn(port, "getPurchaseStatus").mockResolvedValueOnce(
      purchaseState({ status: "FAILED", nextAction: "RETRY_PURCHASE" }),
    );
    setup({ port });
    await user.click(await useButton());
    await screen.findByRole("status");
    expect(screen.getByRole("dialog")).toHaveAttribute("data-layout", "screen");
    fail();
    expect(await screen.findByText("결과를 만들지 못했습니다")).toBeVisible();
    expect(screen.getByRole("dialog")).toHaveAttribute("data-layout", "popup");
  });

  // 견적 값은 서버 모양 그대로 주입한다 (픽스처일 뿐이며 실제 가격 · 규칙과 무관)
  const shortQuote = (recommendedTopUp: string | null): FortuneQuote => ({
    quoteId: "fixture-quote",
    productCode: SELECTION.productCode,
    productName: "FIXTURE",
    charged: { currency: "TURTLE_SHELL", amount: 13 },
    walletBalance: 1,
    balanceAfter: null,
    shortage: 12,
    recommendedTopUp,
    expiresAt: "2099-01-01T00:00:00Z",
  });

  describe("잔액 부족 — 추천 충전 상품 (P-06)", () => {
    it("g. 추천 상품의 가격 · 받는 수를 목록 값 그대로 보이고 다른 상품 값은 보이지 않는다", async () => {
      // 잔액 1 → 부족 12 → 가짜 서버 추천은 FIXTURE_TOP_UP_B (2,222 KRW · 받는 수 24)
      setup({ balance: 1 });
      expect(await screen.findByText("2,222 KRW")).toBeInTheDocument();
      expect(screen.getByText("24")).toBeInTheDocument();
      expect(screen.queryByText("1,111 KRW")).toBeNull();
      expect(screen.queryByText("3,333 KRW")).toBeNull();
      expect(screen.queryByText("36")).toBeNull();
    });

    it("h. 추천이 null 이면 추천 줄도 없고 충전 상품 목록 조회도 하지 않는다", async () => {
      // 충전 상품이 하나도 없으면 가짜 서버는 부족한데도 추천 null 을 준다
      const port = createFakeFortunePort({
        wallet: createFakeWallet(1),
        topUpProducts: [],
      });
      const { listTopUpProducts } = setup({ port });
      await screen.findByRole("button", { name: "충전하기" });
      expect(screen.queryByText("추천 충전")).toBeNull();
      expect(listTopUpProducts).not.toHaveBeenCalled();
    });

    it("i. 추천 code 가 목록에 없으면 추천 줄 없이 그 code 로 경고를 한 번 보낸다", async () => {
      const port = createFakeFortunePort({ wallet: createFakeWallet(1) });
      vi.spyOn(port, "createQuote").mockResolvedValue(
        shortQuote("NOT_IN_LIST"),
      );
      const { listTopUpProducts, reportMismatch } = setup({ port });
      await screen.findByRole("button", { name: "충전하기" });
      await waitFor(() => expect(reportMismatch).toHaveBeenCalledTimes(1));
      expect(reportMismatch).toHaveBeenCalledWith("NOT_IN_LIST");
      expect(listTopUpProducts).toHaveBeenCalledTimes(1);
      expect(screen.queryByText("추천 충전")).toBeNull();
    });

    it("j. 추천 code 가 비활성이면 추천 줄 없이 경고를 한 번 보낸다", async () => {
      const port = createFakeFortunePort({ wallet: createFakeWallet(1) });
      vi.spyOn(port, "createQuote").mockResolvedValue(
        shortQuote("FIXTURE_TOP_UP_INACTIVE"),
      );
      const { reportMismatch } = setup({ port });
      await screen.findByRole("button", { name: "충전하기" });
      await waitFor(() => expect(reportMismatch).toHaveBeenCalledTimes(1));
      expect(reportMismatch).toHaveBeenCalledWith("FIXTURE_TOP_UP_INACTIVE");
      expect(screen.queryByText("추천 충전")).toBeNull();
      expect(screen.queryByText("7,777 KRW")).toBeNull();
    });

    it("k. 잔액이 충분하면 충전 상품 목록 조회를 하지 않는다", async () => {
      const { listTopUpProducts, reportMismatch } = setup({ balance: 100 });
      await useButton();
      expect(listTopUpProducts).not.toHaveBeenCalled();
      expect(reportMismatch).not.toHaveBeenCalled();
    });
  });

  describe("견적 조회 실패 (A-03)", () => {
    it("l. 견적 조회가 401 이면 던지지 않고 로그인 링크를 보인다", async () => {
      const port = createFakeFortunePort({ wallet: createFakeWallet(100) });
      vi.spyOn(port, "createQuote").mockRejectedValue(
        new ApiError({ status: 401, code: "UNAUTHENTICATED", traceId: null }),
      );
      setup({ port });
      const link = await screen.findByRole("link", {
        name: "다시 로그인해 주세요",
      });
      expect(link).toHaveAttribute("href", loginHref("/suneung"));
    });

    it("m. 견적 조회가 500 이면 오류 화면으로 던진다", async () => {
      const silence = vi.spyOn(console, "error").mockImplementation(() => {});
      let caught: unknown = null;
      class Boundary extends Component<
        { children: ReactNode },
        { failed: boolean }
      > {
        state = { failed: false };
        static getDerivedStateFromError() {
          return { failed: true };
        }
        componentDidCatch(error: unknown) {
          caught = error;
        }
        render() {
          return this.state.failed ? <p>caught</p> : this.props.children;
        }
      }
      const port = createFakeFortunePort({ wallet: createFakeWallet(100) });
      vi.spyOn(port, "createQuote").mockRejectedValue(
        new ApiError({ status: 500, code: "INTERNAL_ERROR", traceId: null }),
      );
      render(
        <QueryClientProvider client={makeQueryClient()}>
          <Boundary>
            <ShellCheckout
              open
              onOpenChange={vi.fn()}
              selection={SELECTION}
              targetName="FIXTURE"
              optionLabel="FIXTURE 옵션"
              returnPath="/suneung"
              onPurchased={vi.fn()}
              port={port}
            />
          </Boundary>
        </QueryClientProvider>,
      );
      expect(await screen.findByText("caught")).toBeInTheDocument();
      expect(caught).toBeInstanceOf(ApiError);
      expect((caught as ApiError).status).toBe(500);
      silence.mockRestore();
    });
  });
});

describe("ShellCheckout — 상품 줄 (Q-28)", () => {
  // 견적 값은 서버 모양 그대로 주입한다 (픽스처일 뿐이며 실제 가격 · 규칙과 무관)
  const quoteWith = (productName: string | null): FortuneQuote => ({
    quoteId: "fixture-quote",
    productCode: SELECTION.productCode,
    productName,
    charged: { currency: "TURTLE_SHELL", amount: 13 },
    walletBalance: 100,
    balanceAfter: 87,
    shortage: 0,
    recommendedTopUp: null,
    expiresAt: "2099-01-01T00:00:00Z",
  });

  async function setupWith(productName: string | null) {
    const port = createFakeFortunePort({ wallet: createFakeWallet(100) });
    vi.spyOn(port, "createQuote").mockResolvedValue(quoteWith(productName));
    setup({ port });
    await screen.findByRole("button", { name: "사용하기" });
  }

  it("l. productName 이 null 이면 상품 줄이 없고 나머지 줄은 그대로 있다", async () => {
    await setupWith(null);
    expect(screen.queryByText("상품")).toBeNull();
    for (const label of ["대상", "옵션", "보유", "사용"]) {
      expect(screen.getByText(label)).toBeInTheDocument();
    }
    expect(screen.getByText("13 TURTLE_SHELL")).toBeInTheDocument();
  });

  it("m. productName 이 있으면 상품 줄이 그 값을 보인다", async () => {
    await setupWith("FIXTURE 상품");
    expect(screen.getByText("상품")).toBeInTheDocument();
    expect(screen.getByText("FIXTURE 상품")).toBeInTheDocument();
  });
});

describe("ShellCheckout — 구매 상태 조회 (Q-38 · PURCHASE-STATUS-WAIT 해제 · PURCHASE-STATUS-UI)", () => {
  const retryButton = () => screen.queryByRole("button", { name: "다시 시도" });
  const checkButton = () => screen.findByRole("button", { name: "결과 확인" });

  it("fulfilled: 구매 1번, onPurchased 는 { readingId } 로 한 번, 복원 값 삭제, 상태 조회 없음", async () => {
    const user = userEvent.setup();
    savePurchaseSelection({
      returnPath: "/suneung",
      quoteId: "FIXTURE-QUOTE",
      selection: SELECTION,
    });
    const port = createFakeFortunePort({ wallet: createFakeWallet(100) });
    const lookup = vi.spyOn(port, "getPurchaseStatus");
    const { purchase, onPurchased } = setup({ port });
    await user.click(await useButton());
    await waitFor(() => expect(onPurchased).toHaveBeenCalledTimes(1));
    expect(onPurchased).toHaveBeenCalledWith({ readingId: expect.any(String) });
    expect(purchase).toHaveBeenCalledTimes(1);
    expect(lookup).not.toHaveBeenCalled();
    expect(loadPurchaseSelection()).toBeNull();
  });

  it("generating: 처리 중 + 결과 확인 → 누르면 상태 조회 1번 · 구매 재전송 0번 → onPurchased", async () => {
    const user = userEvent.setup();
    const port = createFakeFortunePort({
      scenario: "generating",
      wallet: createFakeWallet(100),
    });
    const lookup = vi.spyOn(port, "getPurchaseStatus");
    const { purchase, onPurchased } = setup({ port });
    await user.click(await useButton());
    expect(await screen.findByText("처리 중입니다")).toBeVisible();
    expect(onPurchased).not.toHaveBeenCalled();
    expect(lookup).not.toHaveBeenCalled();

    await user.click(await checkButton());
    await waitFor(() => expect(onPurchased).toHaveBeenCalledTimes(1));
    expect(onPurchased).toHaveBeenCalledWith({ readingId: expect.any(String) });
    expect(lookup).toHaveBeenCalledTimes(1);
    expect(purchase).toHaveBeenCalledTimes(1);
  });

  it("processing_409: 자동 상태 조회 1번(null) → 처리 중 · 구매 1번. 결과 확인 → null → 같은 키로 재전송 → onPurchased, 구매 2번", async () => {
    const user = userEvent.setup();
    const port = createFakeFortunePort({
      scenario: "processing_409",
      wallet: createFakeWallet(100),
    });
    const lookup = vi.spyOn(port, "getPurchaseStatus");
    const { purchase, onPurchased } = setup({ port });
    await user.click(await useButton());
    expect(await screen.findByText("처리 중입니다")).toBeVisible();
    await waitFor(() => expect(lookup).toHaveBeenCalledTimes(1));
    expect(await lookup.mock.results[0].value).toBeNull();
    expect(purchase).toHaveBeenCalledTimes(1);
    expect(onPurchased).not.toHaveBeenCalled();

    await user.click(await checkButton());
    await waitFor(() => expect(onPurchased).toHaveBeenCalledTimes(1));
    expect(lookup).toHaveBeenCalledTimes(2);
    expect(purchase).toHaveBeenCalledTimes(2);
    expect(purchase.mock.calls[1][1]).toBe(purchase.mock.calls[0][1]);
    expect(purchase.mock.calls[1][0]).toEqual(purchase.mock.calls[0][0]);
  });

  it("구매가 ApiNetworkError → 자동 상태 조회가 FULFILLED → 구매 재전송 없이 onPurchased", async () => {
    const user = userEvent.setup();
    const port = createFakeFortunePort({ wallet: createFakeWallet(100) });
    const purchase = vi
      .spyOn(port, "purchase")
      .mockRejectedValueOnce(new ApiNetworkError());
    const lookup = vi.spyOn(port, "getPurchaseStatus").mockResolvedValueOnce(
      purchaseState({
        status: "FULFILLED",
        readingId: "FIXTURE-READING",
        nextAction: "VIEW_READING",
      }),
    );
    const { onPurchased } = setup({ port });
    await user.click(await useButton());
    await waitFor(() => expect(onPurchased).toHaveBeenCalledTimes(1));
    expect(onPurchased).toHaveBeenCalledWith({ readingId: "FIXTURE-READING" });
    expect(lookup).toHaveBeenCalledTimes(1);
    expect(purchase).toHaveBeenCalledTimes(1);
  });

  it("구매가 500 READING_GENERATION_FAILED → 자동 상태 조회 REFUNDED · COMPLETED · RETRY_PURCHASE → 실패 + 환급 문장 + 다시 시도", async () => {
    const user = userEvent.setup();
    const port = createFakeFortunePort({ wallet: createFakeWallet(100) });
    vi.spyOn(port, "purchase").mockRejectedValueOnce(
      new ApiError({
        status: 500,
        code: "READING_GENERATION_FAILED",
        traceId: null,
      }),
    );
    vi.spyOn(port, "getPurchaseStatus").mockResolvedValueOnce(
      purchaseState({
        status: "REFUNDED",
        compensationStatus: "COMPLETED",
        nextAction: "RETRY_PURCHASE",
      }),
    );
    const { onPurchased } = setup({ port });
    await user.click(await useButton());
    expect(await screen.findByText("결과를 만들지 못했습니다")).toBeVisible();
    expect(screen.getByText(REFUND_TEXT)).toBeVisible();
    expect(retryButton()).not.toBeNull();
    expect(onPurchased).not.toHaveBeenCalled();
  });

  it("생성 실패 오류인데 상태 조회가 null 이면 실패 화면을 그리지 않고 처리 중", async () => {
    const user = userEvent.setup();
    const port = createFakeFortunePort({ wallet: createFakeWallet(100) });
    vi.spyOn(port, "purchase").mockRejectedValueOnce(
      new ApiError({
        status: 500,
        code: "READING_GENERATION_FAILED",
        traceId: null,
      }),
    );
    const lookup = vi.spyOn(port, "getPurchaseStatus");
    setup({ port });
    await user.click(await useButton());
    expect(await screen.findByText("처리 중입니다")).toBeVisible();
    await waitFor(() => expect(lookup).toHaveBeenCalledTimes(1));
    expect(screen.queryByText("결과를 만들지 못했습니다")).toBeNull();
  });

  it("refunded: 환급 문장 + 다시 시도, 잔액 되돌림", async () => {
    const user = userEvent.setup();
    const { wallet, onPurchased } = setup({ scenario: "refunded" });
    await user.click(await useButton());
    expect(await screen.findByText("결과를 만들지 못했습니다")).toBeVisible();
    expect(screen.getByText(REFUND_TEXT)).toBeVisible();
    expect(retryButton()).not.toBeNull();
    expect(wallet.balance()).toBe(100);
    expect(onPurchased).not.toHaveBeenCalled();
  });

  it("REFUNDED · PENDING · RETRY_PURCHASE 는 환급 문장 없이 실패 + 다시 시도", async () => {
    const user = userEvent.setup();
    const port = createFakeFortunePort({ wallet: createFakeWallet(100) });
    vi.spyOn(port, "purchase").mockResolvedValueOnce(
      purchaseResult({
        status: "REFUNDED",
        compensationStatus: "PENDING",
        nextAction: "RETRY_PURCHASE",
      }),
    );
    setup({ port });
    await user.click(await useButton());
    expect(await screen.findByText("결과를 만들지 못했습니다")).toBeVisible();
    expect(screen.queryByText(REFUND_TEXT)).toBeNull();
    expect(retryButton()).not.toBeNull();
  });

  it("review_required: 실패 + 문의 문장, 다시 시도 버튼 없음, 닫기 가능", async () => {
    const user = userEvent.setup();
    const { onOpenChange, onPurchased } = setup({
      scenario: "review_required",
    });
    await user.click(await useButton());
    expect(await screen.findByText("결과를 만들지 못했습니다")).toBeVisible();
    expect(screen.getByText(SUPPORT_TEXT)).toBeVisible();
    expect(retryButton()).toBeNull();
    const close = screen.getByRole("button", { name: "닫기" });
    expect(close).toBeEnabled();
    await user.click(close);
    expect(onOpenChange).toHaveBeenCalledWith(false);
    expect(onPurchased).not.toHaveBeenCalled();
  });

  it("FAILED · PENDING · WAIT (보상 진행 중) 는 실패 화면이 아니라 처리 중", async () => {
    const user = userEvent.setup();
    const port = createFakeFortunePort({ wallet: createFakeWallet(100) });
    vi.spyOn(port, "purchase").mockResolvedValueOnce(
      purchaseResult({
        status: "FAILED",
        compensationStatus: "PENDING",
        nextAction: "WAIT",
      }),
    );
    setup({ port });
    await user.click(await useButton());
    expect(await screen.findByText("처리 중입니다")).toBeVisible();
    expect(screen.queryByText("결과를 만들지 못했습니다")).toBeNull();
    expect(screen.getByRole("button", { name: "결과 확인" })).toBeEnabled();
  });

  it("FAILED · 모르는 nextAction 은 문의 안내 (다시 시도 없음)", async () => {
    const user = userEvent.setup();
    const port = createFakeFortunePort({ wallet: createFakeWallet(100) });
    vi.spyOn(port, "purchase").mockResolvedValueOnce(
      purchaseResult({ status: "FAILED", nextAction: "UNKNOWN" }),
    );
    setup({ port });
    await user.click(await useButton());
    expect(await screen.findByText(SUPPORT_TEXT)).toBeVisible();
    expect(retryButton()).toBeNull();
  });

  it("상태 조회가 500 이면 알림 문장이 나오고 처리 중 · 결과 확인이 남는다", async () => {
    const user = userEvent.setup();
    const port = createFakeFortunePort({
      scenario: "processing_409",
      wallet: createFakeWallet(100),
    });
    vi.spyOn(port, "getPurchaseStatus").mockRejectedValue(
      new ApiError({ status: 500, code: "INTERNAL_ERROR", traceId: null }),
    );
    const { onPurchased } = setup({ port });
    await user.click(await useButton());
    expect(
      await screen.findByText("확인하지 못했습니다. 다시 확인해 주세요"),
    ).toBeVisible();
    expect(screen.getByText("처리 중입니다")).toBeVisible();
    expect(screen.getByRole("button", { name: "결과 확인" })).toBeEnabled();
    expect(onPurchased).not.toHaveBeenCalled();
  });

  it("상태 조회가 계약 위반이면 오류 화면으로 던진다", async () => {
    const silence = vi.spyOn(console, "error").mockImplementation(() => {});
    const user = userEvent.setup();
    let caught: unknown = null;
    class Boundary extends Component<
      { children: ReactNode },
      { failed: boolean }
    > {
      state = { failed: false };
      static getDerivedStateFromError() {
        return { failed: true };
      }
      componentDidCatch(error: unknown) {
        caught = error;
      }
      render() {
        return this.state.failed ? <p>caught</p> : this.props.children;
      }
    }
    const port = createFakeFortunePort({
      scenario: "processing_409",
      wallet: createFakeWallet(100),
    });
    vi.spyOn(port, "getPurchaseStatus").mockRejectedValue(
      new ApiContractError(200, "픽스처"),
    );
    render(
      <QueryClientProvider client={makeQueryClient()}>
        <Boundary>
          <ShellCheckout
            open
            onOpenChange={vi.fn()}
            selection={SELECTION}
            targetName="FIXTURE"
            optionLabel="FIXTURE 옵션"
            returnPath="/suneung"
            onPurchased={vi.fn()}
            port={port}
            topUpPort={createFakeTopUpPort()}
          />
        </Boundary>
      </QueryClientProvider>,
    );
    await user.click(await useButton());
    expect(await screen.findByText("caught")).toBeInTheDocument();
    expect(caught).toBeInstanceOf(ApiContractError);
    silence.mockRestore();
  });

  it("상태 조회로 끝난 뒤에도 잔액 조회가 무효화된다", async () => {
    const user = userEvent.setup();
    const port = createFakeFortunePort({
      scenario: "generating",
      wallet: createFakeWallet(100),
    });
    const { client, onPurchased } = setup({ port });
    await user.click(await useButton());
    await user.click(await checkButton());
    await waitFor(() => expect(onPurchased).toHaveBeenCalledTimes(1));
    expect(client.getQueryState(WALLET_QUERY_KEY)?.isInvalidated).toBe(true);
  });

  it("자동 상태 조회가 null 이면 구매를 다시 보내지 않는다 (반복 방지)", async () => {
    const user = userEvent.setup();
    const port = createFakeFortunePort({ wallet: createFakeWallet(100) });
    vi.spyOn(port, "purchase").mockRejectedValue(
      new ApiError({
        status: 409,
        code: "IDEMPOTENCY_REQUEST_PROCESSING",
        traceId: null,
      }),
    );
    // 실제 네트워크처럼 조회가 늦게 끝난다 — 앞 구매 요청 정리가 끝난 뒤에 null 이 와도 재전송하지 않아야 한다
    const lookup = vi
      .spyOn(port, "getPurchaseStatus")
      .mockImplementation(
        () => new Promise((resolve) => setTimeout(() => resolve(null), 10)),
      );
    const { purchase, onPurchased } = setup({ port });
    await user.click(await useButton());
    await checkButton();
    await waitFor(() => expect(lookup).toHaveBeenCalledTimes(1));
    // 조회 결과가 반영될 시간을 준다 — 이 사이에 재전송이 일어나면 안 된다
    await new Promise((resolve) => setTimeout(resolve, 100));
    expect(purchase).toHaveBeenCalledTimes(1);
    expect(lookup).toHaveBeenCalledTimes(1);
    expect(onPurchased).not.toHaveBeenCalled();
  });

  it("구매 오류 뒤 상태 조회로 끝나도 잔액 조회가 무효화된다", async () => {
    const user = userEvent.setup();
    const port = createFakeFortunePort({ wallet: createFakeWallet(100) });
    vi.spyOn(port, "purchase").mockRejectedValueOnce(new ApiNetworkError());
    vi.spyOn(port, "getPurchaseStatus").mockResolvedValueOnce(
      purchaseState({
        status: "FULFILLED",
        readingId: "FIXTURE-READING",
        nextAction: "VIEW_READING",
      }),
    );
    const { client, onPurchased } = setup({ port });
    await user.click(await useButton());
    await waitFor(() => expect(onPurchased).toHaveBeenCalledTimes(1));
    expect(client.getQueryState(WALLET_QUERY_KEY)?.isInvalidated).toBe(true);
  });

  it("완료를 넘긴 뒤 앞 화면이 새 onPurchased 로 다시 그려도 한 번만 넘긴다", async () => {
    const user = userEvent.setup();
    const port = createFakeFortunePort({ wallet: createFakeWallet(100) });
    const first = vi.fn();
    const second = vi.fn();
    const client = makeQueryClient();
    const view = (onPurchased: (r: { readingId: string }) => void) => (
      <QueryClientProvider client={client}>
        <ShellCheckout
          open
          onOpenChange={vi.fn()}
          selection={SELECTION}
          targetName="FIXTURE"
          optionLabel="FIXTURE 옵션"
          returnPath="/suneung"
          onPurchased={onPurchased}
          port={port}
          topUpPort={createFakeTopUpPort()}
        />
      </QueryClientProvider>
    );
    const { rerender } = render(view(first));
    await user.click(await useButton());
    await waitFor(() => expect(first).toHaveBeenCalledTimes(1));
    rerender(view(second));
    await new Promise((resolve) => setTimeout(resolve, 20));
    expect(first).toHaveBeenCalledTimes(1);
    expect(second).not.toHaveBeenCalled();
  });

  it("처리 중 · 상태 조회 중에는 팝업이 닫히지 않는다", async () => {
    const user = userEvent.setup();
    const port = createFakeFortunePort({
      scenario: "generating",
      wallet: createFakeWallet(100),
    });
    let release: (state: ReadingPurchaseState | null) => void = () => {};
    vi.spyOn(port, "getPurchaseStatus").mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          release = resolve;
        }),
    );
    const { onOpenChange } = setup({ port });
    await user.click(await useButton());
    // 처리 중
    await screen.findByText("처리 중입니다");
    expect(screen.getByRole("button", { name: "닫기" })).toBeDisabled();
    await user.keyboard("{Escape}");
    expect(onOpenChange).not.toHaveBeenCalled();

    // 상태 조회 중
    await user.click(screen.getByRole("button", { name: "결과 확인" }));
    expect(screen.getByRole("button", { name: "결과 확인" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "닫기" })).toBeDisabled();
    await user.keyboard("{Escape}");
    expect(onOpenChange).not.toHaveBeenCalled();
    release(null);
  });
});

describe("ShellCheckout — 구매 키 저장 · 재개 (PURCHASE-KEY-STORE)", () => {
  // 픽스처일 뿐이다
  const KEY = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";

  // 서버에 구매 기록이 있는 견적을 만든다 (setup 의 spy 는 이 호출 뒤에 걸려 횟수에 들어가지 않는다)
  async function boughtQuote(port: FortunePort) {
    const quote = await port.createQuote(SELECTION);
    await port.purchase({ quoteId: quote.quoteId, selection: SELECTION }, KEY);
    return quote.quoteId;
  }

  it("사용하기를 누르면 구매 직전에 저장값에 그 견적 ID 와 그 구매 키가 들어간다", async () => {
    const user = userEvent.setup();
    const port = createFakeFortunePort({
      scenario: "generating",
      wallet: createFakeWallet(100),
    });
    const real = port.purchase.bind(port);
    let seen: ReturnType<typeof loadPurchaseSelection> = null;
    vi.spyOn(port, "purchase").mockImplementationOnce(async (input, key) => {
      seen = loadPurchaseSelection();
      return real(input, key);
    });
    const { purchase } = setup({ port });
    await user.click(await useButton());
    await screen.findByText("처리 중입니다");
    const [input, key] = purchase.mock.calls[0];
    expect(seen).toMatchObject({
      returnPath: "/suneung",
      quoteId: input.quoteId,
      purchaseKey: key,
    });
    // 결과를 모르는 동안은 저장값이 남는다
    expect(loadPurchaseSelection()?.purchaseKey).toBe(key);
  });

  it("재개 · 상태 FULFILLED → 견적 조회와 구매 0번, onPurchased({ readingId }), 저장값 삭제", async () => {
    const port = createFakeFortunePort({ wallet: createFakeWallet(100) });
    const quoteId = await boughtQuote(port);
    savePurchaseSelection({
      returnPath: "/suneung",
      quoteId,
      selection: SELECTION,
      purchaseKey: KEY,
    });
    const { createQuote, getQuote, purchase, onPurchased } = setup({
      port,
      resumeQuoteId: quoteId,
      resumePurchaseKey: KEY,
    });
    await waitFor(() => expect(onPurchased).toHaveBeenCalledTimes(1));
    expect(onPurchased).toHaveBeenCalledWith({ readingId: expect.any(String) });
    expect(createQuote).not.toHaveBeenCalled();
    expect(getQuote).not.toHaveBeenCalled();
    expect(purchase).not.toHaveBeenCalled();
    expect(loadPurchaseSelection()).toBeNull();
  });

  it("재개 · 상태 null → getQuote(resumeQuoteId) 로 견적을 받고, 사용하기는 resumePurchaseKey 로 보낸다", async () => {
    const user = userEvent.setup();
    const port = createFakeFortunePort({ wallet: createFakeWallet(100) });
    const quote = await port.createQuote(SELECTION);
    const lookup = vi.spyOn(port, "getPurchaseStatus");
    const { getQuote, purchase, onPurchased } = setup({
      port,
      resumeQuoteId: quote.quoteId,
      resumePurchaseKey: KEY,
    });
    await user.click(await useButton());
    expect(lookup).toHaveBeenCalledTimes(1);
    expect(lookup).toHaveBeenCalledWith(quote.quoteId);
    expect(getQuote).toHaveBeenCalledWith(quote.quoteId);
    await waitFor(() => expect(onPurchased).toHaveBeenCalledTimes(1));
    expect(purchase).toHaveBeenCalledTimes(1);
    expect(purchase.mock.calls[0][1]).toBe(KEY);
    expect(purchase.mock.calls[0][0].quoteId).toBe(quote.quoteId);
  });

  it("재개 · 상태 GENERATING → 견적 조회 0번, 정보 줄 없이 처리 중 + 결과 확인 → 누르면 상태 조회 → 완료", async () => {
    const user = userEvent.setup();
    const port = createFakeFortunePort({ wallet: createFakeWallet(100) });
    const quoteId = await boughtQuote(port);
    const lookup = vi
      .spyOn(port, "getPurchaseStatus")
      .mockResolvedValueOnce(
        purchaseState({ status: "GENERATING", nextAction: "WAIT" }),
      );
    const { createQuote, getQuote, purchase, onPurchased } = setup({
      port,
      resumeQuoteId: quoteId,
      resumePurchaseKey: KEY,
    });
    expect(await screen.findByText("처리 중입니다")).toBeVisible();
    expect(screen.queryByText("보유")).toBeNull();
    expect(screen.queryByRole("button", { name: "사용하기" })).toBeNull();
    expect(screen.getByRole("button", { name: "닫기" })).toBeDisabled();
    expect(createQuote).not.toHaveBeenCalled();
    expect(getQuote).not.toHaveBeenCalled();
    expect(onPurchased).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: "결과 확인" }));
    await waitFor(() => expect(onPurchased).toHaveBeenCalledTimes(1));
    expect(lookup).toHaveBeenCalledTimes(2);
    expect(purchase).not.toHaveBeenCalled();
    expect(getQuote).not.toHaveBeenCalled();
  });

  it("재개 · 상태 FAILED · RETRY_PURCHASE → 실패 화면, 저장값 삭제", async () => {
    const port = createFakeFortunePort({ wallet: createFakeWallet(100) });
    const quoteId = await boughtQuote(port);
    vi.spyOn(port, "getPurchaseStatus").mockResolvedValueOnce(
      purchaseState({ status: "FAILED", nextAction: "RETRY_PURCHASE" }),
    );
    savePurchaseSelection({
      returnPath: "/suneung",
      quoteId,
      selection: SELECTION,
      purchaseKey: KEY,
    });
    const { onPurchased } = setup({
      port,
      resumeQuoteId: quoteId,
      resumePurchaseKey: KEY,
    });
    expect(await screen.findByText("결과를 만들지 못했습니다")).toBeVisible();
    expect(screen.getByRole("button", { name: "다시 시도" })).toBeEnabled();
    expect(loadPurchaseSelection()).toBeNull();
    expect(onPurchased).not.toHaveBeenCalled();
  });

  it("재개 · 상태 조회 오류 → 처리 중 + 알림 + 결과 확인, 견적은 받지 않는다", async () => {
    const port = createFakeFortunePort({ wallet: createFakeWallet(100) });
    const quoteId = await boughtQuote(port);
    vi.spyOn(port, "getPurchaseStatus").mockRejectedValueOnce(
      new ApiError({ status: 500, code: "INTERNAL_ERROR", traceId: null }),
    );
    const { createQuote, getQuote } = setup({
      port,
      resumeQuoteId: quoteId,
      resumePurchaseKey: KEY,
    });
    expect(
      await screen.findByText("확인하지 못했습니다. 다시 확인해 주세요"),
    ).toBeVisible();
    expect(screen.getByRole("button", { name: "결과 확인" })).toBeEnabled();
    expect(createQuote).not.toHaveBeenCalled();
    expect(getQuote).not.toHaveBeenCalled();
  });

  it("재개가 아닌 팝업은 열 때 상태 조회를 하지 않는다", async () => {
    const port = createFakeFortunePort({ wallet: createFakeWallet(100) });
    const lookup = vi.spyOn(port, "getPurchaseStatus");
    // 견적 ID 만 있고 키가 없는 충전 복귀도 재개가 아니다
    const quote = await port.createQuote(SELECTION);
    setup({ port, resumeQuoteId: quote.quoteId });
    await useButton();
    expect(lookup).not.toHaveBeenCalled();
  });
});
