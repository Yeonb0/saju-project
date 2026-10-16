import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { RoutePlaceholder } from "./RoutePlaceholder";

// vitest 는 globals 를 켜지 않아 Testing Library 자동 정리가 동작하지 않는다.
afterEach(cleanup);

// 만들지 않은 화면의 공개 자리 (PG-4) — 개발용 경로 · 화면 ID · Figma 번호가 화면 글자에 나오지 않는다
describe("RoutePlaceholder", () => {
  it("a. 「준비 중이에요」 와 / 로 가는 「홈으로」 링크만 보이고, 경로 · 화면 ID · nodeId 글자는 없다", () => {
    const { container } = render(<RoutePlaceholder path="/today" />);
    expect(screen.getByText("준비 중이에요")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "홈으로" })).toHaveAttribute(
      "href",
      "/",
    );
    const text = container.textContent ?? "";
    expect(text).not.toContain("/today");
    expect(text).not.toContain("TODAY-01");
    expect(text).not.toContain("195:");
    // path 는 data-route 속성에만 있다
    expect(container.querySelector('[data-route="/today"]')).not.toBeNull();
  });

  it("a. 화면 ID 가 없는 경로도 「Figma」 · 화면 이름 글자가 없다", () => {
    const { container } = render(<RoutePlaceholder path="/share/[shareId]" />);
    const text = container.textContent ?? "";
    expect(text).not.toContain("Figma");
    expect(text).not.toContain("공유 랜딩");
    expect(text).not.toContain("/share/[shareId]");
    expect(screen.getByText("준비 중이에요")).toBeInTheDocument();
  });
});
