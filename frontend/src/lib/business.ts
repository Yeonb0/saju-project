// 사업자 정보는 이 파일 하나에만 둔다 (frontend/CLAUDE.md "구현 원칙", PG 심사 노출 요건 PG-4).
// 카드사 심사는 사업자등록증과 글자까지 같은 값을 요구한다. 값은 사용자가 준 것만 넣고, 지어내거나 기본값을 넣지 않는다.
// TODO(O-01): 6개 값 모두 미정
export type BusinessField =
  | "companyName"
  | "representative"
  | "registrationNumber"
  | "address"
  | "phone"
  | "mailOrderNumber";

export type BusinessInfo = Record<BusinessField, string | null>;

export const BUSINESS_INFO: BusinessInfo = {
  companyName: null,
  representative: null,
  registrationNumber: null,
  address: null,
  phone: null,
  mailOrderNumber: null,
};
