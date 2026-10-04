// 가짜 세션 (개발 서버 · 미리보기 전용, MOCK-PORT).
// 상태는 메모리에만 둔다 — 같은 탭 안에서는 유지되고, 새로고침하면 시나리오 처음 상태로 돌아간다.
import { safeReturnTo } from "@/lib/auth/returnTo";
import type { SessionPort, SessionState } from "@/lib/ports/session";

export const FAKE_SESSION_SCENARIOS = [
  "signed_out", // 처음 로그아웃, 로그인하면 본인 정보 있음
  "new_user", // 처음 로그아웃, 로그인하면 본인 정보 없음 (A-03 온보딩 경로 확인용)
  "signed_in", // 처음부터 로그인 · 본인 정보 있음
  "signed_in_without_person", // 처음부터 로그인 · 본인 정보 없음
] as const;

export type FakeSessionScenario = (typeof FAKE_SESSION_SCENARIOS)[number];

// 픽스처일 뿐이며 실제 사용자와 무관하다
const FIXTURE_USER_ID = "44444444-4444-4444-8444-444444444444";
const FIXTURE_NICKNAME = "FIXTURE";

export function createFakeSessionPort(
  options: { scenario?: FakeSessionScenario } = {},
): SessionPort {
  const scenario = options.scenario ?? "signed_out";
  const hasPrimaryPerson =
    scenario === "signed_out" || scenario === "signed_in";

  const signedIn = (): SessionState => ({
    status: "signed_in",
    userId: FIXTURE_USER_ID,
    nickname: FIXTURE_NICKNAME,
    hasPrimaryPerson,
  });

  let state: SessionState =
    scenario === "signed_in" || scenario === "signed_in_without_person"
      ? signedIn()
      : { status: "signed_out" };

  return {
    async getSession() {
      return { ...state };
    },
    async startLogin(returnTo, navigate) {
      state = signedIn();
      // 진짜 서버도 내부 경로만 허용하므로 가짜도 같은 검사를 거친다
      navigate(safeReturnTo(returnTo));
    },
    async logout() {
      state = { status: "signed_out" };
    },
  };
}
