import { QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Component, type ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/errors";
import { loginHref } from "@/lib/auth/returnTo";
import type {
  FortunePort,
  FortuneQuote,
  FortuneSelection,
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
        onOpenChange={vi.fn()}
        selection={SELECTION}
        targetName="FIXTURE"
        optionLabel="FIXTURE 옵션"
        resumeQuoteId={options.resumeQuoteId}
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
    listTopUpProducts,
    reportMismatch,
  };
}

const useButton = () => screen.findByRole("button", { name: "사용하기" });

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
    expect(onPurchased.mock.calls[0][0].status).toBe("FULFILLED");
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
      price: { currency: "TURTLE_SHELL", amount: 13 },
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
    price: { currency: "TURTLE_SHELL", amount: 13 },
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
