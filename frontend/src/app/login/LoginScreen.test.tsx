import { QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { onboardingHref } from "@/lib/auth/returnTo";
import type { SessionPort } from "@/lib/ports/session";
import { makeQueryClient } from "@/lib/queryClient";
import {
  createFakeSessionPort,
  type FakeSessionScenario,
} from "@/mocks/session";
import { LoginScreen } from "./LoginScreen";

const router = vi.hoisted(() => ({ replace: vi.fn(), push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => router }));

// vitest 는 globals 를 켜지 않아 Testing Library 자동 정리가 동작하지 않는다.
afterEach(cleanup);

beforeEach(() => {
  router.replace.mockClear();
  router.push.mockClear();
});

// 픽스처일 뿐이며 실제 사용자 · 규칙과 무관하다.
function setup(scenario: FakeSessionScenario, returnTo = "/wallet") {
  const port: SessionPort = createFakeSessionPort({ scenario });
  const startLogin = vi.spyOn(port, "startLogin");
  render(
    <QueryClientProvider client={makeQueryClient()}>
      <LoginScreen returnTo={returnTo} port={port} />
    </QueryClientProvider>,
  );
  return { startLogin };
}

describe("LoginScreen (HOME-01 · PG-2)", () => {
  it("버튼을 누르면 returnTo 와 함께 로그인을 시작하고 그 주소로 보낸다", async () => {
    const { startLogin } = setup("signed_out");
    screen.getByRole("button", { name: "카카오로 시작하기" }).click();
    await waitFor(() => expect(router.push).toHaveBeenCalledWith("/wallet"));
    expect(startLogin).toHaveBeenCalledTimes(1);
    expect(startLogin).toHaveBeenCalledWith("/wallet", expect.any(Function));
  });

  it("서비스명 h1 은 뿌기사주 하나이고, 헤더(banner)는 없다 (LAYOUT-FIGMA)", () => {
    setup("signed_out");
    expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
    expect(
      screen.getByRole("heading", { level: 1, name: "뿌기사주" }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("banner")).toBeNull();
  });

  it("같은 틱에 두 번 눌러도 로그인은 한 번", async () => {
    const { startLogin } = setup("signed_out");
    const button = screen.getByRole("button", { name: "카카오로 시작하기" });
    button.click();
    button.click();
    await waitFor(() => expect(router.push).toHaveBeenCalled());
    expect(startLogin).toHaveBeenCalledTimes(1);
  });

  it("이미 로그인 · 본인 정보 있음이면 returnTo 로 보낸다", async () => {
    setup("signed_in");
    await waitFor(() => expect(router.replace).toHaveBeenCalledWith("/wallet"));
  });

  it("이미 로그인 · 본인 정보 없음이면 온보딩으로 보낸다", async () => {
    setup("signed_in_without_person");
    await waitFor(() =>
      expect(router.replace).toHaveBeenCalledWith(onboardingHref("/wallet")),
    );
  });

  it("signed_out 이면 이동하지 않는다", async () => {
    setup("signed_out");
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(router.replace).not.toHaveBeenCalled();
  });
});
