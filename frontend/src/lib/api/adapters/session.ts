// 세션 adapter. 근거: API_SPEC 3장, OpenAPI SessionResponse, ADAPTER-HTTP, Q-02, A-01, A-03.
// API_SPEC 3장의 GET /session 예시(user 중첩)는 실제 코드 · OpenAPI 와 다르다 — OpenAPI 를 따른다.
import { z } from "zod";
import { ApiContractError } from "@/lib/api/errors";
import { type CsrfSource, createApiClient } from "@/lib/api/http";
import { safeReturnTo } from "@/lib/auth/returnTo";
import type { SessionPort, SessionState } from "@/lib/ports/session";
import type { components } from "@/types/api";

// expiresAt 은 쓰지 않는다
const sessionSchema = z.object({
  authenticated: z.boolean(),
  userId: z.uuid().nullable(),
  nickname: z.string().nullable(),
  hasPrimaryPerson: z.boolean().nullable(),
  csrfToken: z.string().min(1).nullable(),
});

type SessionWire = {
  [K in keyof Omit<
    components["schemas"]["SessionResponse"],
    "expiresAt"
  >]-?: NonNullable<components["schemas"]["SessionResponse"][K]> | null;
};

export function createRealSession(
  deps: { fetch?: typeof fetch; assign?: (url: string) => void } = {},
): { port: SessionPort; csrf: CsrfSource } {
  let csrfToken: string | null = null;

  const csrf: CsrfSource = {
    async current() {
      return csrfToken;
    },
    async refresh() {
      await getSession();
      return csrfToken;
    },
  };
  const client = createApiClient({ csrf, fetch: deps.fetch });
  const assign = deps.assign ?? ((url: string) => window.location.assign(url));

  async function getSession(): Promise<SessionState> {
    const { status, data } = await client.request({
      method: "GET",
      path: "/api/v1/session",
    });
    const parsed = sessionSchema.safeParse(data);
    if (!parsed.success) {
      throw new ApiContractError(status, "세션 데이터 모양이 다르다");
    }
    // 생성 타입은 null 을 모르므로 null 허용을 덧붙인 모양과 이름을 대조한다
    const wire = {
      authenticated: parsed.data.authenticated,
      userId: parsed.data.userId,
      nickname: parsed.data.nickname,
      hasPrimaryPerson: parsed.data.hasPrimaryPerson,
      csrfToken: parsed.data.csrfToken,
    } satisfies SessionWire;
    if (!wire.authenticated) {
      csrfToken = null;
      return { status: "signed_out" };
    }
    if (
      wire.userId === null ||
      wire.hasPrimaryPerson === null ||
      wire.csrfToken === null
    ) {
      throw new ApiContractError(status, "로그인 세션 데이터가 불완전하다");
    }
    csrfToken = wire.csrfToken;
    return {
      status: "signed_in",
      userId: wire.userId,
      nickname: wire.nickname,
      hasPrimaryPerson: wire.hasPrimaryPerson,
    };
  }

  const port: SessionPort = {
    getSession,
    async startLogin(provider, returnTo) {
      // TODO(A-01 · BE-B): 네이버 · 구글 경로가 나오면 추가한다
      if (provider !== "KAKAO")
        throw new Error(`${provider} 로그인은 아직 지원하지 않는다`);
      // Router navigation이 아닌 문서 이동이어야 카카오와 백엔드 callback의 쿠키/redirect 흐름을 보존한다.
      assign(
        `/api/v1/auth/kakao/authorize?returnTo=${encodeURIComponent(safeReturnTo(returnTo))}`,
      );
    },
    async logout() {
      if (csrfToken === null) await getSession();
      await client.request({ method: "POST", path: "/api/v1/auth/logout" });
      csrfToken = null;
    },
  };

  return { port, csrf };
}
