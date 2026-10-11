// 오행분석 포트 (FE 모델, MOCK-PORT). 근거: F-09 (결제 전 노출), API_SPEC 8장 POST /fortune/basic 초안 (main 08fccad — 요청은 생년정보 원문,
// 응답은 원국 · 오행 분포(counts · dominant · missing) · 십성 · 12운성 · 합충형파해 · calculationVersion, 시간 미상이면 시주 기반 값 null).
// 포트 입력은 personId 다 — 진짜 adapter 가 Q-35 답에 따라 personId 요청 또는 인물 생년정보를 받아 요청으로 바꾼다. 화면이 쓰는 값만 둔다(원국 · 십성 등은 화면이 정해지면 추가).
// 절기 경계일에 시간이 미상이면 422 BIRTH_TIME_REQUIRED_AT_TERM. 실패는 src/lib/api/errors.ts 의 오류 클래스로 던진다.

export const ELEMENTS = ["WOOD", "FIRE", "EARTH", "METAL", "WATER"] as const;
export type FiveElement = (typeof ELEMENTS)[number];

export type BasicSaju = Readonly<{
  // 항상 ELEMENTS 순서 5개 — adapter 가 서버 counts 를 이 순서로 바꾸고, 모르는 원소 · 빠진 원소는 ApiContractError 로 던진다 (TODO(OpenAPI)).
  fiveElements: readonly Readonly<{ element: FiveElement; count: number }>[];
  birthTimeKnown: boolean;
  calculationVersion: string;
}>;

export type BasicSajuPort = {
  getBasicSaju(personId: string): Promise<BasicSaju>;
};
