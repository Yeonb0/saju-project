import { QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import type { SessionPort } from "@/lib/ports/session";
import { makeQueryClient } from "@/lib/queryClient";
import { createFakeAccount, type FakeSessionScenario } from "@/mocks/account";
import { createFakeSessionPort } from "@/mocks/session";
import { HomeScreen } from "./HomeScreen";

afterEach(cleanup);

// 픽스처일 뿐이며 실제 사용자와 무관하다.
function setup(scenario: FakeSessionScenario) {
  const account = createFakeAccount(scenario);
  const sessionPort: SessionPort = createFakeSessionPort({ account });
  render(
    <QueryClientProvider client={makeQueryClient()}>
      <HomeScreen sessionPort={sessionPort} />
    </QueryClientProvider>,
  );
}

describe("HomeScreen (HOME-03)", () => {
  it("H1. 로그인 상태에서도 /wallet 링크와 「충전하기」 가 없다 (DIRECT-PAY)", async () => {
    setup("signed_in");
    // 홈은 지갑을 조회하지 않는다 — 충전 포트를 import 하지 않는 것으로 막는다 (DIRECT-PAY)
    await waitFor(() =>
      expect(screen.getByRole("link", { name: "마이페이지" })).toBeVisible(),
    );
    expect(screen.queryByRole("link", { name: /충전하기/ })).toBeNull();
    expect(screen.queryByText("충전하기")).toBeNull();
    expect(document.querySelector('a[href="/wallet"]')).toBeNull();
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
});
