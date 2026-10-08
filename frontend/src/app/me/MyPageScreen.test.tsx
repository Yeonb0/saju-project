import { QueryClientProvider } from "@tanstack/react-query";
import {
  cleanup,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { Component, type ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/errors";
import type { PersonPort } from "@/lib/ports/person";
import type { SessionPort } from "@/lib/ports/session";
import {
  loadPurchaseSelection,
  savePurchaseSelection,
} from "@/lib/purchase/restore";
import { makeQueryClient } from "@/lib/queryClient";
import { createFakeAccount } from "@/mocks/account";
import { createFakePersonPort } from "@/mocks/person";
import { createFakeSessionPort } from "@/mocks/session";
import { MyPageScreen } from "./MyPageScreen";

const router = vi.hoisted(() => ({ replace: vi.fn(), push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => router }));

afterEach(() => {
  cleanup();
  router.replace.mockReset();
  router.push.mockReset();
  sessionStorage.clear();
});

// 픽스처일 뿐이며 실제 사용자와 무관하다. 닉네임 · 이름은 src/mocks 의 픽스처다.
const OTHER_NAME = "FIXTURE OTHER";

function ports() {
  const account = createFakeAccount("signed_in");
  account.addOther({
    personId: "99999999-9999-4999-8999-999999999999",
    isSelf: false,
    name: OTHER_NAME,
  });
  return {
    sessionPort: createFakeSessionPort({ account }),
    personPort: createFakePersonPort(account),
  };
}

function setup(
  overrides: { sessionPort?: SessionPort; personPort?: PersonPort } = {},
) {
  const base = ports();
  const sessionPort = overrides.sessionPort ?? base.sessionPort;
  const logoutSpy = vi.spyOn(sessionPort, "logout");
  const view = render(
    <QueryClientProvider client={makeQueryClient()}>
      <MyPageScreen
        sessionPort={sessionPort}
        personPort={overrides.personPort ?? base.personPort}
      />
    </QueryClientProvider>,
  );
  return { logoutSpy, container: view.container };
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

const logoutButton = () => screen.findByRole("button", { name: "로그아웃" });

describe("MyPageScreen (MY-01)", () => {
  it("a. 세션 닉네임이 계정 상자에 보인다", async () => {
    const { container } = setup();
    await waitFor(() =>
      expect(
        container.querySelector('[data-slot="account"]')?.textContent,
      ).toBe("FIXTURE"),
    );
  });

  it("b. 저장된 사람 수만큼 카드(수정 링크)가 본인 먼저 나오고 불러오기 버튼은 없다", async () => {
    setup();
    const edits = await screen.findAllByRole("link", { name: "수정" });
    expect(edits).toHaveLength(2);
    const cards = document.querySelectorAll('[data-frame="card"]');
    expect(cards).toHaveLength(2);
    // 본인 카드가 먼저, 그 위에 기본 프로필 라벨
    expect(cards[0].textContent).toContain("(본인)");
    expect(cards[1].textContent).toContain(OTHER_NAME);
    expect(screen.getByText("기본 프로필")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "저장된 다른 사용자 불러오기" }),
    ).toBeNull();
  });

  it("c. 로그아웃하면 세션 로그아웃 1회, 구매 선택이 지워지고 처음 화면으로 간다", async () => {
    savePurchaseSelection({
      returnPath: "/suneung",
      quoteId: "fixture-quote",
      selection: {
        productCode: "FIXTURE_SUNEUNG_READING_WITH_TALISMAN",
        personId: "66666666-6666-4666-8666-666666666666",
        counterpartPersonId: null,
      },
    });
    const { logoutSpy } = setup();
    (await logoutButton()).click();
    await waitFor(() => expect(router.replace).toHaveBeenCalledWith("/"));
    expect(router.replace).toHaveBeenCalledTimes(1);
    expect(logoutSpy).toHaveBeenCalledTimes(1);
    expect(loadPurchaseSelection()).toBeNull();
  });

  it("d. 같은 틱에 두 번 눌러도 로그아웃은 한 번", async () => {
    const { logoutSpy } = setup();
    const button = await logoutButton();
    button.click();
    button.click();
    await waitFor(() => expect(router.replace).toHaveBeenCalled());
    expect(logoutSpy).toHaveBeenCalledTimes(1);
  });

  it("e. 로그아웃이 500 이면 오류 문구를 보이고 이동하지 않는다", async () => {
    const { sessionPort, personPort } = ports();
    vi.spyOn(sessionPort, "logout").mockRejectedValue(
      new ApiError({ status: 500, code: "INTERNAL_ERROR", traceId: null }),
    );
    setup({ sessionPort, personPort });
    (await logoutButton()).click();
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "로그아웃하지 못했습니다",
    );
    expect(router.replace).not.toHaveBeenCalled();
  });

  it("f. 다른 사람 추가 링크와 약관 3 링크가 각자의 href 를 가진다", async () => {
    setup();
    expect(
      await screen.findByRole("link", { name: "다른 사람 추가" }),
    ).toHaveAttribute("href", "/me/people/new");
    // 푸터에도 같은 이름의 링크가 있어 본문 안내 목록(nav) 안에서만 찾는다
    const nav = within(screen.getByRole("navigation", { name: "안내" }));
    expect(nav.getByRole("link", { name: "약관" })).toHaveAttribute(
      "href",
      "/terms",
    );
    expect(nav.getByRole("link", { name: "환불정책" })).toHaveAttribute(
      "href",
      "/refund",
    );
    expect(nav.getByRole("link", { name: "개인정보처리방침" })).toHaveAttribute(
      "href",
      "/privacy",
    );
  });

  it("g. 인물 목록 조회가 500 이면 오류 화면으로 던진다", async () => {
    const silence = vi.spyOn(console, "error").mockImplementation(() => {});
    let caught: unknown = null;
    const { sessionPort, personPort } = ports();
    vi.spyOn(personPort, "list").mockRejectedValue(
      new ApiError({ status: 500, code: "INTERNAL_ERROR", traceId: null }),
    );
    render(
      <QueryClientProvider client={makeQueryClient()}>
        <Boundary onCatch={(e) => (caught = e)}>
          <MyPageScreen sessionPort={sessionPort} personPort={personPort} />
        </Boundary>
      </QueryClientProvider>,
    );
    // 기본 retry 1 (makeQueryClient) 뒤에 던진다
    await screen.findByText("caught", {}, { timeout: 5000 });
    expect(caught).toBeInstanceOf(ApiError);
    expect((caught as ApiError).status).toBe(500);
    silence.mockRestore();
  });

  it("h. 진짜 모드인데 구현이 없으면 조용히 넘어가지 않고 오류 화면으로 던진다", async () => {
    const silence = vi.spyOn(console, "error").mockImplementation(() => {});
    let caught: unknown = null;
    // 테스트 환경은 NEXT_PUBLIC_API_MODE 가 비어 있어 진짜 모드다
    render(
      <QueryClientProvider client={makeQueryClient()}>
        <Boundary onCatch={(e) => (caught = e)}>
          <MyPageScreen />
        </Boundary>
      </QueryClientProvider>,
    );
    await screen.findByText("caught", {}, { timeout: 5000 });
    expect(String(caught)).toContain("MOCK-PORT");
    silence.mockRestore();
  });
});
