import { QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Component, type ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/errors";
import type { FortunePort, FortuneQuote } from "@/lib/ports/fortune";
import type { PaymentLauncher } from "@/lib/ports/paymentLauncher";
import type { TopUpPort } from "@/lib/ports/topUp";
import {
  loadPurchaseSelection,
  savePurchaseSelection,
} from "@/lib/purchase/restore";
import { makeQueryClient } from "@/lib/queryClient";
import { createFakeFortunePort } from "@/mocks/fortune";
import { createFakeTopUpPort, FIXTURE_TOP_UP_PRODUCTS } from "@/mocks/topUp";
import { createFakeWallet } from "@/mocks/wallet";
import { WalletScreen } from "./WalletScreen";

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn() }) }));

// vitest 는 globals 를 켜지 않아 Testing Library 자동 정리가 동작하지 않는다.
afterEach(() => {
  cleanup();
  sessionStorage.clear();
});

// 픽스처일 뿐이며 실제 가격 · 규칙과 무관하다. 상품 · 금액은 src/mocks/topUp.ts 의 픽스처다.
function setup(port: TopUpPort = createFakeTopUpPort()) {
  const createOrder = vi.spyOn(port, "createOrder");
  const launcher: PaymentLauncher = { requestPayment: vi.fn(async () => {}) };
  render(
    <QueryClientProvider client={makeQueryClient()}>
      <WalletScreen port={port} launcher={launcher} />
    </QueryClientProvider>,
  );
  return { createOrder, launcher };
}

describe("WalletScreen (PAY-01)", () => {
  it("서버(가짜) 상품 값을 그대로 보여 주고, 비활성 상품은 고를 수 없다", async () => {
    setup();
    const radios = await screen.findAllByRole("radio");
    expect(radios).toHaveLength(7);
    for (const radio of radios.slice(0, 6)) expect(radio).not.toBeDisabled();
    expect(radios[6]).toBeDisabled();
    expect(screen.getByText(/1,111 KRW/)).toBeInTheDocument();
  });

  it("상품을 고르기 전에는 모든 카드가 data-selected=false, 고르면 그 카드만 true (PD 메모 301:165)", async () => {
    const user = userEvent.setup();
    setup();
    const radios = await screen.findAllByRole("radio");
    const cards = radios.map((r) => r.closest("label"));
    for (const card of cards)
      expect(card).toHaveAttribute("data-selected", "false");
    await user.click(radios[1]);
    for (const [i, card] of cards.entries()) {
      expect(card).toHaveAttribute("data-selected", i === 1 ? "true" : "false");
    }
    expect(cards).toHaveLength(7);
  });

  it("k. 보너스 0 상품 카드에는 보너스 줄이 없고, 보너스 상품 카드는 서버 값 그대로 보인다", async () => {
    setup();
    const radios = await screen.findAllByRole("radio");
    const [a, b] = FIXTURE_TOP_UP_PRODUCTS;
    expect(a.bonusAmount).toBe(0);
    expect(b.bonusAmount).toBeGreaterThan(0);
    const fmt = (n: number) => n.toLocaleString("ko-KR");
    expect(radios[0].closest("label")?.textContent).not.toContain("보너스");
    const text = radios[1].closest("label")?.textContent ?? "";
    expect(text).toContain(
      `${fmt(b.paidAmount)} + 보너스 ${fmt(b.bonusAmount)}`,
    );
    expect(text).toContain(fmt(b.creditedAmount));
    expect(text).toContain(`${fmt(b.price.amount)} ${b.price.currency}`);
  });

  it("l. 주문 내용 상자는 없고 안내 · 동의 자리가 하나씩 있으며 체크박스는 없다", async () => {
    const { container } = render(
      <QueryClientProvider client={makeQueryClient()}>
        <WalletScreen
          port={createFakeTopUpPort()}
          launcher={{ requestPayment: vi.fn(async () => {}) }}
        />
      </QueryClientProvider>,
    );
    await screen.findAllByRole("radio");
    expect(container.querySelector('[data-slot="order-total"]')).toBeNull();
    expect(
      container.querySelectorAll('[data-slot="top-up-notice"]'),
    ).toHaveLength(1);
    expect(
      container.querySelectorAll('[data-slot="top-up-agreement"]'),
    ).toHaveLength(1);
    expect(screen.queryByRole("checkbox")).toBeNull();
  });

  it("m. 동의 자리를 건드리지 않아도 상품을 고르면 결제하기가 켜지고 주문이 1회 나간다", async () => {
    const user = userEvent.setup();
    const { createOrder } = setup();
    await user.click((await screen.findAllByRole("radio"))[1]);
    const button = screen.getByRole("button", { name: "결제하기" });
    expect(button).toBeEnabled();
    await user.click(button);
    await waitFor(() => expect(createOrder).toHaveBeenCalledTimes(1));
  });

  it("결제하기 버튼은 하단 CTA 영역 안에 있다 (LAYOUT-FIGMA)", async () => {
    setup();
    await screen.findAllByRole("radio");
    expect(screen.getByTestId("app-cta")).toContainElement(
      screen.getByRole("button", { name: "결제하기" }),
    );
  });

  it("상품을 고르기 전에는 결제할 수 없다", async () => {
    setup();
    await screen.findAllByRole("radio");
    expect(screen.getByRole("button", { name: "결제하기" })).toBeDisabled();
  });

  it("서버 주문 그대로 결제창을 연다", async () => {
    const user = userEvent.setup();
    const { createOrder, launcher } = setup();
    await user.click((await screen.findAllByRole("radio"))[1]);
    await user.click(screen.getByRole("button", { name: "결제하기" }));
    await waitFor(() =>
      expect(launcher.requestPayment).toHaveBeenCalledTimes(1),
    );
    const order = await createOrder.mock.results[0].value;
    expect(launcher.requestPayment).toHaveBeenCalledWith(order);
  });

  it("실패 후 다시 눌러도 같은 상품이면 같은 Idempotency-Key", async () => {
    const user = userEvent.setup();
    const port = createFakeTopUpPort();
    const createOrder = vi
      .spyOn(port, "createOrder")
      .mockRejectedValueOnce(new TypeError("fixture"));
    const launcher: PaymentLauncher = { requestPayment: vi.fn(async () => {}) };
    render(
      <QueryClientProvider client={makeQueryClient()}>
        <WalletScreen port={port} launcher={launcher} />
      </QueryClientProvider>,
    );
    await user.click((await screen.findAllByRole("radio"))[0]);
    await user.click(screen.getByRole("button", { name: "결제하기" }));
    await screen.findByRole("alert");
    await user.click(screen.getByRole("button", { name: "결제하기" }));
    await waitFor(() => expect(createOrder).toHaveBeenCalledTimes(2));
    expect(createOrder.mock.calls[1][1]).toBe(createOrder.mock.calls[0][1]);
  });

  it("다른 상품으로 바꾸면 새 구매 의도라 새 키", async () => {
    const user = userEvent.setup();
    const port = createFakeTopUpPort();
    const createOrder = vi
      .spyOn(port, "createOrder")
      .mockRejectedValueOnce(new TypeError("fixture"));
    const launcher: PaymentLauncher = { requestPayment: vi.fn(async () => {}) };
    render(
      <QueryClientProvider client={makeQueryClient()}>
        <WalletScreen port={port} launcher={launcher} />
      </QueryClientProvider>,
    );
    const radios = await screen.findAllByRole("radio");
    await user.click(radios[0]);
    await user.click(screen.getByRole("button", { name: "결제하기" }));
    await screen.findByRole("alert");
    await user.click(radios[1]);
    await user.click(screen.getByRole("button", { name: "결제하기" }));
    await waitFor(() => expect(createOrder).toHaveBeenCalledTimes(2));
    expect(createOrder.mock.calls[1][1]).not.toBe(createOrder.mock.calls[0][1]);
  });

  it("같은 틱에 두 번 눌러도 주문 · 결제창은 하나", async () => {
    const user = userEvent.setup();
    const { createOrder, launcher } = setup();
    await user.click((await screen.findAllByRole("radio"))[0]);
    const button = screen.getByRole("button", { name: "결제하기" });
    // 렌더 사이 없이 두 번 — 실제 더블 탭 경쟁
    button.click();
    button.click();
    await waitFor(() =>
      expect(launcher.requestPayment).toHaveBeenCalledTimes(1),
    );
    expect(createOrder).toHaveBeenCalledTimes(1);
  });

  describe("판매 중인 충전 상품이 없을 때 (BE-A: PG 준비 전 비활성)", () => {
    const portWith = (products: typeof FIXTURE_TOP_UP_PRODUCTS) => {
      const port = createFakeTopUpPort();
      vi.spyOn(port, "listTopUpProducts").mockResolvedValue(products);
      return port;
    };
    const NOTICE = "판매 중인 충전 상품이 없습니다";

    it("n. 전부 비활성이면 안내만 보이고 라디오 없음 · 결제 버튼 disabled", async () => {
      setup(
        portWith(FIXTURE_TOP_UP_PRODUCTS.map((p) => ({ ...p, active: false }))),
      );
      expect(await screen.findByText(NOTICE)).toBeInTheDocument();
      expect(screen.queryAllByRole("radio")).toHaveLength(0);
      expect(screen.getByRole("button", { name: "결제하기" })).toBeDisabled();
    });

    it("o. 빈 배열이어도 같은 안내", async () => {
      setup(portWith([]));
      expect(await screen.findByText(NOTICE)).toBeInTheDocument();
      expect(screen.queryAllByRole("radio")).toHaveLength(0);
      expect(screen.getByRole("button", { name: "결제하기" })).toBeDisabled();
    });

    it("p. 활성 + 비활성이 섞여 있으면 안내 없이 기존 동작", async () => {
      setup();
      expect(await screen.findAllByRole("radio")).toHaveLength(7);
      expect(screen.queryByText(NOTICE)).toBeNull();
    });
  });

  describe("잔액 부족 팝업에서 왔을 때 (PAY-02 · FORT-04, PURCHASE-RESTORE)", () => {
    // 픽스처일 뿐이며 실제 가격 · 규칙과 무관하다. 가짜 잔액 2 로 부족이 생기게 한다.
    const SELECTION = {
      productCode: "FIXTURE_OVERALL_READING_ONLY",
      personId: "fixture-person",
      counterpartPersonId: null,
    };

    async function makeQuote(balance = 2): Promise<{
      fortune: FortunePort;
      quote: FortuneQuote;
    }> {
      const fortune = createFakeFortunePort({
        wallet: createFakeWallet(balance),
      });
      const quote = await fortune.createQuote(SELECTION);
      return { fortune, quote };
    }

    function saveFor(quote: FortuneQuote, now?: number) {
      savePurchaseSelection(
        {
          returnPath: "/fortune/overall",
          quoteId: quote.quoteId,
          selection: SELECTION,
        },
        now,
      );
    }

    function renderWith(fortune: FortunePort) {
      return render(
        <QueryClientProvider client={makeQueryClient()}>
          <WalletScreen
            port={createFakeTopUpPort()}
            launcher={{ requestPayment: vi.fn(async () => {}) }}
            fortunePort={fortune}
          />
        </QueryClientProvider>,
      );
    }

    const radios = () => screen.getAllByRole("radio") as HTMLInputElement[];
    const checkedCodes = () =>
      radios()
        .filter((r) => r.checked)
        .map((r) => r.value);
    const settle = () => new Promise((r) => setTimeout(r, 50));
    const shortageBlock = (container: HTMLElement) =>
      container.querySelector('[data-slot="shortage"]');

    it("a. 저장된 선택이 없으면 shortage 블록이 없고 getQuote 가 불리지 않는다", async () => {
      const { fortune } = await makeQuote();
      const getQuote = vi.spyOn(fortune, "getQuote");
      const { container } = renderWith(fortune);
      await screen.findAllByRole("radio");
      await settle();
      expect(shortageBlock(container)).toBeNull();
      expect(getQuote).not.toHaveBeenCalled();
    });

    it("b. 부족 견적이면 보유 · 부족 두 줄이 견적 값 그대로 보이고, 기존 보유 줄은 숨는다", async () => {
      const { fortune, quote } = await makeQuote();
      expect(quote.shortage).toBeGreaterThan(0);
      saveFor(quote);
      const { container } = renderWith(fortune);
      await waitFor(() => expect(shortageBlock(container)).not.toBeNull());
      const text = shortageBlock(container)?.textContent ?? "";
      expect(text).toContain(
        `보유 ${quote.walletBalance.toLocaleString("ko-KR")}`,
      );
      expect(text).toContain(`부족 ${quote.shortage.toLocaleString("ko-KR")}`);
      await screen.findAllByRole("radio");
      const holdLines = Array.from(container.querySelectorAll("p")).filter(
        (p) => (p.textContent ?? "").trim().startsWith("보유"),
      );
      expect(holdLines).toHaveLength(1);
    });

    it("c. 견적의 추천 충전 상품이 미리 선택되고 그 카드만 data-selected=true 다", async () => {
      const { fortune, quote } = await makeQuote();
      expect(quote.recommendedTopUp).not.toBeNull();
      saveFor(quote);
      renderWith(fortune);
      await waitFor(() =>
        expect(checkedCodes()).toEqual([quote.recommendedTopUp]),
      );
      const cards = radios().map((r) => r.closest("label"));
      const chosen = radios().findIndex((r) => r.checked);
      expect(chosen).toBeGreaterThanOrEqual(0);
      for (const [i, card] of cards.entries()) {
        expect(card).toHaveAttribute(
          "data-selected",
          i === chosen ? "true" : "false",
        );
      }
    });

    it("d. 추천이 null 이면 아무것도 고르지 않는다", async () => {
      const { fortune, quote } = await makeQuote();
      saveFor(quote);
      vi.spyOn(fortune, "getQuote").mockResolvedValue({
        ...quote,
        recommendedTopUp: null,
      });
      const { container } = renderWith(fortune);
      await waitFor(() => expect(shortageBlock(container)).not.toBeNull());
      await screen.findAllByRole("radio");
      await settle();
      expect(checkedCodes()).toEqual([]);
    });

    it("e. 사용자가 추천과 다른 상품을 고르면 그 선택이 남는다", async () => {
      const user = userEvent.setup();
      const { fortune, quote } = await makeQuote();
      saveFor(quote);
      renderWith(fortune);
      await waitFor(() =>
        expect(checkedCodes()).toEqual([quote.recommendedTopUp]),
      );
      const other = radios().find(
        (r) => r.value !== quote.recommendedTopUp && !r.disabled,
      );
      expect(other).toBeDefined();
      await user.click(other as HTMLInputElement);
      await settle();
      expect(checkedCodes()).toEqual([other?.value]);
    });

    it.each([
      ["409 QUOTE_EXPIRED", 409, "QUOTE_EXPIRED"],
      ["404 not_found", 404, "RESOURCE_NOT_FOUND"],
    ])("f. getQuote 가 %s 이면 부족 표시 없이 PAY-01 그대로 (던지지 않는다)", async (_name, status, code) => {
      const { fortune, quote } = await makeQuote();
      saveFor(quote);
      const getQuote = vi
        .spyOn(fortune, "getQuote")
        .mockRejectedValue(
          new ApiError({ status, code, traceId: "fixture-trace" }),
        );
      const { container } = renderWith(fortune);
      await screen.findAllByRole("radio");
      await waitFor(() => expect(getQuote).toHaveBeenCalled());
      await settle();
      expect(shortageBlock(container)).toBeNull();
      expect(screen.getByText(/^보유/)).toBeInTheDocument();
      expect(checkedCodes()).toEqual([]);
    });

    it("g. getQuote 가 500 이면 오류 경계로 던진다", async () => {
      const silence = vi.spyOn(console, "error").mockImplementation(() => {});
      const { fortune, quote } = await makeQuote();
      saveFor(quote);
      vi.spyOn(fortune, "getQuote").mockRejectedValue(
        new ApiError({
          status: 500,
          code: "INTERNAL_ERROR",
          traceId: "fixture-trace",
        }),
      );
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
      render(
        <QueryClientProvider client={makeQueryClient()}>
          <Boundary>
            <WalletScreen port={createFakeTopUpPort()} fortunePort={fortune} />
          </Boundary>
        </QueryClientProvider>,
      );
      expect(await screen.findByText("caught")).toBeInTheDocument();
      expect(caught).toBeInstanceOf(ApiError);
      silence.mockRestore();
    });

    it("h. shortage 0 인 견적이면 shortage 블록이 없다", async () => {
      const { fortune, quote } = await makeQuote(100);
      expect(quote.shortage).toBe(0);
      saveFor(quote);
      const getQuote = vi.spyOn(fortune, "getQuote");
      const { container } = renderWith(fortune);
      await screen.findAllByRole("radio");
      await waitFor(() => expect(getQuote).toHaveBeenCalled());
      await settle();
      expect(shortageBlock(container)).toBeNull();
      expect(checkedCodes()).toEqual([]);
    });

    it("i. 24시간 넘게 지난 저장값이면 getQuote 가 불리지 않는다", async () => {
      const { fortune, quote } = await makeQuote();
      saveFor(quote, Date.now() - 25 * 60 * 60 * 1000);
      const getQuote = vi.spyOn(fortune, "getQuote");
      const { container } = renderWith(fortune);
      await screen.findAllByRole("radio");
      await settle();
      expect(getQuote).not.toHaveBeenCalled();
      expect(shortageBlock(container)).toBeNull();
    });

    it("j. 화면을 그린 뒤에도 저장값이 그대로 남는다", async () => {
      const { fortune, quote } = await makeQuote();
      saveFor(quote);
      renderWith(fortune);
      await waitFor(() =>
        expect(checkedCodes()).toEqual([quote.recommendedTopUp]),
      );
      expect(loadPurchaseSelection()?.quoteId).toBe(quote.quoteId);
    });
  });

  it("진짜 모드인데 구현이 없으면 조용히 넘어가지 않고 오류 화면으로 던진다", async () => {
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
    // 테스트 환경은 NEXT_PUBLIC_API_MODE 가 비어 있어 진짜 모드다
    render(
      <QueryClientProvider client={makeQueryClient()}>
        <Boundary>
          <WalletScreen />
        </Boundary>
      </QueryClientProvider>,
    );
    expect(
      await screen.findByText("caught", {}, { timeout: 5000 }),
    ).toBeInTheDocument();
    expect(String(caught)).toContain("MOCK-PORT");
    silence.mockRestore();
  });
});
