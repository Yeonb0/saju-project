import { QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { Component, type ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { SessionPort } from "@/lib/ports/session";
import type { TopUpPort } from "@/lib/ports/topUp";
import { makeQueryClient } from "@/lib/queryClient";
import { createFakeAccount, type FakeSessionScenario } from "@/mocks/account";
import { createFakeSessionPort } from "@/mocks/session";
import { createFakeTopUpPort } from "@/mocks/topUp";
import { createFakeWallet } from "@/mocks/wallet";
import { HomeScreen } from "./HomeScreen";

afterEach(cleanup);

// 픽스처일 뿐이며 실제 사용자 · 잔액과 무관하다. 지갑 잔액은 src/mocks/wallet.ts 의 기본 픽스처(7)다.
function setup(scenario: FakeSessionScenario) {
  const account = createFakeAccount(scenario);
  const sessionPort: SessionPort = createFakeSessionPort({ account });
  const topUpPort: TopUpPort = createFakeTopUpPort({
    wallet: createFakeWallet(),
  });
  const getWallet = vi.spyOn(topUpPort, "getWallet");
  render(
    <QueryClientProvider client={makeQueryClient()}>
      <HomeScreen sessionPort={sessionPort} topUpPort={topUpPort} />
    </QueryClientProvider>,
  );
  return { getWallet };
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

describe("HomeScreen (HOME-03)", () => {
  it("b. 비로그인이면 잔액 글자가 없고 충전하기 링크는 /wallet, 지갑 조회는 하지 않는다", async () => {
    const { getWallet } = setup("signed_out");
    const link = await screen.findByRole("link", { name: "충전하기" });
    expect(link).toHaveAttribute("href", "/wallet");
    // 세션이 도착해 비로그인으로 판단될 때까지 기다린 뒤에도 조회가 없다
    await waitFor(() => expect(link.textContent).toBe("충전하기"));
    expect(getWallet).not.toHaveBeenCalled();
  });

  it("c. 로그인이면 지갑 픽스처 잔액(7)이 보인다", async () => {
    const { getWallet } = setup("signed_in");
    // 이름은 잔액 줄과 충전하기 줄을 이은 글자 (줄 사이 공백 유무와 무관하게)
    expect(
      await screen.findByRole("link", { name: /^7s*충전하기$/ }),
    ).toHaveAttribute("href", "/wallet");
    expect(getWallet).toHaveBeenCalledTimes(1);
  });

  it("d. 카드 · 타일 · 마이페이지 링크가 각자의 href 를 가진다", () => {
    setup("signed_out");
    const hrefs: Array<[string, string]> = [
      ["오늘의 운세", "/today"],
      ["부적 모음", "/vault"],
      ["종합운", "/fortune/overall"],
      ["애정운", "/fortune/love"],
      ["재물운", "/fortune/wealth"],
      ["궁합", "/fortune/compatibility"],
      ["신살", "/fortune/sinsal"],
      ["수능운", "/suneung"],
      ["선물하기", "/gift/new"],
      ["마이페이지", "/me"],
    ];
    for (const [name, href] of hrefs) {
      expect(screen.getByRole("link", { name })).toHaveAttribute("href", href);
    }
  });

  it("e. 준비 중 타일은 링크가 아니다", () => {
    setup("signed_out");
    const tile = screen.getByText("준비 중");
    expect(tile.closest("a")).toBeNull();
    expect(tile).toHaveAttribute("aria-disabled", "true");
    expect(screen.queryByRole("link", { name: "준비 중" })).toBeNull();
  });

  it("f. h1 은 뿌기사주 하나이고 헤더(banner)는 없다", () => {
    setup("signed_out");
    expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
    expect(
      screen.getByRole("heading", { level: 1, name: "뿌기사주" }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("banner")).toBeNull();
  });

  it("g. 진짜 모드인데 구현이 없으면 조용히 넘어가지 않고 오류 화면으로 던진다", async () => {
    const silence = vi.spyOn(console, "error").mockImplementation(() => {});
    let caught: unknown = null;
    // 테스트 환경은 NEXT_PUBLIC_API_MODE 가 비어 있어 진짜 모드다
    render(
      <QueryClientProvider client={makeQueryClient()}>
        <Boundary onCatch={(e) => (caught = e)}>
          <HomeScreen />
        </Boundary>
      </QueryClientProvider>,
    );
    await screen.findByText("caught", {}, { timeout: 5000 });
    expect(String(caught)).toContain("MOCK-PORT");
    silence.mockRestore();
  });
});
