// 가짜 세션 (개발 서버 · 미리보기 전용, MOCK-PORT).
// 상태는 메모리에만 둔다 — 같은 탭 안에서는 유지되고, 새로고침하면 시나리오 처음 상태로 돌아간다.
import { safeReturnTo } from "@/lib/auth/returnTo";
import type { SessionPort, SessionState } from "@/lib/ports/session";
import {
  createFakeAccount,
  type FakeAccount,
  type FakeSessionScenario,
} from "./account";

export { FAKE_SESSION_SCENARIOS, type FakeSessionScenario } from "./account";

// 픽스처일 뿐이며 실제 사용자와 무관하다
const FIXTURE_USER_ID = "44444444-4444-4444-8444-444444444444";
const FIXTURE_NICKNAME = "FIXTURE";

export function createFakeSessionPort(
  options: { scenario?: FakeSessionScenario; account?: FakeAccount } = {},
): SessionPort {
  const account =
    options.account ?? createFakeAccount(options.scenario ?? "signed_out");

  const current = (): SessionState =>
    account.isSignedIn()
      ? {
          status: "signed_in",
          userId: FIXTURE_USER_ID,
          nickname: FIXTURE_NICKNAME,
          hasPrimaryPerson: account.getSelf() !== null,
        }
      : { status: "signed_out" };

  return {
    async getSession() {
      return current();
    },
    async startLogin(returnTo, navigate) {
      account.signIn();
      // 진짜 서버도 내부 경로만 허용하므로 가짜도 같은 검사를 거친다
      navigate(safeReturnTo(returnTo));
    },
    async logout() {
      account.signOut();
    },
  };
}
