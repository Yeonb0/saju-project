import { cleanup, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import Privacy from "@/app/privacy/page";
import Refund from "@/app/refund/page";
import Terms from "@/app/terms/page";
import { PolicyPage } from "@/components/PolicyPage";
import type { PolicyDoc } from "@/lib/policy/parse";

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
    const { container } = render(<PolicyPage title="이용약관" doc={null} />);
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

// 파서를 거치지 않고 직접 만든 픽스처 — 실제 약관 · 규칙과 무관하다.
const t = (text: string) => ({ text, bold: false });
const DOC: PolicyDoc = {
  effectiveDate: "시행일: 2000년 1월 1일",
  intro: [{ kind: "paragraph", inlines: [t("앞말 <b>태그</b> 아님")] }],
  sections: [
    {
      id: "article-1",
      title: [t("첫째 조")],
      blocks: [
        {
          kind: "paragraph",
          inlines: [t("본문 "), { text: "굵게", bold: true }],
        },
        {
          kind: "table",
          header: [[t("칸A")], [t("칸B")]],
          rows: [[[t("a")], [t("b")]]],
        },
      ],
    },
    { id: "article-2", title: [t("둘째 조")], blocks: [] },
  ],
};

describe("PolicyPage 원고 표시", () => {
  it("z5. 시행일 · 목차 링크 · h2 id 가 맞는다", () => {
    const { container } = render(<PolicyPage title="이용약관" doc={DOC} />);
    expect(
      container.querySelector('[data-slot="policy-effective-date"]'),
    ).toHaveTextContent("시행일: 2000년 1월 1일");
    const links = screen.getAllByRole("link", { name: /조$/ });
    const toc = within(
      screen.getByRole("navigation", { name: "목차" }),
    ).getAllByRole("link");
    expect(toc.map((a) => a.getAttribute("href"))).toEqual([
      "#article-1",
      "#article-2",
    ]);
    expect(links.length).toBeGreaterThanOrEqual(2);
    const h2s = screen.getAllByRole("heading", { level: 2 });
    expect(h2s.map((h) => h.id)).toEqual(["article-1", "article-2"]);
  });

  it("z6. 표 머리칸 · 굵게", () => {
    const { container } = render(<PolicyPage title="이용약관" doc={DOC} />);
    expect(
      screen.getAllByRole("columnheader").map((c) => c.textContent),
    ).toEqual(["칸A", "칸B"]);
    expect(container.querySelector("strong")).toHaveTextContent("굵게");
  });

  it("z7. 텍스트의 < 는 태그가 아니라 글자로 나온다", () => {
    render(<PolicyPage title="이용약관" doc={DOC} />);
    const body = screen.getByRole("article");
    expect(body.querySelector("b")).toBeNull();
    expect(body.textContent).toContain("앞말 <b>태그</b> 아님");
  });
});
