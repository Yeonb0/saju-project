import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";

vi.mock("@sentry/nextjs", () => ({ captureException: vi.fn() }));

import * as Sentry from "@sentry/nextjs";
import RouteError from "./error";

// vitest 는 globals 를 켜지 않아 Testing Library 자동 정리가 동작하지 않는다.
afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("app/error.tsx", () => {
  it("오류 문구가 보이고, 전달한 error 로 captureException 이 1회 호출된다", () => {
    const error = new Error("boom");
    render(<RouteError error={error} retry={() => {}} />);
    expect(screen.getByText("오류가 발생했습니다")).toBeVisible();
    expect(Sentry.captureException).toHaveBeenCalledTimes(1);
    expect(Sentry.captureException).toHaveBeenCalledWith(error);
  });

  it('"다시 시도"를 누르면 retry 가 1회 호출된다', async () => {
    const retry = vi.fn();
    render(<RouteError error={new Error("boom")} retry={retry} />);
    await userEvent
      .setup()
      .click(screen.getByRole("button", { name: "다시 시도" }));
    expect(retry).toHaveBeenCalledTimes(1);
  });

  it("AppShell 이 유지된다: 뒤로 링크와 메뉴 열기 버튼", () => {
    render(<RouteError error={new Error("boom")} retry={() => {}} />);
    expect(screen.getByRole("link", { name: "뒤로" })).toHaveAttribute(
      "href",
      "/",
    );
    expect(screen.getByRole("button", { name: "메뉴 열기" })).toBeVisible();
  });

  it("error.message 는 화면에 나오지 않는다", () => {
    const error = new Error("secret-internal-message") as Error & {
      digest?: string;
    };
    error.digest = "digest-123";
    render(<RouteError error={error} retry={() => {}} />);
    expect(
      screen.queryByText(/secret-internal-message/),
    ).not.toBeInTheDocument();
    expect(screen.queryByText(/digest-123/)).not.toBeInTheDocument();
  });
});
