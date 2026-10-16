// 사업자 정보는 이 파일 하나에만 둔다 (frontend/CLAUDE.md "구현 원칙", PG 심사 노출 요건 PG-4).
// 카드사 심사는 사업자등록증과 글자까지 같은 값을 요구한다. 값은 사용자가 준 것만 넣고, 지어내거나 기본값을 넣지 않는다.
// 상호 · 대표자 · 사업자등록번호 · 주소 = 사업자등록증 그대로 (R-08 해결 · R-07, 2026-10-10)
// 전자우편주소 = PD 확정값 (2026-10-13 PD 답변 · 정책 확정본, R-07). 「문의하기」 메일 링크는 src/lib/support/contact.ts 가 이 값으로 만든다 (기능 명세서 v0.4 13장 · Q-47)
// 유선번호 = 고객센터 전화번호 (2026-10-15 사용자 전달, R-07)
// 통신판매업 = 신고하지 않고 면제 표기 (2026-10-16 사용자 결정, R-07). 문구는 PD 정책 v4 사업자 표기 그대로
// TODO(Q-44): 호스팅서비스 제공자 상호 — AWS Amplify 로 옮긴 뒤 법적 상호를 받아 넣는다 (2026-10-16 사용자: 백 + 프론트 이전 예정)
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
  phone: "010-7429-0716",
  email: "ppugisaju@gmail.com",
  mailOrderNumber: "면제 (직전연도 통신판매 거래 50회 미만)",
  hostingProvider: null,
};
