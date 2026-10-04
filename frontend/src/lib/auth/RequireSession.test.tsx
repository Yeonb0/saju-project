import { QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { Component, type ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiContractError } from "@/lib/api/errors";
import type { SessionPort, SessionState } from "@/lib/ports/session";
import { makeQueryClient } from "@/lib/queryClient";
import { createFakeSessionPort } from "@/mocks/session";
import { RequireSession, SESSION_QUERY_KEY } from "./RequireSession";

const router = vi.hoisted(() => ({ replace: vi.fn(), push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => router }));

// vitest 는 globals 를 켜지 않아 Testing Library 자동 정리가 동작하지 않는다.
afterEach(cleanup);

beforeEach(() => {
  router.replace.mockClear();
  window.history.pushState({}, "", "/wallet?x=1");
});

// 픽스처일 뿐이며 실제 사용자 · 규칙과 무관하다.
function portOf(session: Promise<SessionState>): SessionPort {
  return {
    getSession: () => session,
    startLogin: vi.fn(async () => {}),
    logout: vi.fn(async () => {}),
  };
}

function setup(
  port: SessionPort,
  props: { requirePerson?: boolean } = {},
  client = makeQueryClient(),
) {
  // 오류 경계 테스트가 재시도 대기로 늘어지지 않게
  client.setDefaultOptions({ queries: { retry: false } });
  render(
    <QueryClientProvider client={client}>
      <RequireSession port={port} {...props}>
        <p>protected</p>
      </RequireSession>
    </QueryClientProvider>,
  );
}

describe("RequireSession (PG-2 · A-02 · A-03)", () => {
  it("signed_out 이면 로그인으로 보내고 children 은 그리지 않는다", async () => {
    setup(createFakeSessionPort({ scenario: "signed_out" }));
    await waitFor(() =>
      expect(router.replace).toHaveBeenCalledWith(
        "/login?returnTo=%2Fwallet%3Fx%3D1",
      ),
    );
    expect(screen.queryByText("protected")).toBeNull();
  });

  it("본인 정보가 없으면 온보딩으로 보낸다", async () => {
    setup(createFakeSessionPort({ scenario: "signed_in_without_person" }));
    await waitFor(() =>
      expect(router.replace).toHaveBeenCalledWith(
        "/onboarding?returnTo=%2Fwallet%3Fx%3D1",
      ),
    );
    expect(router.replace).not.toHaveBeenCalledWith(
      expect.stringContaining("/login"),
    );
    expect(screen.queryByText("protected")).toBeNull();
  });

  it("requirePerson={false} 면 본인 정보가 없어도 children", async () => {
    setup(createFakeSessionPort({ scenario: "signed_in_without_person" }), {
      requirePerson: false,
    });
    expect(await screen.findByText("protected")).toBeVisible();
    expect(router.replace).not.toHaveBeenCalled();
  });

  it("로그인 · 본인 정보 있음이면 children", async () => {
    setup(createFakeSessionPort({ scenario: "signed_in" }));
    expect(await screen.findByText("protected")).toBeVisible();
    expect(router.replace).not.toHaveBeenCalled();
  });

  it("조회가 끝나기 전에는 아무것도 하지 않는다", async () => {
    setup(portOf(new Promise<SessionState>(() => {})));
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(screen.queryByText("protected")).toBeNull();
    expect(router.replace).not.toHaveBeenCalled();
  });

  it("캐시에 옛 signed_out 이 있어도 새로 받은 값으로 판단한다", async () => {
    const client = makeQueryClient();
    client.setQueryData(SESSION_QUERY_KEY, { status: "signed_out" });
    setup(createFakeSessionPort({ scenario: "signed_in" }), {}, client);
    expect(await screen.findByText("protected")).toBeVisible();
    expect(router.replace).not.toHaveBeenCalled();
  });

  it("조회 실패는 오류 경계로 간다", async () => {
    const silence = vi.spyOn(console, "error").mockImplementation(() => {});
    const contract = new ApiContractError(200, "fixture");
    const probe = makeProbe();
    const client = makeQueryClient();
    client.setDefaultOptions({ queries: { retry: false } });
    render(
      <QueryClientProvider client={client}>
        <probe.Boundary>
          <RequireSession port={portOf(Promise.reject(contract))}>
            <p>protected</p>
          </RequireSession>
        </probe.Boundary>
      </QueryClientProvider>,
    );
    expect(await screen.findByText("caught")).toBeInTheDocument();
    expect(probe.caught()).toBe(contract);
    silence.mockRestore();
  });
});

// 오류 경계 — 던진 오류를 붙잡아 확인한다 (error.tsx 자리)
function makeProbe() {
  let last: unknown = null;
  class Boundary extends Component<
    { children: ReactNode },
    { error: unknown }
  > {
    state = { error: null as unknown };
    static getDerivedStateFromError(error: unknown) {
      return { error };
    }
    componentDidCatch(error: unknown) {
      last = error;
    }
    render() {
      return this.state.error ? <p>caught</p> : this.props.children;
    }
  }
  return { Boundary, caught: () => last };
}
