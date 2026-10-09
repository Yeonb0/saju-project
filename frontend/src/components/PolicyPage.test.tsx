import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import Privacy from "@/app/privacy/page";
import Refund from "@/app/refund/page";
import Terms from "@/app/terms/page";

// vitest 는 globals 를 켜지 않아 Testing Library 자동 정리가 동작하지 않는다.
afterEach(cleanup);

const PAGES = [
  ["이용약관", Terms],
  ["개인정보처리방침", Privacy],
  ["환불정책", Refund],
] as const;

describe("PolicyPage (INFO-02)", () => {
  it.each(PAGES)("z1. %s 페이지의 h1 은 제목 하나다", (title, Page) => {
    render(<Page />);
    const headings = screen.getAllByRole("heading", { level: 1 });
    expect(headings).toHaveLength(1);
    expect(headings[0]).toHaveTextContent(title);
  });

  it("z2. 시행일 · 목차 · 본문 자리는 하나씩 있고 모두 비어 있다", () => {
    const { container } = render(<Terms />);
    for (const slot of ["policy-effective-date", "policy-toc", "policy-body"]) {
      const found = container.querySelectorAll(`[data-slot="${slot}"]`);
      expect(found).toHaveLength(1);
      expect(found[0].textContent).toBe("");
    }
  });

  it("z3. 목차 자리는 이름이 목차인 navigation, 본문 자리는 article 이다", () => {
    const { container } = render(<Terms />);
    expect(screen.getByRole("navigation", { name: "목차" })).toBe(
      container.querySelector('[data-slot="policy-toc"]'),
    );
    expect(screen.getByRole("article")).toBe(
      container.querySelector('[data-slot="policy-body"]'),
    );
  });

  it("z4. 푸터(약관 navigation)가 본문 뒤에 있다", () => {
    render(<Terms />);
    const body = screen.getByRole("article");
    const footerNav = screen.getByRole("navigation", { name: "약관" });
    expect(
      body.compareDocumentPosition(footerNav) &
        Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });
});
