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
import type { FortunePort, FortuneProduct } from "@/lib/ports/fortune";
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
import { FortuneQuestionsScreen } from "./FortuneQuestionsScreen";

const router = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => router }));

// 클라이언트 컴포넌트에서는 notFound() 를 부르지 않는다 — 대상이 없으면 replace 로 FORT-01 에 돌려보낸다.
// 그 경우가 아니면 replace 는 불리지 않아야 한다 (afterEach 에서 단언)
let replaceAllowed = false;

afterEach(() => {
  const replaced = router.replace.mock.calls.length;
  cleanup();
  router.push.mockReset();
  router.replace.mockReset();
  sessionStorage.clear();
  const allowed = replaceAllowed;
  replaceAllowed = false;
  if (!allowed) expect(replaced).toBe(0);
});

// 픽스처일 뿐이며 실제 사용자 · 가격 · 규칙과 무관하다. 상품 · 금액은 src/mocks/fortune.ts 의 픽스처다.
const OTHER: PersonSummary = {
  personId: "99999999-9999-4999-8999-999999999999",
  isSelf: false,
  name: "FIXTURE OTHER",
};
const SELF_ID = "55555555-5555-4555-8555-555555555555"; // src/mocks/account.ts 의 본인 픽스처
const SELF_NAME = "FIXTURE";
const TALISMAN = "FIXTURE_LOVE_READING_WITH_TALISMAN";
const ONLY = "FIXTURE_LOVE_READING_ONLY";
const TALISMAN_LABEL = "사주 보고 부적도 받기";
const ONLY_LABEL = "사주만 보기";

function ports() {
  const account = createFakeAccount("signed_in");
  account.addOther({ ...OTHER });
  return {
    personPort: createFakePersonPort(account),
    fortunePort: createFakeFortunePort({ wallet: createFakeWallet(100) }),
    topUpPort: createFakeTopUpPort(),
  };
}

type Injected = {
  slug?: "love" | "wealth" | "overall" | "sinsal" | "compatibility";
  personId?: string;
  counterpartId?: string | null;
  personPort?: PersonPort;
  fortunePort?: FortunePort;
};

function setup(overrides: Injected = {}) {
  const base = ports();
  const fortunePort = overrides.fortunePort ?? base.fortunePort;
  const createQuote = vi.spyOn(fortunePort, "createQuote");
  const getQuote = vi.spyOn(fortunePort, "getQuote");
  const purchase = vi.spyOn(fortunePort, "purchase");
  const view = render(
    <QueryClientProvider client={makeQueryClient()}>
      <FortuneQuestionsScreen
        slug={overrides.slug ?? "love"}
        personId={overrides.personId ?? SELF_ID}
        counterpartId={overrides.counterpartId ?? null}
        personPort={overrides.personPort ?? base.personPort}
        fortunePort={fortunePort}
        topUpPort={base.topUpPort}
      />
    </QueryClientProvider>,
  );
  return { ...view, fortunePort, createQuote, getQuote, purchase };
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

async function expectThrown(overrides: Injected = {}) {
  const silence = vi.spyOn(console, "error").mockImplementation(() => {});
  const base = ports();
  let caught: unknown = null;
  render(
    <QueryClientProvider client={makeQueryClient()}>
      <Boundary onCatch={(e) => (caught = e)}>
        <FortuneQuestionsScreen
          slug={overrides.slug ?? "love"}
          personId={overrides.personId ?? SELF_ID}
          counterpartId={overrides.counterpartId ?? null}
          personPort={
            "personPort" in overrides ? overrides.personPort : base.personPort
          }
          fortunePort={
            "fortunePort" in overrides
              ? overrides.fortunePort
              : base.fortunePort
          }
          topUpPort={base.topUpPort}
        />
      </Boundary>
    </QueryClientProvider>,
  );
  // 기본 retry 1 (makeQueryClient) 뒤에 던진다
  await screen.findByText("caught", {}, { timeout: 5000 });
  silence.mockRestore();
  return caught;
}

const ctaButtons = async () =>
  within(await screen.findByTestId("app-cta")).getAllByRole("button");

const err500 = () =>
  new ApiError({ status: 500, code: "INTERNAL_ERROR", traceId: null });

async function loveProducts(port: FortunePort) {
  return (await port.listProducts("LOVE")) as readonly FortuneProduct[];
}

describe("FortuneQuestionsScreen (FORT-02 · 03)", () => {
  it("a. love: 제목 · 라벨 · 버튼 둘(위가 부적 포함)이고, 부적 포함을 누르면 본인 이름의 팝업과 견적 요청", async () => {
    const user = userEvent.setup();
    const { createQuote } = setup();
    expect(
      await screen.findByRole("heading", { level: 1, name: "애정운" }),
    ).toBeInTheDocument();
    expect(screen.getByText("연애 상태")).toBeInTheDocument();
    expect(screen.getByText("알고 싶은 부분")).toBeInTheDocument();
    const buttons = await ctaButtons();
    expect(buttons.map((b) => b.textContent)).toEqual([
      TALISMAN_LABEL,
      ONLY_LABEL,
    ]);
    await user.click(buttons[0]);
    const dialog = await screen.findByRole("dialog");
    expect(await within(dialog).findByText(SELF_NAME)).toBeInTheDocument();
    await waitFor(() => expect(createQuote).toHaveBeenCalledTimes(1));
    expect(createQuote).toHaveBeenCalledWith({
      productCode: TALISMAN,
      personId: SELF_ID,
      counterpartPersonId: null,
    });
  });

  it("b. 사주만 보기를 누르면 견적 요청의 productCode 가 READING_ONLY 상품이다", async () => {
    const user = userEvent.setup();
    const { createQuote } = setup();
    await user.click((await ctaButtons())[1]);
    await waitFor(() => expect(createQuote).toHaveBeenCalledTimes(1));
    expect(createQuote.mock.calls[0][0].productCode).toBe(ONLY);
  });

  it("c. overall: 연애 상태 라벨은 없고 알고 싶은 부분은 있다", async () => {
    setup({ slug: "overall" });
    await ctaButtons();
    expect(screen.queryByText("연애 상태")).toBeNull();
    expect(screen.getByText("알고 싶은 부분")).toBeInTheDocument();
  });

  it("d. 잔액이 충분하면 구매 성공 뒤 결과 화면으로 한 번 이동한다", async () => {
    const user = userEvent.setup();
    const { purchase } = setup();
    await user.click((await ctaButtons())[0]);
    await user.click(await screen.findByRole("button", { name: "사용하기" }));
    await waitFor(() => expect(router.push).toHaveBeenCalledTimes(1));
    const result = await purchase.mock.results[0].value;
    expect(router.push).toHaveBeenCalledWith(`/fortune/r/${result.readingId}`);
  });

  it("e. READING_ONLY 만 active 면 사주만 버튼 하나만 있다", async () => {
    const { fortunePort } = ports();
    const all = await loveProducts(fortunePort);
    vi.spyOn(fortunePort, "listProducts").mockResolvedValue(
      all.filter((p) => p.option === "READING_ONLY"),
    );
    setup({ fortunePort });
    const buttons = await ctaButtons();
    expect(buttons.map((b) => b.textContent)).toEqual([ONLY_LABEL]);
  });

  it("f. active 상품이 없으면 판매 종료 문구만 보이고 두 버튼이 모두 없다", async () => {
    const { fortunePort } = ports();
    const all = await loveProducts(fortunePort);
    vi.spyOn(fortunePort, "listProducts").mockResolvedValue(
      all.map((p) => ({ ...p, active: false })),
    );
    setup({ fortunePort });
    expect(
      await screen.findByText("지금은 구매할 수 없습니다"),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: TALISMAN_LABEL })).toBeNull();
    expect(screen.queryByRole("button", { name: ONLY_LABEL })).toBeNull();
  });

  it("g. 같은 option 의 active 상품이 둘이면 던진다", async () => {
    const { fortunePort } = ports();
    const all = await loveProducts(fortunePort);
    const one = all.find((p) => p.option === "READING_ONLY") as FortuneProduct;
    vi.spyOn(fortunePort, "listProducts").mockResolvedValue([
      ...all,
      { ...one, code: "FIXTURE_LOVE_READING_ONLY_SECOND" },
    ]);
    const caught = await expectThrown({ fortunePort });
    expect(String(caught)).toContain("활성 상품이 하나가 아니다");
  });

  it("h. 다른 fortuneType 상품이 섞여 오면 던진다", async () => {
    const { fortunePort } = ports();
    const all = await loveProducts(fortunePort);
    vi.spyOn(fortunePort, "listProducts").mockResolvedValue([
      ...all,
      // 비활성이라 "활성 상품이 하나가 아니다" 검사와 겹치지 않고, 종류 불일치만 걸린다
      {
        ...all[0],
        code: "FIXTURE_WEALTH_X",
        fortuneType: "WEALTH",
        active: false,
      },
    ]);
    const caught = await expectThrown({ fortunePort });
    expect(String(caught)).toContain("다른 운세 종류");
  });

  it("i. 인물 조회가 500 이면 던진다", async () => {
    const personPort = ports().personPort;
    vi.spyOn(personPort, "list").mockRejectedValue(err500());
    const caught = await expectThrown({ personPort });
    expect((caught as ApiError).status).toBe(500);
  });

  it("i2. 상품 조회가 500 이면 던진다", async () => {
    const { fortunePort } = ports();
    vi.spyOn(fortunePort, "listProducts").mockRejectedValue(err500());
    const caught = await expectThrown({ fortunePort });
    expect((caught as ApiError).status).toBe(500);
  });

  it("j. personId 가 목록에 없으면 FORT-01 로 replace 한 번, push 없음, 버튼 · 견적 없음", async () => {
    replaceAllowed = true;
    const { createQuote } = setup({ personId: "no-such-person" });
    await waitFor(() => expect(router.replace).toHaveBeenCalledTimes(1));
    expect(router.replace).toHaveBeenCalledWith("/fortune/love");
    expect(router.push).not.toHaveBeenCalled();
    expect(screen.queryByRole("button", { name: TALISMAN_LABEL })).toBeNull();
    expect(screen.queryByRole("button", { name: ONLY_LABEL })).toBeNull();
    expect(createQuote).not.toHaveBeenCalled();
  });

  it("j2. 대상이 없을 때 같은 returnPath 의 저장값은 그대로 남고 팝업도 열리지 않는다", async () => {
    replaceAllowed = true;
    const saved = {
      returnPath: "/fortune/love/questions?personId=no-such-person",
      quoteId: "fixture-quote",
      selection: {
        productCode: TALISMAN,
        personId: "no-such-person",
        counterpartPersonId: null,
      },
    };
    savePurchaseSelection(saved);
    setup({ personId: "no-such-person" });
    await waitFor(() => expect(router.replace).toHaveBeenCalledTimes(1));
    expect(screen.queryByRole("dialog")).toBeNull();
    expect(loadPurchaseSelection()).toEqual(saved);
  });

  it("k. 복원: 같은 returnPath · personId · 활성 code 면 팝업이 저절로 열리고 저장한 견적을 재확인한다", async () => {
    const { fortunePort, personPort } = ports();
    const quote = await fortunePort.createQuote({
      productCode: TALISMAN,
      personId: OTHER.personId,
      counterpartPersonId: null,
    });
    savePurchaseSelection({
      returnPath: `/fortune/love/questions?personId=${OTHER.personId}`,
      quoteId: quote.quoteId,
      selection: {
        productCode: TALISMAN,
        personId: OTHER.personId,
        counterpartPersonId: null,
      },
    });
    const { createQuote, getQuote } = setup({
      fortunePort,
      personPort,
      personId: OTHER.personId,
    });
    const dialog = await screen.findByRole("dialog");
    expect(await within(dialog).findByText(OTHER.name)).toBeInTheDocument();
    await within(dialog).findByRole("button", { name: "사용하기" });
    expect(getQuote).toHaveBeenCalledWith(quote.quoteId);
    expect(createQuote).not.toHaveBeenCalled();
  });

  it("l. 복원: 다른 화면(/suneung)의 저장값은 팝업을 열지 않고 그대로 남는다", async () => {
    const saved = {
      returnPath: "/suneung",
      quoteId: "fixture-quote",
      selection: {
        productCode: TALISMAN,
        personId: SELF_ID,
        counterpartPersonId: null,
      },
    };
    savePurchaseSelection(saved);
    setup();
    await ctaButtons();
    expect(screen.queryByRole("dialog")).toBeNull();
    expect(loadPurchaseSelection()).toEqual(saved);
  });

  it("m. 복원: 같은 returnPath 인데 productCode 가 없는 code 면 팝업이 열리지 않고 저장값이 지워진다", async () => {
    savePurchaseSelection({
      returnPath: `/fortune/love/questions?personId=${SELF_ID}`,
      quoteId: "fixture-quote",
      selection: {
        productCode: "FIXTURE_LOVE_NO_SUCH",
        personId: SELF_ID,
        counterpartPersonId: null,
      },
    });
    setup();
    await ctaButtons();
    await waitFor(() => expect(loadPurchaseSelection()).toBeNull());
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("n. 복원: 같은 returnPath 인데 저장한 personId 가 다르면 팝업이 열리지 않고 저장값이 지워진다", async () => {
    savePurchaseSelection({
      returnPath: `/fortune/love/questions?personId=${SELF_ID}`,
      quoteId: "fixture-quote",
      selection: {
        productCode: TALISMAN,
        personId: OTHER.personId,
        counterpartPersonId: null,
      },
    });
    setup();
    await ctaButtons();
    await waitFor(() => expect(loadPurchaseSelection()).toBeNull());
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("o. 진짜 모드인데 구현이 없으면 조용히 넘어가지 않고 오류 화면으로 던진다", async () => {
    // 테스트 환경은 NEXT_PUBLIC_API_MODE 가 비어 있어 진짜 모드다
    const caught = await expectThrown({
      personPort: undefined,
      fortunePort: undefined,
    });
    expect(String(caught)).toContain("MOCK-PORT");
  });

  it("p. 팝업을 열기 전 본문에는 등껍질 글자도 숫자도 없다", async () => {
    setup();
    await ctaButtons();
    const body = screen.getByRole("main").textContent ?? "";
    expect(body).not.toContain("등껍질");
    expect(body).not.toMatch(/\d/);
    expect(screen.queryByRole("dialog")).toBeNull();
  });
  describe("궁합 MATCH-03 (본인 + 상대, Q-26)", () => {
    const COMPAT_TALISMAN = "FIXTURE_COMPATIBILITY_READING_WITH_TALISMAN";
    const compatPath = (personId: string, counterpartId: string) =>
      `/fortune/compatibility/questions?personId=${personId}&counterpartId=${counterpartId}`;

    it("r. 제목 궁합 · 관계 / 알고 싶은 부분 라벨 · 버튼 둘, 부적 포함을 누르면 두 사람 ID 로 견적 요청하고 팝업에 두 이름", async () => {
      const user = userEvent.setup();
      const { createQuote } = setup({
        slug: "compatibility",
        counterpartId: OTHER.personId,
      });
      expect(
        await screen.findByRole("heading", { level: 1, name: "궁합" }),
      ).toBeInTheDocument();
      expect(screen.getByText("관계")).toBeInTheDocument();
      expect(screen.getByText("알고 싶은 부분")).toBeInTheDocument();
      expect(screen.queryByText("연애 상태")).toBeNull();
      const buttons = await ctaButtons();
      expect(buttons.map((b) => b.textContent)).toEqual([
        TALISMAN_LABEL,
        ONLY_LABEL,
      ]);
      await user.click(buttons[0]);
      const dialog = await screen.findByRole("dialog");
      expect(
        await within(dialog).findByText(`${SELF_NAME} · ${OTHER.name}`),
      ).toBeInTheDocument();
      await waitFor(() => expect(createQuote).toHaveBeenCalledTimes(1));
      expect(createQuote).toHaveBeenCalledWith({
        productCode: COMPAT_TALISMAN,
        personId: SELF_ID,
        counterpartPersonId: OTHER.personId,
      });
    });

    it("s. 상대가 목록에 없으면 MATCH-01 로 replace 한 번, 버튼 · 견적 없음", async () => {
      replaceAllowed = true;
      const { createQuote } = setup({
        slug: "compatibility",
        counterpartId: "no-such-person",
      });
      await waitFor(() => expect(router.replace).toHaveBeenCalledTimes(1));
      expect(router.replace).toHaveBeenCalledWith("/fortune/compatibility");
      expect(screen.queryByRole("button", { name: TALISMAN_LABEL })).toBeNull();
      expect(createQuote).not.toHaveBeenCalled();
    });

    it("t. 상대가 본인(counterpartId === personId)이면 MATCH-01 로 replace", async () => {
      replaceAllowed = true;
      const { createQuote } = setup({
        slug: "compatibility",
        counterpartId: SELF_ID,
      });
      await waitFor(() => expect(router.replace).toHaveBeenCalledTimes(1));
      expect(router.replace).toHaveBeenCalledWith("/fortune/compatibility");
      expect(createQuote).not.toHaveBeenCalled();
    });

    it("t2. 첫 사람이 타인이고 상대가 본인이어도 MATCH-01 로 replace (첫 사람은 본인이어야 한다)", async () => {
      replaceAllowed = true;
      const { createQuote } = setup({
        slug: "compatibility",
        personId: OTHER.personId,
        counterpartId: SELF_ID,
      });
      await waitFor(() => expect(router.replace).toHaveBeenCalledTimes(1));
      expect(router.replace).toHaveBeenCalledWith("/fortune/compatibility");
      expect(screen.queryByRole("button", { name: TALISMAN_LABEL })).toBeNull();
      expect(createQuote).not.toHaveBeenCalled();
    });

    it("t3. 첫 사람도 상대도 타인이면(둘 다 본인이 아님) MATCH-01 로 replace — 첫 사람이 본인이어야 한다는 조건만 걸린다", async () => {
      replaceAllowed = true;
      const OTHER2 = {
        personId: "88888888-8888-4888-8888-888888888888",
        isSelf: false,
        name: "FIXTURE OTHER2",
      };
      const account = createFakeAccount("signed_in");
      account.addOther({ ...OTHER });
      account.addOther({ ...OTHER2 });
      const { createQuote } = setup({
        slug: "compatibility",
        personId: OTHER.personId,
        counterpartId: OTHER2.personId,
        personPort: createFakePersonPort(account),
      });
      await waitFor(() => expect(router.replace).toHaveBeenCalledTimes(1));
      expect(router.replace).toHaveBeenCalledWith("/fortune/compatibility");
      expect(screen.queryByRole("button", { name: TALISMAN_LABEL })).toBeNull();
      expect(createQuote).not.toHaveBeenCalled();
    });

    it("u. 복원: 같은 returnPath · personId · counterpartPersonId · 활성 code 면 팝업이 저절로 열리고 저장한 견적을 재확인한다", async () => {
      const { fortunePort, personPort } = ports();
      const quote = await fortunePort.createQuote({
        productCode: COMPAT_TALISMAN,
        personId: SELF_ID,
        counterpartPersonId: OTHER.personId,
      });
      savePurchaseSelection({
        returnPath: compatPath(SELF_ID, OTHER.personId),
        quoteId: quote.quoteId,
        selection: {
          productCode: COMPAT_TALISMAN,
          personId: SELF_ID,
          counterpartPersonId: OTHER.personId,
        },
      });
      const { createQuote, getQuote } = setup({
        slug: "compatibility",
        counterpartId: OTHER.personId,
        fortunePort,
        personPort,
      });
      const dialog = await screen.findByRole("dialog");
      await within(dialog).findByRole("button", { name: "사용하기" });
      expect(getQuote).toHaveBeenCalledWith(quote.quoteId);
      expect(createQuote).not.toHaveBeenCalled();
    });

    it("v. 복원: 같은 returnPath 인데 저장한 counterpartPersonId 가 다른 ID 면 팝업이 열리지 않고 저장값이 지워진다", async () => {
      savePurchaseSelection({
        returnPath: compatPath(SELF_ID, OTHER.personId),
        quoteId: "fixture-quote",
        selection: {
          productCode: COMPAT_TALISMAN,
          personId: SELF_ID,
          counterpartPersonId: "another-person",
        },
      });
      setup({ slug: "compatibility", counterpartId: OTHER.personId });
      await ctaButtons();
      await waitFor(() => expect(loadPurchaseSelection()).toBeNull());
      expect(screen.queryByRole("dialog")).toBeNull();
    });

    it("w. 복원: love 에서 저장한 counterpartPersonId 가 null 이 아니면 팝업이 열리지 않고 저장값이 지워진다", async () => {
      savePurchaseSelection({
        returnPath: `/fortune/love/questions?personId=${SELF_ID}`,
        quoteId: "fixture-quote",
        selection: {
          productCode: TALISMAN,
          personId: SELF_ID,
          counterpartPersonId: OTHER.personId,
        },
      });
      setup();
      await ctaButtons();
      await waitFor(() => expect(loadPurchaseSelection()).toBeNull());
      expect(screen.queryByRole("dialog")).toBeNull();
    });

    it("x. 궁합이 아닌 운세(love)에 counterpartId 가 오면 던진다", async () => {
      const caught = await expectThrown({ counterpartId: OTHER.personId });
      expect(String(caught)).toContain("상대 ID");
    });
  });
});
