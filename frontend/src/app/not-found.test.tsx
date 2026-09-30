import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import NotFound from "./not-found";

// vitest 는 globals 를 켜지 않아 Testing Library 자동 정리가 동작하지 않는다.
afterEach(cleanup);

describe("app/not-found.tsx", () => {
  it('안내 문구가 보이고 "홈으로" 링크가 / 로 간다', () => {
    render(<NotFound />);
    expect(screen.getByText("페이지를 찾을 수 없습니다")).toBeVisible();
    expect(screen.getByRole("link", { name: "홈으로" })).toHaveAttribute(
      "href",
      "/",
    );
  });

  it("AppShell 이 유지된다: 메뉴 열기 버튼", () => {
    render(<NotFound />);
    expect(screen.getByRole("button", { name: "메뉴 열기" })).toBeVisible();
  });
});
