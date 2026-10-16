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
import { ApiError, ApiNetworkError } from "@/lib/api/errors";
import { loginHref } from "@/lib/auth/returnTo";
import { BUSINESS_INFO } from "@/lib/business";
import type { DirectPaymentPort } from "@/lib/ports/directPayment";
import type { FortuneSelection } from "@/lib/ports/fortune";
import {
  PaymentCanceledError,
  type PaymentLauncher,
} from "@/lib/ports/paymentLauncher";
import {
  loadPurchaseSelection,
  PURCHASE_SELECTION_KEY,
} from "@/lib/purchase/restore";
import { makeQueryClient } from "@/lib/queryClient";
import {
  createFakeDirectPaymentPort,
  type FakeDirectPaymentScenario,
} from "@/mocks/directPayment";
import { createFakeFortunePort } from "@/mocks/fortune";
import { createFakeWallet } from "@/mocks/wallet";
import { DirectCheckout } from "./DirectCheckout";

const push = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));

afterEach(() => {
  cleanup();
  push.mockReset();
  sessionStorage.clear();
});

// 픽스처일 뿐이며 실제 가격 · 규칙과 무관하다. 상품 · 금액은 src/mocks/fortune.ts 의 픽스처다 (DIRECT-PAY).
const RETURN_PATH =
  "/fortune/love/questions?personId=66666666-6666-4666-8666-666666666666";
const SELECTION: FortuneSelection = {
  fortuneType: "LOVE",
  productCode: "FIXTURE_LOVE_READING_ONLY",
  personId: "66666666-6666-4666-8666-666666666666",
  counterpartPersonId: null,
  productOption: "READING_ONLY",
  questionKey: "CURRENT_RELATIONSHIP",
  relationType: null,
};
const SAVED_KEY = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";

function setup(
  options: {
    priceCurrency?: "KRW" | "TURTLE_SHELL";
    scenario?: FakeDirectPaymentScenario;
    batchLimitKrw?: number;
    // 먼저 만들어 둔 가짜 서버를 쓴다 (복원 시험)
    fortune?: ReturnType<typeof createFakeFortunePort>;
    direct?: DirectPaymentPort;
    resumeQuoteId?: string | null;
    resumeOrder?: { orderKey: string; orderId: string | null } | null;
  } = {},
) {
  const fortune =
    options.fortune ??
    createFakeFortunePort({
      wallet: createFakeWallet(100),
      priceCurrency: options.priceCurrency ?? "KRW",
    });
  const direct =
    options.direct ??
    createFakeDirectPaymentPort({
      fortune,
      scenario: options.scenario,
      batchLimitKrw: options.batchLimitKrw,
    });
  const createQuote = vi.spyOn(fortune, "createQuote");
  const getQuote = vi.spyOn(fortune, "getQuote");
  const createOrder = vi.spyOn(direct, "createOrder");
  const getOrder = vi.spyOn(direct, "getOrder");
  const launcher = {
    requestPayment: vi.fn<PaymentLauncher["requestPayment"]>(async () => {}),
  };
  const onOpenChange = vi.fn();
  const view = render(
    <QueryClientProvider client={makeQueryClient()}>
      <DirectCheckout
        open
        onOpenChange={onOpenChange}
        selection={SELECTION}
        targetName="FIXTURE"
        optionLabel="FIXTURE 옵션"
        resumeQuoteId={options.resumeQuoteId}
        resumeOrder={options.resumeOrder}
        returnPath={RETURN_PATH}
        fortunePort={fortune}
        directPort={direct}
        launcher={launcher}
      />
    </QueryClientProvider>,
  );
  return {
    ...view,
    fortune,
    direct,
    createQuote,
    getQuote,
    createOrder,
    getOrder,
    launcher,
    onOpenChange,
  };
}

const payButton = () => screen.findByRole("button", { name: /원 결제하기$/ });
const agreeBox = () =>
  screen.findByRole("checkbox", { name: /결제에 동의합니다/ });
async function agreeAndPay(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await agreeBox());
  await user.click(await payButton());
}
const mailSubject = (link: HTMLElement) =>
  decodeURIComponent(
    (link.getAttribute("href") ?? "").split("?subject=")[1] ?? "",
  );

class Boundary extends Component<
  { children: ReactNode; onCatch: (error: unknown) => void },
  { failed: boolean }
> {
  state = { failed: false };
  static getDerivedStateFromError() {
    return { failed: true };
  }
  componentDidCatch(error: unknown) {
    this.props.onCatch(error);
  }
  render() {
    return this.state.failed ? <p>caught</p> : this.props.children;
  }
}

// 먼저 주문을 만들어 두고 복원 값처럼 넘긴다
async function withExistingOrder(
  scenario: FakeDirectPaymentScenario,
  confirm: boolean,
) {
  const fortune = createFakeFortunePort({
    wallet: createFakeWallet(100),
    priceCurrency: "KRW",
  });
  const direct = createFakeDirectPaymentPort({ fortune, scenario });
  const quote = await fortune.createQuote(SELECTION);
  const order = await direct.createOrder({ quoteId: quote.quoteId }, SAVED_KEY);
  if (confirm) {
    await direct.confirm({
      paymentKey: "pk-1",
      orderId: order.orderId,
      amount: String(order.amount.amount),
    });
  }
  return { fortune, direct, quote, order };
}

describe("DirectCheckout — 결제하기 팝업 (DIRECT-PAY)", () => {
  it("C1. 정보 줄: 상품명 · 대상 · 옵션 · 결제 금액은 서버 견적 값이다", async () => {
    const { createQuote } = setup();
    const dialog = await screen.findByRole("dialog");
    await payButton();
    const quote = await createQuote.mock.results[0].value;
    const money = `${quote.charged.amount.toLocaleString("ko-KR")}원`;
    expect(within(dialog).getByText("결제하기")).toBeInTheDocument();
    expect(within(dialog).getByText(quote.productName)).toBeInTheDocument();
    expect(within(dialog).getByText("FIXTURE")).toBeInTheDocument();
    expect(within(dialog).getByText("FIXTURE 옵션")).toBeInTheDocument();
    expect(
      within(dialog).getByText("결제 금액").parentElement?.textContent,
    ).toContain(money);
    expect(
      within(dialog).getByRole("button", { name: `${money} 결제하기` }),
    ).toBeInTheDocument();
  });

  it("C2. 동의 전 결제 버튼은 비활성, 동의 후 활성 — 안내 4줄과 환불정책 링크가 보인다", async () => {
    const user = userEvent.setup();
    setup();
    const button = await payButton();
    expect(button).toBeDisabled();
    expect(
      screen.getByText(
        "결제 승인이 확인되면 운세와 부적을 만들어 화면으로 제공합니다.",
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        "결제 후 7일 이내이고, 완성된 운세 결과 또는 부적을 아직 받지 않았다면 고객센터로 신청해 결제를 취소할 수 있습니다.",
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        "완성된 운세 결과를 확인했거나 부적이 지급되는 등 디지털 콘텐츠 제공이 시작된 뒤에는 단순 변심으로 취소 · 환불할 수 없습니다.",
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText("미성년자는 법정대리인의 동의를 받아 결제해 주세요."),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "환불정책 보기 >" }),
    ).toHaveAttribute("href", "/refund");
    const box = await agreeBox();
    expect(box).not.toBeChecked();
    await user.click(box);
    expect(button).toBeEnabled();
  });

  it("C3. 누르면 주문을 한 번 만들고 결제창을 연다 — 복원 저장값에 주문 키 · 주문 ID 가 있고 생년정보는 없다", async () => {
    const user = userEvent.setup();
    const { createOrder, createQuote, launcher } = setup();
    await agreeAndPay(user);
    await waitFor(() =>
      expect(launcher.requestPayment).toHaveBeenCalledTimes(1),
    );
    const quote = await createQuote.mock.results[0].value;
    expect(createOrder).toHaveBeenCalledTimes(1);
    expect(createOrder.mock.calls[0][0]).toEqual({ quoteId: quote.quoteId });
    const order = await createOrder.mock.results[0].value;
    expect(launcher.requestPayment).toHaveBeenCalledWith(order, {
      kind: "direct",
      returnPath: RETURN_PATH,
    });
    const saved = loadPurchaseSelection();
    expect(saved?.orderKey).toBe(createOrder.mock.calls[0][1]);
    expect(saved?.orderId).toBe(order.orderId);
    expect(saved?.quoteId).toBe(quote.quoteId);
    const raw = sessionStorage.getItem(PURCHASE_SELECTION_KEY) ?? "";
    expect(raw).not.toMatch(/birth|name|amount|paymentKey/i);
  });

  it("C4. 같은 틱에 두 번 눌러도 주문은 한 번만 만든다", async () => {
    const user = userEvent.setup();
    const { createOrder, launcher } = setup();
    await user.click(await agreeBox());
    const button = await payButton();
    fireEvent.click(button);
    fireEvent.click(button);
    await waitFor(() =>
      expect(launcher.requestPayment).toHaveBeenCalledTimes(1),
    );
    expect(createOrder).toHaveBeenCalledTimes(1);
  });

  it("C5. 결제창을 닫으면 안내 없이 버튼이 다시 열리고, 다시 누르면 같은 키로 같은 주문이다", async () => {
    const user = userEvent.setup();
    const { createOrder, launcher } = setup();
    launcher.requestPayment.mockRejectedValueOnce(new PaymentCanceledError());
    await agreeAndPay(user);
    await waitFor(() =>
      expect(launcher.requestPayment).toHaveBeenCalledTimes(1),
    );
    await waitFor(async () => expect(await payButton()).toBeEnabled());
    expect(screen.queryByRole("alert")).toBeNull();
    await user.click(await payButton());
    await waitFor(() =>
      expect(launcher.requestPayment).toHaveBeenCalledTimes(2),
    );
    expect(createOrder).toHaveBeenCalledTimes(2);
    expect(createOrder.mock.calls[1][1]).toBe(createOrder.mock.calls[0][1]);
    const first = await createOrder.mock.results[0].value;
    const second = await createOrder.mock.results[1].value;
    expect(second.orderId).toBe(first.orderId);
  });

  it("C6. 주문 생성이 네트워크로 끊기면 버튼만 다시 열리고, 다시 누르면 같은 키다", async () => {
    const user = userEvent.setup();
    const { createOrder, launcher } = setup();
    createOrder.mockRejectedValueOnce(new ApiNetworkError());
    await agreeAndPay(user);
    await waitFor(() => expect(createOrder).toHaveBeenCalledTimes(1));
    await waitFor(async () => expect(await payButton()).toBeEnabled());
    expect(screen.queryByRole("alert")).toBeNull();
    await user.click(await payButton());
    await waitFor(() =>
      expect(launcher.requestPayment).toHaveBeenCalledTimes(1),
    );
    expect(createOrder.mock.calls[1][1]).toBe(createOrder.mock.calls[0][1]);
  });

  it("C7. 주문 생성이 409 QUOTE_EXPIRED 면 새 견적을 받고 안내 문구를 보이며, 다음 클릭은 새 키다", async () => {
    const user = userEvent.setup();
    const { createOrder, createQuote, launcher } = setup();
    createOrder.mockRejectedValueOnce(
      new ApiError({ status: 409, code: "QUOTE_EXPIRED", traceId: null }),
    );
    await agreeAndPay(user);
    expect(
      await screen.findByText("내용이 바뀌었습니다. 다시 확인해 주세요"),
    ).toBeInTheDocument();
    await waitFor(() => expect(createQuote).toHaveBeenCalledTimes(2));
    await user.click(await payButton());
    await waitFor(() =>
      expect(launcher.requestPayment).toHaveBeenCalledTimes(1),
    );
    expect(createOrder.mock.calls[1][1]).not.toBe(createOrder.mock.calls[0][1]);
    const q2 = await createQuote.mock.results[1].value;
    expect(createOrder.mock.calls[1][0]).toEqual({ quoteId: q2.quoteId });
  });

  it("C8. 결제 전(PAYMENT_PENDING) 주문을 이어 가면 저장된 키를 쓰고 같은 주문이다", async () => {
    const user = userEvent.setup();
    const { fortune, direct, quote, order } = await withExistingOrder(
      "paid",
      false,
    );
    const { createOrder, getOrder, launcher } = setup({
      fortune,
      direct,
      resumeQuoteId: quote.quoteId,
      resumeOrder: { orderKey: SAVED_KEY, orderId: order.orderId },
    });
    await agreeAndPay(user);
    await waitFor(() =>
      expect(launcher.requestPayment).toHaveBeenCalledTimes(1),
    );
    expect(getOrder).toHaveBeenCalledWith(order.orderId);
    expect(createOrder.mock.calls[0][1]).toBe(SAVED_KEY);
    const created = await createOrder.mock.results[0].value;
    expect(created.orderId).toBe(order.orderId);
  });

  it("C9. 저장된 주문이 PAID 면 결제 버튼 없이 확인 필요 블록 — 문의하기 제목에 결제 주문번호", async () => {
    const { fortune, direct, quote, order } = await withExistingOrder(
      "paid",
      true,
    );
    const { launcher, createOrder } = setup({
      fortune,
      direct,
      resumeQuoteId: quote.quoteId,
      resumeOrder: { orderKey: SAVED_KEY, orderId: order.orderId },
    });
    expect(
      await screen.findByText("확인이 필요한 구매예요"),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /원 결제하기$/ })).toBeNull();
    expect(
      screen.getByText("고객센터로 문의해 주시면 빠르게 도와드릴게요."),
    ).toBeInTheDocument();
    if (BUSINESS_INFO.email !== null) {
      expect(screen.getByText(BUSINESS_INFO.email)).toBeInTheDocument();
    }
    const link = screen.getByRole("link", { name: "문의하기" });
    expect(link.getAttribute("href")).toContain(
      encodeURIComponent(`결제 주문번호 ${order.orderId}`),
    );
    expect(mailSubject(link)).toContain(`결제 주문번호 ${order.orderId}`);
    expect(launcher.requestPayment).not.toHaveBeenCalled();
    expect(createOrder).not.toHaveBeenCalled();
  });

  it("C10. 저장된 주문이 FAILED 면 결제 버튼이 있고, 누르면 저장된 키와 다른 키다", async () => {
    const user = userEvent.setup();
    const { fortune, direct, quote, order } = await withExistingOrder(
      "rejected",
      true,
    );
    const { createOrder, launcher } = setup({
      fortune,
      direct,
      resumeQuoteId: quote.quoteId,
      resumeOrder: { orderKey: SAVED_KEY, orderId: order.orderId },
    });
    await agreeAndPay(user);
    await waitFor(() =>
      expect(launcher.requestPayment).toHaveBeenCalledTimes(1),
    );
    expect(createOrder.mock.calls[0][1]).not.toBe(SAVED_KEY);
    const created = await createOrder.mock.results[0].value;
    expect(created.orderId).not.toBe(order.orderId);
  });

  it("C11. 견적이 TURTLE_SHELL 이면 오류 화면으로 던진다", async () => {
    const silence = vi.spyOn(console, "error").mockImplementation(() => {});
    const fortune = createFakeFortunePort({
      wallet: createFakeWallet(100),
      priceCurrency: "TURTLE_SHELL",
    });
    const direct = createFakeDirectPaymentPort({ fortune });
    let caught: unknown = null;
    render(
      <QueryClientProvider client={makeQueryClient()}>
        <Boundary onCatch={(e) => (caught = e)}>
          <DirectCheckout
            open
            onOpenChange={() => {}}
            selection={SELECTION}
            targetName="FIXTURE"
            optionLabel="FIXTURE 옵션"
            returnPath={RETURN_PATH}
            fortunePort={fortune}
            directPort={direct}
            launcher={{ requestPayment: vi.fn(async () => {}) }}
          />
        </Boundary>
      </QueryClientProvider>,
    );
    await screen.findByText("caught");
    silence.mockRestore();
    expect(String(caught)).toContain("직접결제 견적이 KRW 가 아니다");
  });

  it("C12. 견적이 401 이면 다시 로그인해 주세요 링크", async () => {
    const fortune = createFakeFortunePort({
      wallet: createFakeWallet(100),
      priceCurrency: "KRW",
    });
    vi.spyOn(fortune, "createQuote").mockRejectedValue(
      new ApiError({ status: 401, code: "SESSION_EXPIRED", traceId: null }),
    );
    setup({ fortune });
    const link = await screen.findByRole("link", {
      name: "다시 로그인해 주세요",
    });
    expect(link).toHaveAttribute("href", loginHref(RETURN_PATH));
  });

  it("C13. 주문 생성 응답이 이미 PAID 면 결제창을 열지 않고 확인 필요 블록", async () => {
    const user = userEvent.setup();
    const { createOrder, launcher } = setup();
    createOrder.mockResolvedValueOnce({
      orderId: "cccccccc-cccc-4ccc-8ccc-cccccccccccc",
      productCode: "FIXTURE",
      orderName: "FIXTURE",
      amount: { currency: "KRW", amount: 1 },
      status: "PAID",
    });
    await agreeAndPay(user);
    expect(
      await screen.findByText("확인이 필요한 구매예요"),
    ).toBeInTheDocument();
    expect(launcher.requestPayment).not.toHaveBeenCalled();
    expect(
      mailSubject(screen.getByRole("link", { name: "문의하기" })),
    ).toContain("결제 주문번호 cccccccc-cccc-4ccc-8ccc-cccccccccccc");
  });

  it("C14. 주문 한도를 넘으면(PAYMENT_BATCH_LIMIT_EXCEEDED) 결제하지 못했습니다 alert, 결제창은 열지 않는다", async () => {
    const user = userEvent.setup();
    const { launcher, createOrder } = setup({ batchLimitKrw: 1 });
    await agreeAndPay(user);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "결제하지 못했습니다",
    );
    expect(createOrder).toHaveBeenCalledTimes(1);
    expect(launcher.requestPayment).not.toHaveBeenCalled();
  });

  it("결제창 쪽 일반 오류(PaymentCanceledError 아님)는 충전 화면과 같이 결제하지 못했습니다 alert", async () => {
    const user = userEvent.setup();
    const { launcher } = setup();
    launcher.requestPayment.mockRejectedValueOnce(new Error("FIXTURE"));
    await agreeAndPay(user);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "결제하지 못했습니다",
    );
  });

  it("지갑 · 충전 · 구매 문구가 없다 — 사용하기 버튼 · 보유 · 부족 · 충전하기", async () => {
    setup();
    await payButton();
    expect(screen.queryByRole("button", { name: "사용하기" })).toBeNull();
    expect(screen.queryByText("충전하기")).toBeNull();
    expect(screen.queryByText("보유")).toBeNull();
    expect(screen.queryByText("부족")).toBeNull();
    expect(document.body.textContent).not.toContain("등껍질 사용");
  });
});
