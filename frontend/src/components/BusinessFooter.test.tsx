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
  mailOrderNumber: "테스트신고번호",
};

const LABELS = [
  "상호",
  "대표자",
  "사업자등록번호",
  "사업장 주소",
  "유선번호",
  "통신판매업 신고번호",
] as const;

describe("BusinessFooter", () => {
  it("항목 이름 6개와 값 6개가 모두 보인다", () => {
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
});
