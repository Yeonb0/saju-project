// 세션 포트 (FE 모델, MOCK-PORT). 근거: API_SPEC 3 · 4장 초안
// (GET /session · GET /me 의 hasPrimaryPerson · POST /auth/logout), A-01 · A-03.
//
// - 이 모델은 FE 모델이고 백엔드 타입이 아니다. 진짜 구현은 OpenAPI 수령 후
//   adapters 에서 GET /session · GET /me 를 합쳐 만든다 (초안).
// - startLogin 은 제공자를 받는다 (A-01 소셜 로그인 3종, 개발 범위 LOGIN-3). API_SPEC 3장 초안에는 카카오 경로(/auth/kakao/authorize?returnTo=)만 있다.
//   진짜 구현은 네이버 · 구글 경로를 BE-B 가 줄 때까지 지어내지 않고 던진다 — TODO(A-01 · BE-B).
//   진짜 구현은 navigate 를 쓰지 않고 /api/v1/auth/kakao/authorize?returnTo= 로
//   전체 페이지 이동한다 (초안). returnTo 는 safeReturnTo 를 거친 값만.
// - 로그아웃은 화면이 이 포트를 직접 부르지 않고 src/lib/auth/logout.ts 를 거친다 — 구매 선택 sessionStorage 삭제(PURCHASE-RESTORE)를
//   가짜 · 진짜 구현마다 따로 넣지 않게.
// - CSRF 토큰은 포트에 두지 않는다. 진짜 구현이 src/lib/api/http.ts 의 CsrfSource 로 연결한다 (Q-02).
// - 실패는 src/lib/api/errors.ts 의 오류 클래스로 던진다.

export const LOGIN_PROVIDERS = ["KAKAO", "NAVER", "GOOGLE"] as const;
export type LoginProvider = (typeof LOGIN_PROVIDERS)[number];

export type SessionState =
  | { status: "signed_out" }
  | {
      status: "signed_in";
      userId: string;
      nickname: string | null;
      hasPrimaryPerson: boolean;
    };

export type SessionPort = {
  getSession(): Promise<SessionState>;
  startLogin(
    provider: LoginProvider,
    returnTo: string,
    navigate: (url: string) => void,
  ): Promise<void>;
  logout(): Promise<void>;
};
