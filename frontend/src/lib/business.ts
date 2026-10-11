// 사업자 정보는 이 파일 하나에만 둔다 (frontend/CLAUDE.md "구현 원칙", PG 심사 노출 요건 PG-4).
// 카드사 심사는 사업자등록증과 글자까지 같은 값을 요구한다. 값은 사용자가 준 것만 넣고, 지어내거나 기본값을 넣지 않는다.
// 상호 · 대표자 · 사업자등록번호 · 주소 = 사업자등록증 그대로 (R-08 해결 · R-07, 2026-10-10)
// TODO(R-07): 유선번호 · 전자우편(O-05) · 통신판매업 신고번호(또는 신고 면제 표시 — 문구 확정 전) · 호스팅서비스 제공자 상호는 미정
// 전자우편주소 · 호스팅 제공자: 전자상거래법 제10조 · 시행령 사이버몰 표시사항 (토스 심사 6개 항목 외 법정 항목)
export type BusinessField =
  | "companyName"
  | "representative"
  | "registrationNumber"
  | "address"
  | "phone"
  | "email"
  | "mailOrderNumber"
  | "hostingProvider";

export type BusinessInfo = Record<BusinessField, string | null>;

export const BUSINESS_INFO: BusinessInfo = {
  companyName: "마다",
  representative: "김윤진",
  registrationNumber: "792-74-00624",
  address: "서울특별시 강남구 논현로38길 30, 3층 a105호(도곡동)",
  phone: null,
  email: null,
  mailOrderNumber: null,
  hostingProvider: null,
};
