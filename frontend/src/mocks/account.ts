// 가짜 계정 (개발 서버 · 미리보기 전용, MOCK-PORT). 가짜 세션과 가짜 인물이 같이 본다.
// 상태는 같은 탭 메모리에만 둔다 — 새로고침하면 시나리오 처음 상태로 돌아간다.
import type { PersonSummary } from "@/lib/ports/person";

export const FAKE_SESSION_SCENARIOS = [
  "signed_out", // 처음 로그아웃, 로그인하면 본인 정보 있음
  "new_user", // 처음 로그아웃, 로그인하면 본인 정보 없음 (A-03 온보딩 경로 확인용)
  "signed_in", // 처음부터 로그인 · 본인 정보 있음
  "signed_in_without_person", // 처음부터 로그인 · 본인 정보 없음
] as const;

export type FakeSessionScenario = (typeof FAKE_SESSION_SCENARIOS)[number];

export type FakeAccount = {
  isSignedIn(): boolean;
  signIn(): void;
  signOut(): void;
  getSelf(): PersonSummary | null;
  setSelf(person: PersonSummary): void;
  getOthers(): readonly PersonSummary[];
  addOther(person: PersonSummary): void;
};

// 픽스처일 뿐이며 실제 사용자와 무관하다
const FIXTURE_SELF: PersonSummary = {
  personId: "55555555-5555-4555-8555-555555555555",
  isSelf: true,
  name: "FIXTURE",
};

export function createFakeAccount(scenario: FakeSessionScenario): FakeAccount {
  let signedIn =
    scenario === "signed_in" || scenario === "signed_in_without_person";
  let self: PersonSummary | null =
    scenario === "signed_out" || scenario === "signed_in"
      ? { ...FIXTURE_SELF }
      : null;
  const others: PersonSummary[] = [];

  return {
    isSignedIn: () => signedIn,
    signIn() {
      signedIn = true;
    },
    signOut() {
      signedIn = false;
    },
    getSelf: () => (self === null ? null : { ...self }),
    setSelf(person) {
      self = { ...person };
    },
    getOthers: () => others.map((person) => ({ ...person })),
    addOther(person) {
      others.push({ ...person });
    },
  };
}
