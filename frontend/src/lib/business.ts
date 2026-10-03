// 사업자 정보는 이 파일 하나에만 둔다 (frontend/CLAUDE.md "구현 원칙", PG 심사 노출 요건 PG-4).
// 카드사 심사는 사업자등록증과 글자까지 같은 값을 요구한다. 값은 사용자가 준 것만 넣고, 지어내거나 기본값을 넣지 않는다.
// TODO(O-01): 8개 값 모두 미정. email 은 CS 이메일(O-05), hostingProvider 는 사이트 호스팅 제공자 상호
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
  companyName: null,
  representative: null,
  registrationNumber: null,
  address: null,
  phone: null,
  email: null,
  mailOrderNumber: null,
  hostingProvider: null,
};
