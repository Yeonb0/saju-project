// 인물 포트 (FE 모델, MOCK-PORT). 근거: FUNCTIONAL_SPEC 4장, API_SPEC 4장 초안
// (POST /people — 인증 · CSRF, 타인 최대 10명 → 409 PERSON_LIMIT_EXCEEDED), A-03 · A-07 · O-03.
//
// - FE 모델이다. 서버 요청 모양(PersonInput · relation · thirdPartyAuthorizationConfirmed — 초안)으로
//   바꾸는 일은 진짜 adapter 가 한다.
// - 입력은 폼 검증 결과(PersonFormOutput)다. createSelf 는 relation 이 null 인 입력,
//   createOther 는 relation 이 있는 입력만 받고, 맞지 않으면 구현이 던진다 (폼 검증을 거치지 않은 호출을 막기 위해).
// - 타인 권한 확인(O-03)은 폼 검증이 이미 확인했다.
// - Idempotency-Key 는 API_SPEC 초안에 없어 쓰지 않는다 — 중복 제출은 화면의 ref 가드로 막는다.
// - 본인을 두 번 저장할 때의 서버 규칙은 API_SPEC 에 없다 — TODO(BE-B).
// - 실패는 src/lib/api/errors.ts 의 오류 클래스로 던진다.
import type { PersonFormOutput } from "@/lib/person/schema";

export type PersonSummary = {
  personId: string;
  isSelf: boolean;
  name: string;
};

export type PersonPort = {
  createSelf(input: PersonFormOutput): Promise<PersonSummary>;
  createOther(input: PersonFormOutput): Promise<PersonSummary>;
};
