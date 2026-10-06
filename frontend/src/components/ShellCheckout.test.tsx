import { QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/errors";
import type { FortunePort, FortuneSelection } from "@/lib/ports/fortune";
import {
  loadPurchaseSelection,
  savePurchaseSelection,
} from "@/lib/purchase/restore";
import { makeQueryClient } from "@/lib/queryClient";
import {
  createFakeFortunePort,
  type FakeFortuneScenario,
} from "@/mocks/fortune";
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
  render(
    <QueryClientProvider client={makeQueryClient()}>
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
      />
    </QueryClientProvider>,
  );
  return { wallet, port, purchase, createQuote, getQuote, onPurchased };
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
      recommendedTopUp: "FIXTURE_TOP_UP_A",
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

  it("생성 실패(FAILED · refunded)도 그대로 앞 화면에 넘긴다", async () => {
    const user = userEvent.setup();
    const { onPurchased } = setup({ scenario: "generation_failed" });
    await user.click(await useButton());
    await waitFor(() => expect(onPurchased).toHaveBeenCalledTimes(1));
    expect(onPurchased.mock.calls[0][0]).toMatchObject({
      status: "FAILED",
      refunded: true,
    });
  });
});
