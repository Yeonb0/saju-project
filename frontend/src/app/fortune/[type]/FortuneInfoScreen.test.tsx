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
import { FORTUNE_LABELS } from "@/lib/navigation";
import { type BasicSaju, ELEMENTS } from "@/lib/ports/basicSaju";
import type { PersonPort, PersonSummary } from "@/lib/ports/person";
import { makeQueryClient } from "@/lib/queryClient";
import { createFakeAccount } from "@/mocks/account";
import { createFakePersonPort } from "@/mocks/person";
import { FortuneInfoScreen } from "./FortuneInfoScreen";

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
});

// 픽스처일 뿐이며 실제 사용자와 무관하다
const SELF: PersonSummary = {
  personId: "55555555-5555-4555-8555-555555555555",
  isSelf: true,
  name: "FIXTURE",
};
const OTHER: PersonSummary = {
  personId: "99999999-9999-4999-8999-999999999999",
  isSelf: false,
  name: "FIXTURE OTHER",
};

const stub = (people: readonly PersonSummary[]): PersonPort => ({
  list: async () => people,
  createSelf: async () => SELF,
  createOther: async () => OTHER,
});

function fakeAccountPort() {
  const account = createFakeAccount("signed_in");
  account.addOther({ ...OTHER });
  return createFakePersonPort(account);
}

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

function setup(
  personPort: PersonPort = fakeAccountPort(),
  slug: "love" | "wealth" | "overall" | "sinsal" = "love",
) {
  const sajuStub = basicSajuStub();
  const rendered = render(
    <QueryClientProvider client={makeQueryClient()}>
      <FortuneInfoScreen
        slug={slug}
        personPort={personPort}
        basicSajuPort={sajuStub.port}
      />
    </QueryClientProvider>,
  );
  return { ...rendered, getBasicSaju: sajuStub.getBasicSaju };
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

async function expectThrown(personPort?: PersonPort) {
  const silence = vi.spyOn(console, "error").mockImplementation(() => {});
  let caught: unknown = null;
  render(
    <QueryClientProvider client={makeQueryClient()}>
      <Boundary onCatch={(e) => (caught = e)}>
        <FortuneInfoScreen slug="love" personPort={personPort} />
      </Boundary>
    </QueryClientProvider>,
  );
  // 기본 retry 1 (makeQueryClient) 뒤에 던진다
  await screen.findByText("caught", {}, { timeout: 5000 });
  silence.mockRestore();
  return caught;
}

const proceed = () => screen.findByRole("button", { name: "진행" });

describe("FortuneInfoScreen (FORT-01)", () => {
  it("a. 제목 · 본인 카드가 보이고, 진행을 누르면 질문 화면으로 본인 personId 와 함께 한 번 이동한다", async () => {
    const user = userEvent.setup();
    setup();
    expect(
      await screen.findByRole("heading", { level: 1, name: "애정운" }),
    ).toBeInTheDocument();
    expect(await screen.findByText("FIXTURE")).toBeInTheDocument();
    await user.click(await proceed());
    expect(push).toHaveBeenCalledTimes(1);
    expect(push).toHaveBeenCalledWith(
      `/fortune/love/questions?personId=${SELF.personId}`,
    );
  });

  it("b. 불러오기로 다른 인물을 고르면 그 인물의 ID 로 이동한다", async () => {
    const user = userEvent.setup();
    setup();
    await user.click(
      await screen.findByRole("button", {
        name: "저장된 다른 사용자 불러오기",
      }),
    );
    await user.click(screen.getByRole("button", { name: OTHER.name }));
    await user.click(await proceed());
    expect(push).toHaveBeenCalledTimes(1);
    expect(push).toHaveBeenCalledWith(
      `/fortune/love/questions?personId=${encodeURIComponent(OTHER.personId)}`,
    );
  });

  it("c. 목록 순서가 [타인, 본인] 이어도 기본 대상은 본인이다", async () => {
    const user = userEvent.setup();
    setup(stub([OTHER, SELF]));
    const card = (await screen.findByText("FIXTURE")).closest(
      '[data-frame="card"]',
    );
    expect(card).not.toBeNull();
    expect(card?.textContent).not.toContain(OTHER.name);
    await user.click(await proceed());
    expect(push).toHaveBeenCalledWith(
      `/fortune/love/questions?personId=${SELF.personId}`,
    );
  });

  it.each([
    ["overall", "종합운"],
    ["wealth", "재물운"],
    ["sinsal", "신살"],
  ] as const)("d. %s 의 제목은 FORTUNE_LABELS 값(%s)이다", async (slug, label) => {
    expect(FORTUNE_LABELS[slug]).toBe(label);
    setup(fakeAccountPort(), slug);
    expect(
      await screen.findByRole("heading", { level: 1, name: label }),
    ).toBeInTheDocument();
  });

  it("e. 인물 조회가 500 이면 오류 화면으로 던진다", async () => {
    const personPort = stub([SELF]);
    personPort.list = vi
      .fn()
      .mockRejectedValue(
        new ApiError({ status: 500, code: "INTERNAL_ERROR", traceId: null }),
      );
    const caught = await expectThrown(personPort);
    expect(caught).toBeInstanceOf(ApiError);
    expect((caught as ApiError).status).toBe(500);
  });

  it("f. 인물 목록이 비어 있으면 던진다", async () => {
    const caught = await expectThrown(stub([]));
    expect(String(caught)).toContain("인물 목록이 비어 있다");
  });

  it("g. 본인이 없는 목록이면 던진다", async () => {
    const caught = await expectThrown(stub([OTHER]));
    expect(String(caught)).toContain("본인이 없다");
  });

  it("h. 진짜 모드인데 구현이 없으면 조용히 넘어가지 않고 오류 화면으로 던진다", async () => {
    // 테스트 환경은 NEXT_PUBLIC_API_MODE 가 비어 있어 진짜 모드다
    const caught = await expectThrown(undefined);
    expect(String(caught)).toContain("MOCK-PORT");
  });

  it("i. 오행분석 섹션에는 li 5개가 있고, 섹션 밖 본문에는 등껍질 글자나 숫자가 없다", async () => {
    const { container } = setup();
    await proceed();
    const section = container.querySelector('[data-slot="five-elements"]');
    expect(section).not.toBeNull();
    await waitFor(() =>
      expect(
        within(section as HTMLElement).getAllByRole("listitem"),
      ).toHaveLength(5),
    );
    // 섹션 밖 본문(푸터 제외) 어디에도 등껍질 글자나 숫자가 없다
    const body = screen.getByRole("main").textContent ?? "";
    const outside = body.replace(section?.textContent ?? "", "");
    expect(outside).not.toContain("등껍질");
    expect(outside).not.toMatch(/\d/);
  });

  it("j. 저장된 다른 사용자를 고르면 오행분석을 그 사람의 personId 로 조회한다", async () => {
    const user = userEvent.setup();
    const { getBasicSaju } = setup();
    await waitFor(() =>
      expect(getBasicSaju).toHaveBeenCalledWith(SELF.personId),
    );
    await user.click(
      await screen.findByRole("button", {
        name: "저장된 다른 사용자 불러오기",
      }),
    );
    await user.click(screen.getByRole("button", { name: OTHER.name }));
    await waitFor(() =>
      expect(getBasicSaju).toHaveBeenCalledWith(OTHER.personId),
    );
  });
});
