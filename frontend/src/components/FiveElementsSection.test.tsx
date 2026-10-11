import { QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { Component, type ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/errors";
import { type BasicSaju, ELEMENTS } from "@/lib/ports/basicSaju";
import { makeQueryClient } from "@/lib/queryClient";
import { FiveElementsSection } from "./FiveElementsSection";

// vitest 는 globals 를 켜지 않아 Testing Library 자동 정리가 동작하지 않는다.
afterEach(cleanup);

// 픽스처일 뿐이며 실제 계산 · 규칙과 무관하다. 사람마다 다른 값을 돌려주는 stub 이다.
const COUNTS: Record<string, readonly number[]> = {
  "person-a": [1, 2, 3, 4, 5],
  "person-b": [5, 4, 3, 2, 1],
};

function fixtureOf(personId: string): BasicSaju {
  return {
    fiveElements: ELEMENTS.map((element, i) => ({
      element,
      count: COUNTS[personId][i],
    })),
    birthTimeKnown: true,
    calculationVersion: "fixture-calc",
  };
}

function stub() {
  const getBasicSaju = vi.fn(async (personId: string) => fixtureOf(personId));
  return { port: { getBasicSaju }, getBasicSaju };
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

function ui(
  client: ReturnType<typeof makeQueryClient>,
  personId: string | null,
  port: ReturnType<typeof stub>["port"],
) {
  return (
    <QueryClientProvider client={client}>
      <FiveElementsSection personId={personId} port={port} />
    </QueryClientProvider>
  );
}

describe("FiveElementsSection (F-09)", () => {
  it("q1. personId 가 null 이면 조회하지 않고 회색 막대 5개만 있다", () => {
    const { port, getBasicSaju } = stub();
    const { container } = render(ui(makeQueryClient(), null, port));
    expect(getBasicSaju).not.toHaveBeenCalled();
    expect(screen.queryAllByRole("listitem")).toHaveLength(0);
    const bars = container.querySelectorAll("[aria-hidden] > div");
    expect(bars).toHaveLength(5);
  });

  it("q2. 값이 오면 목 · 화 · 토 · 금 · 수 순서로 stub 의 count 를 보이고 그 personId 로 1회 부른다", async () => {
    const { port, getBasicSaju } = stub();
    render(ui(makeQueryClient(), "person-a", port));
    const items = await screen.findAllByRole("listitem");
    expect(items.map((li) => li.textContent)).toEqual([
      "목1",
      "화2",
      "토3",
      "금4",
      "수5",
    ]);
    expect(getBasicSaju).toHaveBeenCalledTimes(1);
    expect(getBasicSaju).toHaveBeenCalledWith("person-a");
  });

  it("q3. personId 를 바꾸면 새 personId 로 부르고 그 사람의 count 가 보인다", async () => {
    const { port, getBasicSaju } = stub();
    const client = makeQueryClient();
    const { rerender } = render(ui(client, "person-a", port));
    await screen.findAllByRole("listitem");
    rerender(ui(client, "person-b", port));
    await waitFor(() =>
      expect(
        screen.getAllByRole("listitem").map((li) => li.textContent),
      ).toEqual(["목5", "화4", "토3", "금2", "수1"]),
    );
    expect(getBasicSaju).toHaveBeenCalledTimes(2);
    expect(getBasicSaju).toHaveBeenLastCalledWith("person-b");
  });

  it("q4. 422 BIRTH_TIME_REQUIRED_AT_TERM 이면 안내 자리가 보이고 막대는 없으며 던지지 않는다", async () => {
    const getBasicSaju = vi.fn(async () => {
      throw new ApiError({
        status: 422,
        code: "BIRTH_TIME_REQUIRED_AT_TERM",
        traceId: "fixture-trace",
      });
    });
    const { container } = render(
      ui(makeQueryClient(), "person-a", { getBasicSaju }),
    );
    expect(await screen.findByRole("status")).toBe(
      container.querySelector('[data-slot="birth-time-required"]'),
    );
    expect(screen.queryAllByRole("listitem")).toHaveLength(0);
    expect(screen.queryByText("caught")).toBeNull();
  });

  it("q5. 500 이면 오류 경계로 던진다", async () => {
    const silence = vi.spyOn(console, "error").mockImplementation(() => {});
    let caught: unknown = null;
    const getBasicSaju = vi.fn(async () => {
      throw new ApiError({
        status: 500,
        code: "INTERNAL_SERVER_ERROR",
        traceId: "fixture-trace",
      });
    });
    render(
      <QueryClientProvider client={makeQueryClient()}>
        <Boundary onCatch={(e) => (caught = e)}>
          <FiveElementsSection personId="person-a" port={{ getBasicSaju }} />
        </Boundary>
      </QueryClientProvider>,
    );
    expect(await screen.findByText("caught")).toBeInTheDocument();
    expect(caught).toBeInstanceOf(ApiError);
    expect((caught as ApiError).status).toBe(500);
    silence.mockRestore();
  });
});
