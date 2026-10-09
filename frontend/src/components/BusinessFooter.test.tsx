import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import type { BusinessInfo } from "@/lib/business";
import { BusinessFooter } from "./BusinessFooter";

// vitest 는 globals 를 켜지 않아 Testing Library 자동 정리가 동작하지 않는다.
afterEach(cleanup);

// 픽스처일 뿐이며 실제 사업자 정보와 무관하다
const INFO: BusinessInfo = {
  companyName: "테스트상호",
  representative: "테스트대표",
  registrationNumber: "000-00-00000",
  address: "테스트주소",
  phone: "00-0000-0000",
  email: "test@example.com",
  mailOrderNumber: "테스트신고번호",
  hostingProvider: "테스트호스팅",
};

const LABELS = [
  "상호",
  "대표자",
  "사업자등록번호",
  "사업장 주소",
  "유선번호",
  "전자우편주소",
  "통신판매업 신고번호",
  "호스팅 제공자",
] as const;

describe("BusinessFooter", () => {
  it("항목 이름 8개와 값 8개가 모두 보인다", () => {
    render(<BusinessFooter info={INFO} />);
    for (const label of LABELS) {
      expect(screen.getByText(label)).toBeInTheDocument();
    }
    for (const value of Object.values(INFO)) {
      expect(screen.getByText(value as string)).toBeInTheDocument();
    }
  });

  it('값이 null 인 항목은 "(미정)" 이 보인다', () => {
    render(<BusinessFooter info={{ ...INFO, phone: null }} />);
    expect(screen.getAllByText("(미정)")).toHaveLength(1);
  });

  it("각 항목은 dt · dd 를 그대로 가진 채 한 줄(flex) 컨테이너 안에 있다 (LAYOUT-FIGMA)", () => {
    render(<BusinessFooter info={INFO} />);
    for (const label of LABELS) {
      const dt = screen.getByText(label);
      expect(dt.tagName).toBe("DT");
      const row = dt.parentElement;
      expect(row).toHaveClass("flex");
      expect(row?.querySelectorAll("dt")).toHaveLength(1);
      expect(row?.querySelectorAll("dd")).toHaveLength(1);
    }
  });

  it("약관 링크 3개가 각자의 href 를 가진다", () => {
    render(<BusinessFooter info={INFO} />);
    expect(screen.getByRole("link", { name: "이용약관" })).toHaveAttribute(
      "href",
      "/terms",
    );
    expect(
      screen.getByRole("link", { name: "개인정보처리방침" }),
    ).toHaveAttribute("href", "/privacy");
    expect(screen.getByRole("link", { name: "환불정책" })).toHaveAttribute(
      "href",
      "/refund",
    );
  });

  it("u. 항목 이름(dt)이 와이어 순서대로 나온다", () => {
    const { container } = render(<BusinessFooter info={INFO} />);
    expect(
      Array.from(container.querySelectorAll("dt")).map((d) => d.textContent),
    ).toEqual([
      "상호",
      "대표자",
      "사업자등록번호",
      "통신판매업 신고번호",
      "사업장 주소",
      "유선번호",
      "전자우편주소",
      "호스팅 제공자",
    ]);
  });

  it("v. 상호 · 대표자와 유선번호 · 전자우편주소는 각각 한 줄에 있고 사업자등록번호는 다른 줄이다", () => {
    render(<BusinessFooter info={INFO} />);
    const rowOf = (label: string) =>
      screen.getByText(label).parentElement?.parentElement;
    expect(rowOf("상호")).toBe(rowOf("대표자"));
    expect(rowOf("유선번호")).toBe(rowOf("전자우편주소"));
    expect(rowOf("사업자등록번호")).not.toBe(rowOf("상호"));
    expect(rowOf("사업자등록번호")).not.toBe(rowOf("유선번호"));
  });

  it("w. 개인정보처리방침만 font-bold 다 (PD 메모 302:179)", () => {
    render(<BusinessFooter info={INFO} />);
    expect(screen.getByRole("link", { name: "개인정보처리방침" })).toHaveClass(
      "font-bold",
    );
    expect(screen.getByRole("link", { name: "이용약관" })).not.toHaveClass(
      "font-bold",
    );
    expect(screen.getByRole("link", { name: "환불정책" })).not.toHaveClass(
      "font-bold",
    );
  });

  it("x. 링크 줄이 사업자 정보보다 앞에 있고 구분자는 aria-hidden 이다", () => {
    const { container } = render(<BusinessFooter info={INFO} />);
    const nav = screen.getByRole("navigation", { name: "약관" });
    const dl = container.querySelector("dl");
    expect(dl).not.toBeNull();
    expect(
      nav.compareDocumentPosition(dl as Node) &
        Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
    const bars = Array.from(nav.querySelectorAll("span")).filter(
      (el) => el.textContent === "|",
    );
    expect(bars).toHaveLength(2);
    for (const bar of bars) expect(bar).toHaveAttribute("aria-hidden", "true");
    expect(screen.getAllByRole("link").map((l) => l.textContent)).toEqual([
      "이용약관",
      "개인정보처리방침",
      "환불정책",
    ]);
  });

  it("y. 저작권 줄이 사업자 정보 뒤에 있다", () => {
    const { container } = render(<BusinessFooter info={INFO} />);
    const dl = container.querySelector("dl");
    const copyright = screen.getByText("© 뿌기사주");
    expect(dl).not.toBeNull();
    expect(
      (dl as Node).compareDocumentPosition(copyright) &
        Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });
});
