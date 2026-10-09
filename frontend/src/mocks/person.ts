// 가짜 인물 (개발 서버 · 미리보기 전용, MOCK-PORT). 가짜 세션과 같은 가짜 계정을 본다.
import { ApiError } from "@/lib/api/errors";
import type { PersonPort, PersonSummary } from "@/lib/ports/person";
import type { FakeAccount } from "./account";

const TRACE = "fixture-trace";
// API_SPEC 4장 초안: 타인 최대 10명
const MAX_OTHERS = 10;

export function createFakePersonPort(account: FakeAccount): PersonPort {
  const fail = (status: number, code: string) =>
    new ApiError({ status, code, traceId: TRACE });

  function requireSignedIn() {
    if (!account.isSignedIn()) throw fail(401, "AUTHENTICATION_REQUIRED");
  }

  return {
    async list() {
      requireSignedIn();
      const self = account.getSelf();
      return [...(self ? [self] : []), ...account.getOthers()];
    },
    async createSelf(input) {
      requireSignedIn();
      if (input.relation !== null) {
        throw new Error("createSelf 는 relation 이 없는 입력만 받는다");
      }
      // 이미 본인이 있을 때의 서버 규칙은 미정이라 새 값으로 바꾸는 것은 가짜의 임시 동작이다 — TODO(BE-B)
      const person: PersonSummary = {
        personId: crypto.randomUUID(),
        isSelf: true,
        name: input.name,
      };
      account.setSelf(person);
      return { ...person };
    },
    async createOther(input) {
      requireSignedIn();
      if (input.relation === null) {
        throw new Error("createOther 는 relation 이 있는 입력만 받는다");
      }
      if (account.getOthers().length >= MAX_OTHERS) {
        throw fail(409, "PERSON_LIMIT_EXCEEDED");
      }
      const person: PersonSummary = {
        personId: crypto.randomUUID(),
        isSelf: false,
        name: input.name,
      };
      account.addOther(person);
      return { ...person };
    },
  };
}
