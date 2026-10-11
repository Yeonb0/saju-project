import { cleanup, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import Page from "./page";

// vitest 는 globals 를 켜지 않아 Testing Library 자동 정리가 동작하지 않는다.
afterEach(cleanup);

describe("/pay/fail (PAY-05)", () => {
  it("t. 실패 안내와 충전으로 돌아가기 · 홈으로 링크 — PG 원문 자리는 비어 있다", () => {
    const { container } = render(<Page />);
    expect(screen.getByRole("alert")).toHaveTextContent(
      "결제가 완료되지 않았습니다",
    );
    const links = within(screen.getByTestId("app-cta")).getAllByRole("link");
    expect(links.map((l) => l.textContent)).toEqual([
      "충전으로 돌아가기",
      "홈으로",
    ]);
    expect(links.map((l) => l.getAttribute("href"))).toEqual(["/wallet", "/"]);
    expect(screen.queryByRole("banner")).toBeNull();
    const note = container.querySelector('[data-slot="pay-result-note"]');
    expect(note).not.toBeNull();
    expect(note?.textContent).toBe("");
  });
});
