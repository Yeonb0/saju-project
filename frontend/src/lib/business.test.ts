// @vitest-environment jsdom
import { cleanup, render, screen } from "@testing-library/react";
import { createElement } from "react";
import { afterEach, describe, expect, it } from "vitest";
import { BusinessFooter } from "@/components/BusinessFooter";
import { BUSINESS_INFO } from "./business";

// vitest 는 globals 를 켜지 않아 Testing Library 자동 정리가 동작하지 않는다.
afterEach(cleanup);

describe("BUSINESS_INFO (사업자등록증 그대로)", () => {
  it("상호", () => {
    expect(BUSINESS_INFO.companyName).toBe("마다");
  });

  it("대표자", () => {
    expect(BUSINESS_INFO.representative).toBe("김윤진");
  });

  it("사업자등록번호", () => {
    expect(BUSINESS_INFO.registrationNumber).toBe("792-74-00624");
  });

  it("주소", () => {
    expect(BUSINESS_INFO.address).toBe(
      "서울특별시 강남구 논현로38길 30, 3층 a105호(도곡동)",
    );
  });

  it("유선번호는 미정", () => {
    expect(BUSINESS_INFO.phone).toBeNull();
  });

  it("전자우편주소는 미정", () => {
    expect(BUSINESS_INFO.email).toBeNull();
  });

  it("통신판매업 신고번호는 미정", () => {
    expect(BUSINESS_INFO.mailOrderNumber).toBeNull();
  });

  it("호스팅 제공자는 미정", () => {
    expect(BUSINESS_INFO.hostingProvider).toBeNull();
  });
});

describe("BusinessFooter 기본값", () => {
  it("info 없이 렌더하면 BUSINESS_INFO 값이 보이고 미정은 4개다", () => {
    render(createElement(BusinessFooter));
    expect(screen.getByText("마다")).toBeTruthy();
    expect(screen.getByText("김윤진")).toBeTruthy();
    expect(screen.getByText("792-74-00624")).toBeTruthy();
    expect(
      screen.getByText("서울특별시 강남구 논현로38길 30, 3층 a105호(도곡동)"),
    ).toBeTruthy();
    expect(screen.getAllByText("(미정)")).toHaveLength(4);
  });
});
