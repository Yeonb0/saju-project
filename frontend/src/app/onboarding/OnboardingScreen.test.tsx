import { QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Component, type ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiContractError, ApiError } from "@/lib/api/errors";
import { loginHref } from "@/lib/auth/returnTo";
import type { PersonPort } from "@/lib/ports/person";
import { makeQueryClient } from "@/lib/queryClient";
import { createFakeAccount, type FakeSessionScenario } from "@/mocks/account";
import { createFakePersonPort } from "@/mocks/person";
import { createFakeSessionPort } from "@/mocks/session";
import { OnboardingScreen } from "./OnboardingScreen";

const router = vi.hoisted(() => ({ replace: vi.fn(), push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => router }));

// vitest 는 globals 를 켜지 않아 Testing Library 자동 정리가 동작하지 않는다.
afterEach(cleanup);

beforeEach(() => {
  router.replace.mockClear();
  router.push.mockClear();
});

// 픽스처일 뿐이며 실제 사용자 · 규칙과 무관하다.
function setup(
  scenario: FakeSessionScenario,
  wrapPort?: (port: PersonPort) => PersonPort,
) {
  const account = createFakeAccount(scenario);
  const sessionPort = createFakeSessionPort({ account });
  const base = createFakePersonPort(account);
  const personPort = wrapPort ? wrapPort(base) : base;
  const createSelf = vi.spyOn(personPort, "createSelf");
  const probe = makeProbe();
  const client = makeQueryClient();
  client.setDefaultOptions({ queries: { retry: false } });
  const user = userEvent.setup();
  render(
    <QueryClientProvider client={client}>
      <probe.Boundary>
        <OnboardingScreen
          returnTo="/wallet"
          personPort={personPort}
          sessionPort={sessionPort}
        />
      </probe.Boundary>
    </QueryClientProvider>,
  );
  return { user, createSelf, sessionPort, probe };
}

async function fillAndSave(user: ReturnType<typeof userEvent.setup>) {
  await user.type(await screen.findByLabelText("이름"), "픽스처");
  await user.type(screen.getByLabelText("생년월일"), "2008-03-15");
  await user.click(screen.getByRole("radio", { name: "양력" }));
  await user.click(screen.getByRole("checkbox", { name: "시간 모름" }));
  await user.selectOptions(screen.getByLabelText("성별"), "여성");
  await user.click(screen.getByRole("button", { name: "저장하기" }));
}

const failWith = (error: unknown) => (port: PersonPort) => ({
  ...port,
  createSelf: async () => {
    throw error;
  },
});

describe("OnboardingScreen (HOME-02 · A-03)", () => {
  it("저장하면 returnTo 로 돌아가고 같은 계정의 세션은 본인 정보 있음", async () => {
    const { user, createSelf, sessionPort } = setup("signed_in_without_person");
    await fillAndSave(user);
    await waitFor(() => expect(router.replace).toHaveBeenCalledWith("/wallet"));
    expect(createSelf).toHaveBeenCalledTimes(1);
    await expect(sessionPort.getSession()).resolves.toMatchObject({
      hasPrimaryPerson: true,
    });
  });

  it("이미 본인 정보가 있으면 폼 없이 returnTo 로 보낸다", async () => {
    setup("signed_in");
    await waitFor(() => expect(router.replace).toHaveBeenCalledWith("/wallet"));
    expect(screen.queryByLabelText("이름")).toBeNull();
  });

  it("500 으로 실패하면 안내만 보이고 이동 없이 입력값을 유지한다", async () => {
    const { user } = setup(
      "signed_in_without_person",
      failWith(new ApiError({ status: 500, code: null, traceId: "fixture" })),
    );
    await fillAndSave(user);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "저장하지 못했습니다",
    );
    expect(router.replace).not.toHaveBeenCalled();
    expect(screen.getByLabelText("이름")).toHaveValue("픽스처");
  });

  it("401 이면 로그인 링크를 보인다", async () => {
    const { user } = setup(
      "signed_in_without_person",
      failWith(
        new ApiError({
          status: 401,
          code: "AUTHENTICATION_REQUIRED",
          traceId: "fixture",
        }),
      ),
    );
    await fillAndSave(user);
    const link = await screen.findByRole("link", {
      name: "다시 로그인해 주세요",
    });
    expect(link).toHaveAttribute("href", loginHref("/wallet"));
  });

  it("응답 계약 위반은 오류 경계로 간다", async () => {
    const silence = vi.spyOn(console, "error").mockImplementation(() => {});
    const contract = new ApiContractError(200, "fixture");
    const { user, probe } = setup(
      "signed_in_without_person",
      failWith(contract),
    );
    await fillAndSave(user);
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
