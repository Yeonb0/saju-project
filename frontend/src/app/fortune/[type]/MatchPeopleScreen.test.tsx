import { QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Component, type ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/errors";
import type { PersonPort, PersonSummary } from "@/lib/ports/person";
import { makeQueryClient } from "@/lib/queryClient";
import { MatchPeopleScreen } from "./MatchPeopleScreen";

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
const OTHER2: PersonSummary = {
  personId: "88888888-8888-4888-8888-888888888888",
  isSelf: false,
  name: "FIXTURE OTHER2",
};

const stub = (people: readonly PersonSummary[]): PersonPort => ({
  list: async () => people,
  createSelf: async () => SELF,
  createOther: async () => OTHER,
});

function setup(personPort: PersonPort) {
  return render(
    <QueryClientProvider client={makeQueryClient()}>
      <MatchPeopleScreen personPort={personPort} />
    </QueryClientProvider>,
  );
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
        <MatchPeopleScreen personPort={personPort} />
      </Boundary>
    </QueryClientProvider>,
  );
  // 기본 retry 1 (makeQueryClient) 뒤에 던진다
  await screen.findByText("caught", {}, { timeout: 5000 });
  silence.mockRestore();
  return caught;
}

const proceed = () => screen.findByRole("button", { name: "진행" });
const openPicker = async (user: ReturnType<typeof userEvent.setup>) =>
  user.click(await screen.findByRole("button", { name: "상대 선택" }));

describe("MatchPeopleScreen (MATCH-01 · 02)", () => {
  it("a. 제목 궁합 · 첫 칸은 본인 · 진행은 처음에 disabled 이고 불러오기는 없다", async () => {
    setup(stub([SELF, OTHER]));
    expect(
      await screen.findByRole("heading", { level: 1, name: "궁합" }),
    ).toBeInTheDocument();
    expect(await screen.findByText("FIXTURE")).toBeInTheDocument();
    expect(screen.getByText("(본인)")).toBeInTheDocument();
    expect(await proceed()).toBeDisabled();
    // 타인이 있어도 첫 칸은 본인 고정이라 불러오기가 없다
    expect(
      screen.queryByRole("button", { name: "저장된 다른 사용자 불러오기" }),
    ).toBeNull();
  });

  it("b. 시트에는 타인만 있고, 타인을 고르면 둘째 칸에 보이며 진행이 두 ID 로 이동한다", async () => {
    const user = userEvent.setup();
    setup(stub([SELF, OTHER]));
    await openPicker(user);
    const sheet = await screen.findByRole("dialog");
    expect(
      within(sheet).getByRole("button", { name: OTHER.name }),
    ).toBeInTheDocument();
    expect(within(sheet).queryByRole("button", { name: SELF.name })).toBeNull();
    await user.click(within(sheet).getByRole("button", { name: OTHER.name }));

    expect(await screen.findByText(OTHER.name)).toBeInTheDocument();
    const go = await proceed();
    expect(go).toBeEnabled();
    await user.click(go);
    expect(push).toHaveBeenCalledTimes(1);
    expect(push).toHaveBeenCalledWith(
      `/fortune/compatibility/questions?personId=${encodeURIComponent(SELF.personId)}&counterpartId=${encodeURIComponent(OTHER.personId)}`,
    );
  });

  it("c. 타인이 없으면 시트에 인물 버튼이 없고 새로 입력 링크는 /me/people/new, 진행은 disabled", async () => {
    const user = userEvent.setup();
    setup(stub([SELF]));
    await openPicker(user);
    const sheet = await screen.findByRole("dialog");
    expect(within(sheet).queryAllByRole("button")).toHaveLength(0);
    expect(
      within(sheet).getByRole("link", { name: "새로 입력" }),
    ).toHaveAttribute("href", "/me/people/new");
    // 시트가 열려 있는 동안 뒷화면은 접근성 트리에서 숨겨지므로 hidden 포함해 찾는다
    expect(
      screen.getByRole("button", { name: "진행", hidden: true }),
    ).toBeDisabled();
  });

  it("d. 타인 둘: 고른 뒤 둘째 칸의 불러오기로 바꾸면 이동하는 counterpartId 가 바뀐다", async () => {
    const user = userEvent.setup();
    setup(stub([SELF, OTHER, OTHER2]));
    await openPicker(user);
    await user.click(
      within(await screen.findByRole("dialog")).getByRole("button", {
        name: OTHER.name,
      }),
    );
    // 둘째 칸이 채워졌고 타인이 둘이라 그 카드에 불러오기가 생긴다
    await user.click(
      await screen.findByRole("button", {
        name: "저장된 다른 사용자 불러오기",
      }),
    );
    await user.click(
      within(await screen.findByRole("dialog")).getByRole("button", {
        name: OTHER2.name,
      }),
    );
    await user.click(await proceed());
    expect(push).toHaveBeenCalledTimes(1);
    expect(push).toHaveBeenCalledWith(
      `/fortune/compatibility/questions?personId=${encodeURIComponent(SELF.personId)}&counterpartId=${encodeURIComponent(OTHER2.personId)}`,
    );
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

  it("i. 본문에는 등껍질 글자도 숫자도 없다", async () => {
    setup(stub([SELF, OTHER]));
    await proceed();
    const body = screen.getByRole("main").textContent ?? "";
    expect(body).not.toContain("등껍질");
    expect(body).not.toMatch(/\d/);
  });
});
