import { QueryClientProvider } from "@tanstack/react-query";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Component, type ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/errors";
import { BUSINESS_INFO } from "@/lib/business";
import type { FortunePort, FortuneQuote } from "@/lib/ports/fortune";
import {
  PaymentCanceledError,
  type PaymentLauncher,
} from "@/lib/ports/paymentLauncher";
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

const fmtWon = (n: number) => n.toLocaleString("ko-KR");
// 고른 상품 기준 결제 버튼 이름 (PAY-01 「N원 결제하기」) — 가격은 픽스처 서버 값에서 가져온다. 인덱스는 FIXTURE_TOP_UP_PRODUCTS 순서
const payName = (index: number) =>
  `${fmtWon(FIXTURE_TOP_UP_PRODUCTS[index].price.amount)}원 결제하기`;
const AGREEMENT = "주문 내용과 환불정책을 확인했으며 결제에 동의합니다. (필수)";
// 결제하기를 누르는 테스트는 먼저 필수 동의를 체크한다 (Q-25)
const agree = (user: ReturnType<typeof userEvent.setup>) =>
  user.click(screen.getByRole("checkbox", { name: AGREEMENT }));
// 「문의하기」 링크의 메일 제목 (mailto 의 subject 를 푼다)
const mailSubject = (link: HTMLElement) =>
  decodeURIComponent(
    (link.getAttribute("href") ?? "").split("?subject=")[1] ?? "",
  );

describe("WalletScreen — 다시 결제하기 상품 유지 (TOPUP-RETRY-URL, FUNCTIONAL_SPEC 7장)", () => {
  // 픽스처일 뿐이며 실제 상품 코드 · 가격과 무관하다
  const renderRetry = (retryProductCode: string | null) => {
    const port = createFakeTopUpPort();
    const launcher: PaymentLauncher = { requestPayment: vi.fn(async () => {}) };
    render(
      <QueryClientProvider client={makeQueryClient()}>
        <WalletScreen
          port={port}
          launcher={launcher}
          retryProductCode={retryProductCode}
        />
      </QueryClientProvider>,
    );
    return { port, launcher };
  };
  const checkedRadios = () =>
    (screen.getAllByRole("radio") as HTMLInputElement[])
      .filter((r) => r.checked)
      .map((r) => r.value);

  it("판매 중인 상품이면 처음부터 골라져 있고, 동의는 미리 체크하지 않아 결제하기는 꺼져 있다", async () => {
    renderRetry("FIXTURE_TOP_UP_C");
    await waitFor(() => expect(checkedRadios()).toEqual(["FIXTURE_TOP_UP_C"]));
    expect(screen.getByRole("checkbox", { name: AGREEMENT })).not.toBeChecked();
    expect(screen.getByRole("button", { name: payName(2) })).toBeDisabled();
  });

  it.each([
    ["비활성 상품", "FIXTURE_TOP_UP_INACTIVE"],
    ["목록에 없는 코드", "FIXTURE_NOT_IN_LIST"],
  ])("%s 이면 아무것도 고르지 않고 오류도 아니다", async (_name, code) => {
    renderRetry(code);
    await screen.findAllByRole("radio");
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(checkedRadios()).toEqual([]);
    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("결제하기 → requestPayment 두 번째 인자는 고른 상품 code", async () => {
    const user = userEvent.setup();
    const { launcher } = renderRetry("FIXTURE_TOP_UP_C");
    await waitFor(() => expect(checkedRadios()).toEqual(["FIXTURE_TOP_UP_C"]));
    await agree(user);
    await user.click(screen.getByRole("button", { name: payName(2) }));
    await waitFor(() =>
      expect(launcher.requestPayment).toHaveBeenCalledTimes(1),
    );
    expect(vi.mocked(launcher.requestPayment).mock.calls[0][1]).toEqual({
      retryProductCode: "FIXTURE_TOP_UP_C",
    });
  });
});

describe("WalletScreen (PAY-01)", () => {
  it("서버(가짜) 상품 값을 그대로 보여 주고, 비활성 상품은 고를 수 없다", async () => {
    setup();
    const radios = await screen.findAllByRole("radio");
    expect(radios).toHaveLength(7);
    for (const radio of radios.slice(0, 6)) expect(radio).not.toBeDisabled();
    expect(radios[6]).toBeDisabled();
    expect(
      screen.getByText(`${fmtWon(FIXTURE_TOP_UP_PRODUCTS[0].price.amount)}원`),
    ).toBeInTheDocument();
    expect(screen.queryByText(/KRW/)).toBeNull();
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
    expect(text).toContain(`${fmt(b.price.amount)}원`);
  });

  it("l. 주문 내용 상자는 없고 안내 · 동의 자리가 하나씩 있으며 동의 체크박스는 처음에 꺼져 있다", async () => {
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
    expect(screen.getByRole("checkbox", { name: AGREEMENT })).not.toBeChecked();
  });

  it("m. 동의하고 상품을 고르면 결제하기가 켜지고 주문이 1회 나간다", async () => {
    const user = userEvent.setup();
    const { createOrder } = setup();
    await user.click((await screen.findAllByRole("radio"))[1]);
    await agree(user);
    const button = screen.getByRole("button", { name: payName(1) });
    expect(button).toBeEnabled();
    await user.click(button);
    await waitFor(() => expect(createOrder).toHaveBeenCalledTimes(1));
  });

  it("안내 상자에 다섯 문장이 글자 그대로 보이고, 환불정책 보기 링크는 /refund", async () => {
    setup();
    await screen.findAllByRole("radio");
    for (const line of [
      "등껍질은 결제 승인 즉시 지급돼요. 유효기간은 유료 5년, 보너스 1년이에요.",
      "유효기간이 먼저 끝나는 등껍질부터 사용돼요.",
      "충전 후 7일 이내, 그 충전의 등껍질을 하나도 쓰지 않았다면 고객센터로 신청해 전액 환불받을 수 있어요.",
      "남은 유료 등껍질 환불도 고객센터 이메일로 신청해 주세요.",
      "미성년자는 법정대리인 동의를 받아 결제해 주세요. 동의 없이 결제하면 취소될 수 있어요.",
    ]) {
      expect(screen.getByText(line)).toBeVisible();
    }
    expect(
      screen.getByRole("link", { name: "환불정책 보기 >" }),
    ).toHaveAttribute("href", "/refund");
  });

  describe("필수 동의 (Q-25)", () => {
    it("상품을 골라도 동의 전에는 결제하기가 꺼져 있고, 체크하면 켜지고, 해제하면 다시 꺼진다", async () => {
      const user = userEvent.setup();
      setup();
      await user.click((await screen.findAllByRole("radio"))[1]);
      const button = screen.getByRole("button", { name: payName(1) });
      expect(button).toBeDisabled();
      await agree(user);
      expect(button).toBeEnabled();
      await agree(user);
      expect(button).toBeDisabled();
    });

    it("동의 전에 버튼을 강제로 눌러도 주문 생성 요청은 0번", async () => {
      const user = userEvent.setup();
      const { createOrder, launcher } = setup();
      await user.click((await screen.findAllByRole("radio"))[1]);
      const button = screen.getByRole("button", { name: payName(1) });
      expect(button).toBeDisabled();
      // 비활성 버튼에 click 이벤트를 직접 보내 onPay 방어를 본다
      fireEvent.click(button);
      await new Promise((resolve) => setTimeout(resolve, 50));
      expect(createOrder).not.toHaveBeenCalled();
      expect(launcher.requestPayment).not.toHaveBeenCalled();
    });

    it("동의 문구는 label 로 체크박스와 연결되고, 저장소에 남기지 않는다", async () => {
      const user = userEvent.setup();
      setup();
      await screen.findAllByRole("radio");
      const checkbox = screen.getByRole("checkbox", { name: AGREEMENT });
      expect(checkbox).not.toBeChecked();
      await user.click(screen.getByText(AGREEMENT));
      expect(checkbox).toBeChecked();
      expect(localStorage.length).toBe(0);
      expect(sessionStorage.length).toBe(0);
    });
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

  it("a. 상품을 고르면 버튼 이름이 「{가격}원 결제하기」, 다른 상품으로 바꾸면 그 상품 가격으로 바뀐다 (PAY-01)", async () => {
    const user = userEvent.setup();
    setup();
    const radios = await screen.findAllByRole("radio");
    await user.click(radios[0]);
    expect(
      screen.getByRole("button", { name: payName(0) }),
    ).toBeInTheDocument();
    await user.click(radios[1]);
    expect(
      screen.getByRole("button", { name: payName(1) }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: payName(0) })).toBeNull();
  });

  it("서버 주문 그대로 결제창을 연다", async () => {
    const user = userEvent.setup();
    const { createOrder, launcher } = setup();
    await user.click((await screen.findAllByRole("radio"))[1]);
    await agree(user);
    await user.click(screen.getByRole("button", { name: payName(1) }));
    await waitFor(() =>
      expect(launcher.requestPayment).toHaveBeenCalledTimes(1),
    );
    const order = await createOrder.mock.results[0].value;
    // 두 번째 인자: 주문을 만든 상품 코드 (TOPUP-RETRY-URL) — 2번째 카드는 FIXTURE_TOP_UP_B
    expect(launcher.requestPayment).toHaveBeenCalledWith(order, {
      retryProductCode: "FIXTURE_TOP_UP_B",
    });
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
    await agree(user);
    await user.click(screen.getByRole("button", { name: payName(0) }));
    await screen.findByRole("alert");
    await user.click(screen.getByRole("button", { name: payName(0) }));
    await waitFor(() => expect(createOrder).toHaveBeenCalledTimes(2));
    expect(createOrder.mock.calls[1][1]).toBe(createOrder.mock.calls[0][1]);
  });

  it("j. 결제창이 PaymentCanceledError 로 끝나면 오류 안내 없이 결제하기가 다시 눌린다", async () => {
    const user = userEvent.setup();
    const port = createFakeTopUpPort();
    const launcher: PaymentLauncher = {
      requestPayment: vi.fn().mockRejectedValueOnce(new PaymentCanceledError()),
    };
    render(
      <QueryClientProvider client={makeQueryClient()}>
        <WalletScreen port={port} launcher={launcher} />
      </QueryClientProvider>,
    );
    await user.click((await screen.findAllByRole("radio"))[0]);
    await agree(user);
    await user.click(screen.getByRole("button", { name: payName(0) }));
    await waitFor(() =>
      expect(launcher.requestPayment).toHaveBeenCalledTimes(1),
    );
    await waitFor(() =>
      expect(screen.getByRole("button", { name: payName(0) })).toBeEnabled(),
    );
    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("k. 결제창을 닫은 뒤 다시 누르면 createOrder 가 같은 Idempotency-Key 로 불린다", async () => {
    const user = userEvent.setup();
    const port = createFakeTopUpPort();
    const createOrder = vi.spyOn(port, "createOrder");
    const launcher: PaymentLauncher = {
      requestPayment: vi.fn().mockRejectedValueOnce(new PaymentCanceledError()),
    };
    render(
      <QueryClientProvider client={makeQueryClient()}>
        <WalletScreen port={port} launcher={launcher} />
      </QueryClientProvider>,
    );
    await user.click((await screen.findAllByRole("radio"))[0]);
    await agree(user);
    await user.click(screen.getByRole("button", { name: payName(0) }));
    await waitFor(() => expect(createOrder).toHaveBeenCalledTimes(1));
    await waitFor(() =>
      expect(screen.getByRole("button", { name: payName(0) })).toBeEnabled(),
    );
    await user.click(screen.getByRole("button", { name: payName(0) }));
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
    await agree(user);
    await user.click(screen.getByRole("button", { name: payName(0) }));
    await screen.findByRole("alert");
    await user.click(radios[1]);
    await user.click(screen.getByRole("button", { name: payName(1) }));
    await waitFor(() => expect(createOrder).toHaveBeenCalledTimes(2));
    expect(createOrder.mock.calls[1][1]).not.toBe(createOrder.mock.calls[0][1]);
  });

  it("409 WALLET_REVIEW_REQUIRED 는 다시 시도 없이 문의 안내만 보이고 결제하기가 잠긴다 (Q-41 5)", async () => {
    const user = userEvent.setup();
    const port = createFakeTopUpPort();
    vi.spyOn(port, "createOrder").mockRejectedValueOnce(
      new ApiError({
        status: 409,
        code: "WALLET_REVIEW_REQUIRED",
        traceId: null,
      }),
    );
    const { createOrder, launcher } = setup(port);
    await user.click((await screen.findAllByRole("radio"))[1]);
    await agree(user);
    await user.click(screen.getByRole("button", { name: payName(1) }));
    const title = await screen.findByText("확인이 필요한 구매예요");
    expect(title).toBeVisible();
    // 푸터에도 같은 이메일이 있어 안내 블록 안에서만 본다
    const block = title.parentElement as HTMLElement;
    expect(
      within(block).getByText("고객센터로 문의해 주시면 빠르게 도와드릴게요."),
    ).toBeVisible();
    expect(within(block).getByText(BUSINESS_INFO.email ?? "")).toBeVisible();
    // 주문이 만들어지기 전 거절이라 번호 없음
    expect(mailSubject(screen.getByRole("link", { name: "문의하기" }))).toBe(
      "[뿌기사주 문의]",
    );
    expect(screen.queryByText("결제를 시작하지 못했습니다")).toBeNull();
    expect(screen.getByRole("button", { name: payName(1) })).toBeDisabled();
    expect(createOrder).toHaveBeenCalledTimes(1);
    expect(launcher.requestPayment).not.toHaveBeenCalled();
  });

  it("같은 틱에 두 번 눌러도 주문 · 결제창은 하나", async () => {
    const user = userEvent.setup();
    const { createOrder, launcher } = setup();
    await user.click((await screen.findAllByRole("radio"))[0]);
    await agree(user);
    const button = screen.getByRole("button", { name: payName(0) });
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
    const NOTICE = "지금은 충전할 수 있는 상품이 없어요";

    // PAY-06 — 두 줄 문구, 홈으로, 결제하기 · 동의 · 안내 상자 없음
    async function expectPay06(container: HTMLElement) {
      expect(await screen.findByText(NOTICE)).toBeInTheDocument();
      expect(
        screen.getByText("잠시 후 다시 확인해 주세요."),
      ).toBeInTheDocument();
      expect(screen.queryAllByRole("radio")).toHaveLength(0);
      expect(screen.getByRole("link", { name: "홈으로" })).toHaveAttribute(
        "href",
        "/",
      );
      expect(screen.queryByRole("button", { name: "결제하기" })).toBeNull();
      expect(screen.queryByRole("checkbox")).toBeNull();
      expect(container.querySelector('[data-slot="top-up-notice"]')).toBeNull();
      expect(
        container.querySelector('[data-slot="top-up-agreement"]'),
      ).toBeNull();
    }

    const renderWith = (port: TopUpPort) =>
      render(
        <QueryClientProvider client={makeQueryClient()}>
          <WalletScreen
            port={port}
            launcher={{ requestPayment: vi.fn(async () => {}) }}
          />
        </QueryClientProvider>,
      ).container;

    it("n. 전부 비활성이면 PAY-06 (라디오 없음 · 결제하기 없음 · 홈으로)", async () => {
      const container = renderWith(
        portWith(FIXTURE_TOP_UP_PRODUCTS.map((p) => ({ ...p, active: false }))),
      );
      await expectPay06(container);
    });

    it("o. 빈 배열이어도 같은 PAY-06", async () => {
      const container = renderWith(portWith([]));
      await expectPay06(container);
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
      fortuneType: "OVERALL" as const,
      productCode: "FIXTURE_OVERALL_READING_ONLY",
      personId: "fixture-person",
      counterpartPersonId: null,
      productOption: "READING_ONLY" as const,
      questionKey: "OVERALL_FLOW",
      relationType: null,
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

    function renderWith(
      fortune: FortunePort,
      retryProductCode: string | null = null,
    ) {
      return render(
        <QueryClientProvider client={makeQueryClient()}>
          <WalletScreen
            port={createFakeTopUpPort()}
            launcher={{ requestPayment: vi.fn(async () => {}) }}
            fortunePort={fortune}
            retryProductCode={retryProductCode}
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

    it("retryProductCode 와 잔액 부족 추천이 둘 다 있으면 retryProductCode 가 골라진다 (TOPUP-RETRY-URL)", async () => {
      const { fortune, quote } = await makeQuote();
      // 추천(서버 값)과 다른 판매 중 상품을 다시 결제하기 상품으로 쓴다 (픽스처일 뿐이다)
      expect(quote.recommendedTopUp).not.toBe("FIXTURE_TOP_UP_D");
      saveFor(quote);
      renderWith(fortune, "FIXTURE_TOP_UP_D");
      await waitFor(() => expect(checkedCodes()).toEqual(["FIXTURE_TOP_UP_D"]));
      // 추천이 뒤늦게 덮어쓰지 않는다
      await settle();
      expect(checkedCodes()).toEqual(["FIXTURE_TOP_UP_D"]);
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
    expect(String(caught)).not.toContain("FIXTURE");
    silence.mockRestore();
  });
});
