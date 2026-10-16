import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { StrictMode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/errors";
import type { FortuneSelection } from "@/lib/ports/fortune";
import {
  loadPurchaseSelection,
  savePurchaseSelection,
} from "@/lib/purchase/restore";
import {
  createFakeDirectPaymentPort,
  type FakeDirectPaymentScenario,
} from "@/mocks/directPayment";
import {
  createFakeFortunePort,
  type FakeFortuneScenario,
} from "@/mocks/fortune";
import { createFakeWallet } from "@/mocks/wallet";
import { DirectPaymentReturn } from "./DirectPaymentReturn";

const push = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));

afterEach(() => {
  cleanup();
  push.mockReset();
  sessionStorage.clear();
});

// 픽스처일 뿐이며 실제 가격 · 결제 키 · 규칙과 무관하다 (DIRECT-PAY). 상품 · 금액은 src/mocks/fortune.ts 의 픽스처다.
const PERSON_ID = "66666666-6666-4666-8666-666666666666";
const ORDER_KEY = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
const FULFILL_KEY = "dddddddd-dddd-4ddd-8ddd-dddddddddddd";
const RETURN_PATH = `/fortune/love/questions?personId=${PERSON_ID}`;

const LOVE: FortuneSelection = {
  fortuneType: "LOVE",
  productCode: "FIXTURE_LOVE_READING_ONLY",
  personId: PERSON_ID,
  counterpartPersonId: null,
  productOption: "READING_ONLY",
  questionKey: "CURRENT_RELATIONSHIP",
  relationType: null,
};
const SUNEUNG: FortuneSelection = {
  fortuneType: "SUNEUNG",
  productCode: "FIXTURE_SUNEUNG_READING_WITH_TALISMAN",
  personId: PERSON_ID,
  counterpartPersonId: null,
  productOption: null,
  questionKey: null,
  relationType: null,
};

async function prepare(
  options: {
    selection?: FortuneSelection;
    fortuneScenario?: FakeFortuneScenario;
    directScenario?: FakeDirectPaymentScenario;
    // 복원값: 기본 이 주문, "none" 없음, "other" 다른 주문 ID
    saved?: "this" | "none" | "other";
    fulfillmentKey?: string | null;
  } = {},
) {
  const selection = options.selection ?? LOVE;
  const fortune = createFakeFortunePort({
    wallet: createFakeWallet(100),
    priceCurrency: "KRW",
    scenario: options.fortuneScenario,
  });
  const direct = createFakeDirectPaymentPort({
    fortune,
    scenario: options.directScenario,
  });
  const quote = await fortune.createQuote(selection);
  const order = await direct.createOrder({ quoteId: quote.quoteId }, ORDER_KEY);
  const saved = options.saved ?? "this";
  if (saved !== "none") {
    savePurchaseSelection({
      returnPath: RETURN_PATH,
      quoteId: quote.quoteId,
      selection,
      orderKey: ORDER_KEY,
      orderId:
        saved === "this"
          ? order.orderId
          : "cccccccc-cccc-4ccc-8ccc-cccccccccccc",
      fulfillmentKey: options.fulfillmentKey ?? null,
    });
  }
  const originalFulfill = direct.fulfillReading.bind(direct);
  const originalStatus = fortune.getPurchaseStatus.bind(fortune);
  const spies = {
    confirm: vi.spyOn(direct, "confirm"),
    getOrder: vi.spyOn(direct, "getOrder"),
    fulfillReading: vi.spyOn(direct, "fulfillReading"),
    getPurchaseStatus: vi.spyOn(fortune, "getPurchaseStatus"),
  };
  const ret = {
    paymentKey: "fixture-payment-key",
    orderId: order.orderId,
    amount: String(order.amount.amount),
  };
  let t = 0;
  const deps = {
    now: () => t,
    sleep: async (ms: number) => {
      t += ms;
    },
  };
  return {
    fortune,
    direct,
    quote,
    order,
    ret,
    deps,
    spies,
    originalFulfill,
    originalStatus,
  };
}

function view(p: Awaited<ReturnType<typeof prepare>>, strict = false) {
  const ui = (
    <DirectPaymentReturn
      ret={p.ret}
      directPort={p.direct}
      fortunePort={p.fortune}
      deps={p.deps}
    />
  );
  return render(strict ? <StrictMode>{ui}</StrictMode> : ui);
}

const viewButton = () =>
  screen.findByRole("button", { name: "결과 보러 가기" });
const mailSubject = (link: HTMLElement) =>
  decodeURIComponent(
    (link.getAttribute("href") ?? "").split("?subject=")[1] ?? "",
  );

describe("DirectPaymentReturn (/pay/success?flow=direct, DIRECT-PAY)", () => {
  it("B1. 수능운: 완료 블록 전에는 이동 0번, 결과 보러 가기 → /suneung/r/<id> 1번 (같은 틱 두 번도 1번)", async () => {
    const p = await prepare({ selection: SUNEUNG });
    view(p);
    const button = await viewButton();
    expect(screen.getByText("준비가 끝났어요!")).toBeInTheDocument();
    expect(push).not.toHaveBeenCalled();
    fireEvent.click(button);
    fireEvent.click(button);
    const result = await p.spies.fulfillReading.mock.results[0].value;
    expect(push).toHaveBeenCalledTimes(1);
    expect(push).toHaveBeenCalledWith(
      `/suneung/r/${encodeURIComponent(result.readingId)}`,
    );
  });

  it("B2. 애정운: 같은 흐름에서 /fortune/r/<id>", async () => {
    const user = userEvent.setup();
    const p = await prepare({ selection: LOVE });
    view(p);
    await user.click(await viewButton());
    const result = await p.spies.fulfillReading.mock.results[0].value;
    expect(push).toHaveBeenCalledTimes(1);
    expect(push).toHaveBeenCalledWith(
      `/fortune/r/${encodeURIComponent(result.readingId)}`,
    );
  });

  it("B3. StrictMode 로 감싸도 승인 1번 · 생성 1번", async () => {
    const p = await prepare();
    view(p, true);
    await viewButton();
    expect(p.spies.confirm).toHaveBeenCalledTimes(1);
    expect(p.spies.fulfillReading).toHaveBeenCalledTimes(1);
  });

  it("B4. 복원값에 생성 키가 이미 있으면 그 키로 보낸다 / 없으면 보내기 전에 복원값에 키가 저장돼 있다", async () => {
    const withKey = await prepare({ fulfillmentKey: FULFILL_KEY });
    view(withKey);
    await viewButton();
    expect(withKey.spies.fulfillReading.mock.calls[0][1]).toBe(FULFILL_KEY);
    cleanup();
    sessionStorage.clear();

    const without = await prepare({ fulfillmentKey: null });
    let storedWhenSent: string | null | undefined;
    without.spies.fulfillReading.mockImplementation(async (input, key) => {
      storedWhenSent = loadPurchaseSelection()?.fulfillmentKey;
      return without.originalFulfill(input, key);
    });
    view(without);
    await viewButton();
    const sentKey = without.spies.fulfillReading.mock.calls[0][1];
    expect(sentKey).toBeTruthy();
    expect(storedWhenSent).toBe(sentKey);
  });

  it("B5. 가짜 운세 generating: 자동 상태 조회 1번 → 완료 블록", async () => {
    const p = await prepare({ fortuneScenario: "generating" });
    view(p);
    await viewButton();
    expect(p.spies.fulfillReading.mock.results.length).toBe(1);
    const first = await p.spies.fulfillReading.mock.results[0].value;
    expect(first.status).toBe("GENERATING");
    expect(p.spies.getPurchaseStatus).toHaveBeenCalledTimes(1);
    expect(p.spies.getPurchaseStatus).toHaveBeenCalledWith(p.quote.quoteId);
  });

  it.each([
    ["가짜 서버 값 그대로", false],
    ["상태 조회가 RETRY_PURCHASE 를 줘도", true],
  ])("B6. 생성 실패(%s): 다시 시도 없이 확인 필요 구매 안내 · 문의하기에 구매번호, 복원값 삭제", async (_name, stub) => {
    const p = await prepare({ fortuneScenario: "generation_failed" });
    if (stub) {
      p.spies.getPurchaseStatus.mockImplementation(async (quoteId) => {
        const state = await p.originalStatus(quoteId);
        return state ? { ...state, nextAction: "RETRY_PURCHASE" } : state;
      });
    }
    view(p);
    expect(
      await screen.findByText("확인이 필요한 구매예요"),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "다시 시도" })).toBeNull();
    const first = await p.spies.fulfillReading.mock.results[0].value;
    const link = screen.getByRole("link", { name: "문의하기" });
    expect(mailSubject(link)).toContain(`운세 구매번호 ${first.purchaseId}`);
    await waitFor(() => expect(loadPurchaseSelection()).toBeNull());
  });

  it("B7. 생성이 409 IDEMPOTENCY_REQUEST_PROCESSING: 상태 조회 null → 구매 결과 미확인 · 다시 확인하기 → 다시 null → 같은 키로 재전송 → 완료", async () => {
    const user = userEvent.setup();
    const p = await prepare({ fortuneScenario: "processing_409" });
    view(p);
    expect(
      await screen.findByText("구매 결과를 아직 확인하지 못했어요"),
    ).toBeInTheDocument();
    expect(screen.getByText("잠시 후 다시 확인해 주세요.")).toBeInTheDocument();
    expect(p.spies.getPurchaseStatus).toHaveBeenCalledTimes(1);
    expect(p.spies.fulfillReading).toHaveBeenCalledTimes(1);
    await user.click(screen.getByRole("button", { name: "다시 확인하기" }));
    await viewButton();
    expect(p.spies.getPurchaseStatus).toHaveBeenCalledTimes(2);
    expect(p.spies.fulfillReading).toHaveBeenCalledTimes(2);
    expect(p.spies.fulfillReading.mock.calls[1][1]).toBe(
      p.spies.fulfillReading.mock.calls[0][1],
    );
    expect(p.spies.fulfillReading.mock.calls[1][0]).toEqual(
      p.spies.fulfillReading.mock.calls[0][0],
    );
  });

  it.each([
    ["복원값 없음", "none"],
    ["복원값의 orderId 가 다름", "other"],
  ] as const)("B8. %s: 생성 0번, 확인이 필요한 결제 화면 (문의하기 제목에 결제 주문번호)", async (_name, saved) => {
    const p = await prepare({ saved });
    view(p);
    expect(
      await screen.findByText(
        "확인이 필요한 결제예요. 고객센터로 문의해 주세요.",
      ),
    ).toBeInTheDocument();
    expect(p.spies.fulfillReading).not.toHaveBeenCalled();
    expect(
      mailSubject(screen.getByRole("link", { name: "문의하기" })),
    ).toContain(`결제 주문번호 ${p.ret.orderId}`);
  });

  it("B9. 결제 FAILED(rejected): 다시 결제하기 href 는 복원 returnPath, 생성 0번", async () => {
    const p = await prepare({ directScenario: "rejected" });
    view(p);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "결제가 완료되지 않았어요",
    );
    expect(
      screen.getByText("결제가 완료되지 않아 결제 금액은 청구되지 않았어요."),
    ).toBeInTheDocument();
    expect(
      await screen.findByRole("link", { name: "다시 결제하기" }),
    ).toHaveAttribute("href", RETURN_PATH);
    expect(p.spies.fulfillReading).not.toHaveBeenCalled();
  });

  it("B9b. 결제 FAILED 인데 복원값이 다른 주문이면 다시 결제하기 없음", async () => {
    const p = await prepare({ directScenario: "rejected", saved: "other" });
    view(p);
    await screen.findByText("결제가 완료되지 않았어요");
    await screen.findByRole("link", { name: "홈으로" });
    expect(screen.queryByRole("link", { name: "다시 결제하기" })).toBeNull();
  });

  it("B10. REVIEW_REQUIRED: 확인 필요 화면, 다시 결제하기 없음, 생성 0번", async () => {
    const p = await prepare({ directScenario: "review_required" });
    view(p);
    expect(
      await screen.findByText(
        "확인이 필요한 결제예요. 고객센터로 문의해 주세요.",
      ),
    ).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "다시 결제하기" })).toBeNull();
    expect(p.spies.fulfillReading).not.toHaveBeenCalled();
  });

  it("B11. stuck_pending: 주문 확인하기 화면 → 누르면 다시 조회", async () => {
    const user = userEvent.setup();
    const p = await prepare({ directScenario: "stuck_pending" });
    view(p);
    const button = await screen.findByRole("button", { name: "주문 확인하기" });
    expect(
      screen.getByText(
        "확인이 조금 늦어지고 있어요. 다시 결제하지 말고 아래 버튼으로 주문 상태를 확인해 주세요.",
      ),
    ).toBeInTheDocument();
    const before = p.spies.getOrder.mock.calls.length;
    await user.click(button);
    await screen.findByRole("button", { name: "주문 확인하기" });
    expect(p.spies.getOrder.mock.calls.length).toBeGreaterThan(before);
    expect(p.spies.fulfillReading).not.toHaveBeenCalled();
  });

  it("B12. 생성이 409 PAYMENT_ORDER_NOT_CONFIRMABLE: 확인 필요 결제 화면", async () => {
    const p = await prepare();
    p.spies.fulfillReading.mockRejectedValueOnce(
      new ApiError({
        status: 409,
        code: "PAYMENT_ORDER_NOT_CONFIRMABLE",
        traceId: null,
      }),
    );
    view(p);
    expect(
      await screen.findByText(
        "확인이 필요한 결제예요. 고객센터로 문의해 주세요.",
      ),
    ).toBeInTheDocument();
    expect(p.spies.getPurchaseStatus).not.toHaveBeenCalled();
  });

  it("B13. 완료 블록이 보이는 동안 복원값(생성 키 포함)은 남고, 「결과 보러 가기」 를 누르면 지워진다", async () => {
    const p = await prepare();
    view(p);
    const button = await viewButton();
    const saved = loadPurchaseSelection();
    expect(saved).not.toBeNull();
    expect(saved?.fulfillmentKey).not.toBeNull();
    const user = userEvent.setup();
    await user.click(button);
    expect(loadPurchaseSelection()).toBeNull();
  });

  it("화면에 결제 키와 서버 원문은 없다", async () => {
    const p = await prepare({ directScenario: "rejected" });
    view(p);
    await screen.findByText("결제가 완료되지 않았어요");
    expect(document.body.textContent).not.toContain("fixture-payment-key");
  });
});
