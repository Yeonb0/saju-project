// 세션 포트 (FE 모델, MOCK-PORT). 근거: API_SPEC 3 · 4장 초안
// (GET /session · GET /me 의 hasPrimaryPerson · POST /auth/logout), A-01 · A-03.
//
// - 이 모델은 FE 모델이고 백엔드 타입이 아니다. 진짜 구현은 OpenAPI 수령 후
//   adapters 에서 GET /session · GET /me 를 합쳐 만든다 (초안).
// - startLogin 은 카카오만 (A-01 — 네이버 · 구글은 BE-B 지원 · 개발 범위 확정 전).
//   진짜 구현은 navigate 를 쓰지 않고 /api/v1/auth/kakao/authorize?returnTo= 로
//   전체 페이지 이동한다 (초안). returnTo 는 safeReturnTo 를 거친 값만.
// - logout 의 구매 선택 sessionStorage 삭제는 TODO(PURCHASE-RESTORE) — 저장소가 생길 때 붙인다.
// - CSRF 토큰은 포트에 두지 않는다. 진짜 구현이 src/lib/api/http.ts 의 CsrfSource 로 연결한다 (Q-02).
// - 실패는 src/lib/api/errors.ts 의 오류 클래스로 던진다.

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
  startLogin(returnTo: string, navigate: (url: string) => void): Promise<void>;
  logout(): Promise<void>;
};
