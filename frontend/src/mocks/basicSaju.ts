// 가짜 오행분석 (개발 서버 · 미리보기 전용, MOCK-PORT). 가짜 세션 · 인물과 같은 가짜 계정을 본다.
import { ApiError } from "@/lib/api/errors";
import type { BasicSaju, BasicSajuPort } from "@/lib/ports/basicSaju";
import type { FakeAccount } from "./account";

const TRACE = "fixture-trace";

// 픽스처일 뿐이며 실제 계산 · 규칙과 무관하다 (ELEMENTS 순서: 목 · 화 · 토 · 금 · 수)
const FIXTURE_COUNTS = [2, 1, 3, 0, 2] as const;
const FIXTURE_ELEMENTS = ["WOOD", "FIRE", "EARTH", "METAL", "WATER"] as const;

export function createFakeBasicSajuPort(
  account: FakeAccount,
  // 테스트에서만 쓴다 — 환경 변수 · 조작판 시나리오는 만들지 않는다
  options: { failure?: "birth_time_required" } = {},
): BasicSajuPort {
  const fail = (status: number, code: string) =>
    new ApiError({ status, code, traceId: TRACE });

  return {
    async getBasicSaju(personId): Promise<BasicSaju> {
      if (!account.isSignedIn()) throw fail(401, "AUTHENTICATION_REQUIRED");
      const known =
        account.getSelf()?.personId === personId ||
        account.getOthers().some((person) => person.personId === personId);
      if (!known) throw fail(404, "PERSON_NOT_FOUND");
      if (options.failure === "birth_time_required") {
        throw fail(422, "BIRTH_TIME_REQUIRED_AT_TERM");
      }
      // 매번 새 배열 — 화면이 바꿔도 픽스처가 오염되지 않게
      return {
        fiveElements: FIXTURE_ELEMENTS.map((element, i) => ({
          element,
          count: FIXTURE_COUNTS[i],
        })),
        birthTimeKnown: true,
        calculationVersion: "fixture-calc",
      };
    },
  };
}
