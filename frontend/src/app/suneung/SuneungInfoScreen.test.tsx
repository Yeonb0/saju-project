import { QueryClientProvider } from "@tanstack/react-query";
import {
  cleanup,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Component, type ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/errors";
import { type BasicSaju, ELEMENTS } from "@/lib/ports/basicSaju";
import type { FortunePort } from "@/lib/ports/fortune";
import type { PersonPort, PersonSummary } from "@/lib/ports/person";
import {
  loadPurchaseSelection,
  savePurchaseSelection,
} from "@/lib/purchase/restore";
import { makeQueryClient } from "@/lib/queryClient";
import { createFakeAccount } from "@/mocks/account";
import { createFakeFortunePort } from "@/mocks/fortune";
import { createFakePersonPort } from "@/mocks/person";
import { createFakeTopUpPort } from "@/mocks/topUp";
import { createFakeWallet } from "@/mocks/wallet";
import { SuneungInfoScreen } from "./SuneungInfoScreen";

const push = vi.fn();
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));

// vaul 은 시트 안을 누를 때 setPointerCapture 를 부르는데 jsdom 에는 없다 — 이 파일에서만 빈 함수로 채운다
if (!Element.prototype.setPointerCapture) {
  Element.prototype.setPointerCapture = () => {};
  Element.prototype.releasePointerCapture = () => {};
}

afterEach(() => {
  cleanup();
  push.mockReset();
  sessionStorage.clear();
});

// 픽스처일 뿐이며 실제 사용자 · 가격 · 규칙과 무관하다. 상품 · 금액은 src/mocks/fortune.ts 의 픽스처다.
const SUNEUNG_CODE = "FIXTURE_SUNEUNG_READING_WITH_TALISMAN";
const SELF_NAME = "FIXTURE"; // src/mocks/account.ts 의 본인 픽스처
const OTHER: PersonSummary = {
  personId: "99999999-9999-4999-8999-999999999999",
  isSelf: false,
  name: "FIXTURE OTHER",
};

// 픽스처일 뿐이며 실제 계산 · 규칙과 무관하다
function basicSajuStub() {
  const getBasicSaju = vi.fn(
    async (_personId: string): Promise<BasicSaju> => ({
      fiveElements: ELEMENTS.map((element, i) => ({ element, count: i + 1 })),
      birthTimeKnown: true,
      calculationVersion: "fixture-calc",
    }),
  );
  return { port: { getBasicSaju }, getBasicSaju };
}

type Injected = { personPort?: PersonPort; fortunePort?: FortunePort };

function ports() {
  const account = createFakeAccount("signed_in");
  account.addOther({ ...OTHER });
  return {
    personPort: createFakePersonPort(account),
    fortunePort: createFakeFortunePort({ wallet: createFakeWallet(100) }),
    topUpPort: createFakeTopUpPort(),
  };
}

function setup(overrides: Injected = {}) {
  const base = ports();
  const fortunePort = overrides.fortunePort ?? base.fortunePort;
  const createQuote = vi.spyOn(fortunePort, "createQuote");
  const getQuote = vi.spyOn(fortunePort, "getQuote");
  const purchase = vi.spyOn(fortunePort, "purchase");
  const sajuStub = basicSajuStub();
  render(
    <QueryClientProvider client={makeQueryClient()}>
      <SuneungInfoScreen
        personPort={overrides.personPort ?? base.personPort}
        fortunePort={fortunePort}
        topUpPort={base.topUpPort}
        basicSajuPort={sajuStub.port}
      />
    </QueryClientProvider>,
  );
  return {
    createQuote,
    getQuote,
    purchase,
    getBasicSaju: sajuStub.getBasicSaju,
  };
}

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

async function expectThrown(props: Injected) {
  const silence = vi.spyOn(console, "error").mockImplementation(() => {});
  let caught: unknown = null;
  render(
    <QueryClientProvider client={makeQueryClient()}>
      <Boundary onCatch={(e) => (caught = e)}>
        <SuneungInfoScreen {...props} />
      </Boundary>
    </QueryClientProvider>,
  );
  // 기본 retry 1 (makeQueryClient) 뒤에 던진다
  await screen.findByText("caught", {}, { timeout: 5000 });
  silence.mockRestore();
  return caught;
}

const proceed = () => screen.findByRole("button", { name: "진행" });

describe("SuneungInfoScreen (CSAT-01)", () => {
  it("a. 본인이 기본 대상으로 보이고, 진행을 누르면 팝업이 열리며 대상 이름이 본인 이름", async () => {
    const user = userEvent.setup();
    setup();
    expect(await screen.findByText(SELF_NAME)).toBeInTheDocument();
    await user.click(await proceed());
    const dialog = await screen.findByRole("dialog");
    expect(await within(dialog).findByText(SELF_NAME)).toBeInTheDocument();
    expect(within(dialog).getByText("사주+부적")).toBeInTheDocument();
  });

  it("b. 불러오기로 고른 인물과 수능운 상품으로 견적을 요청한다", async () => {
    const user = userEvent.setup();
    const { createQuote } = setup();
    await user.click(
      await screen.findByRole("button", {
        name: "저장된 다른 사용자 불러오기",
      }),
    );
    await user.click(screen.getByRole("button", { name: OTHER.name }));
    await user.click(await proceed());
    await waitFor(() => expect(createQuote).toHaveBeenCalledTimes(1));
    expect(createQuote).toHaveBeenCalledWith({
      productCode: SUNEUNG_CODE,
      personId: OTHER.personId,
      counterpartPersonId: null,
    });
  });

  it("c. 구매에 성공하면 결과 화면으로 한 번 이동한다", async () => {
    const user = userEvent.setup();
    const { purchase } = setup();
    await user.click(await proceed());
    await user.click(await screen.findByRole("button", { name: "사용하기" }));
    await waitFor(() => expect(push).toHaveBeenCalledTimes(1));
    const result = await purchase.mock.results[0].value;
    expect(push).toHaveBeenCalledWith(`/suneung/r/${result.readingId}`);
  });

  it("d. 활성 수능운 상품이 없으면 판매 종료 문구만 보이고 진행 버튼이 없다", async () => {
    const { personPort, fortunePort } = ports();
    vi.spyOn(fortunePort, "listProducts").mockResolvedValue([]);
    setup({ personPort, fortunePort });
    expect(
      await screen.findByText("지금은 구매할 수 없습니다"),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "진행" })).toBeNull();
  });

  it("e. 활성 수능운 상품이 둘이면 오류 화면으로 던진다", async () => {
    const { personPort, fortunePort } = ports();
    const [one] = await fortunePort.listProducts("SUNEUNG");
    vi.spyOn(fortunePort, "listProducts").mockResolvedValue([
      one,
      { ...one, code: "FIXTURE_SUNEUNG_SECOND" },
    ]);
    const caught = await expectThrown({ personPort, fortunePort });
    expect(String(caught)).toContain("SUNEUNG 활성 상품이 하나가 아니다");
  });

  it("f. 인물 목록 조회가 500 이면 오류 화면으로 던진다", async () => {
    const { fortunePort } = ports();
    const personPort = createFakePersonPort(createFakeAccount("signed_in"));
    vi.spyOn(personPort, "list").mockRejectedValue(
      new ApiError({ status: 500, code: "INTERNAL_ERROR", traceId: null }),
    );
    const caught = await expectThrown({ personPort, fortunePort });
    expect(caught).toBeInstanceOf(ApiError);
    expect((caught as ApiError).status).toBe(500);
  });

  it("g. 충전 후 복귀면 팝업이 저절로 열리고 저장한 견적을 재확인한다", async () => {
    const { personPort, fortunePort } = ports();
    // 만료 안 된 견적 — 같은 가짜 서버에서 미리 받는다
    const quote = await fortunePort.createQuote({
      productCode: SUNEUNG_CODE,
      personId: OTHER.personId,
      counterpartPersonId: null,
    });
    savePurchaseSelection({
      returnPath: "/suneung",
      quoteId: quote.quoteId,
      selection: {
        productCode: SUNEUNG_CODE,
        personId: OTHER.personId,
        counterpartPersonId: null,
      },
    });
    const { createQuote, getQuote } = setup({ personPort, fortunePort });
    const dialog = await screen.findByRole("dialog");
    expect(await within(dialog).findByText(OTHER.name)).toBeInTheDocument();
    await within(dialog).findByRole("button", { name: "사용하기" });
    expect(getQuote).toHaveBeenCalledWith(quote.quoteId);
    expect(createQuote).not.toHaveBeenCalled();
  });

  it("h. 같은 화면의 저장값인데 상품 code 가 다르면 팝업이 열리지 않고 저장값이 지워진다", async () => {
    savePurchaseSelection({
      returnPath: "/suneung",
      quoteId: "fixture-quote",
      selection: {
        productCode: "FIXTURE_OTHER_PRODUCT",
        personId: OTHER.personId,
        counterpartPersonId: null,
      },
    });
    setup();
    await proceed();
    await waitFor(() => expect(loadPurchaseSelection()).toBeNull());
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("i. 다른 화면의 저장값은 팝업을 열지 않고 그대로 남는다", async () => {
    const saved = {
      returnPath: "/fortune/LOVE/questions",
      quoteId: "fixture-quote",
      selection: {
        productCode: SUNEUNG_CODE,
        personId: OTHER.personId,
        counterpartPersonId: null,
      },
    };
    savePurchaseSelection(saved);
    setup();
    await proceed();
    expect(screen.queryByRole("dialog")).toBeNull();
    expect(loadPurchaseSelection()).toEqual(saved);
  });

  it("j. 진짜 모드인데 구현이 없으면 조용히 넘어가지 않고 오류 화면으로 던진다", async () => {
    // 테스트 환경은 NEXT_PUBLIC_API_MODE 가 비어 있어 진짜 모드다
    const caught = await expectThrown({});
    expect(String(caught)).toContain("MOCK-PORT");
  });

  it("k. 오행분석 섹션에 li 5개가 보인다", async () => {
    const { getBasicSaju } = setup();
    const section = (await screen.findByText("오행분석")).closest("section");
    expect(section).not.toBeNull();
    await waitFor(() =>
      expect(
        within(section as HTMLElement).getAllByRole("listitem"),
      ).toHaveLength(5),
    );
    expect(getBasicSaju).toHaveBeenCalledTimes(1);
  });
});
