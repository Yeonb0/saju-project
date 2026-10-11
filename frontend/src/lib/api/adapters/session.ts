import { z } from "zod";
import { errorEnvelopeSchema, successEnvelopeSchema } from "@/lib/api/envelope";
import { ApiContractError, ApiError, ApiNetworkError } from "@/lib/api/errors";
import type { CsrfSource } from "@/lib/api/http";
import { safeReturnTo } from "@/lib/auth/returnTo";
import type { SessionPort, SessionState } from "@/lib/ports/session";

const sessionSchema = z.object({
  authenticated: z.boolean(),
  userId: z.uuid().nullable(),
  nickname: z.string().nullable(),
  hasPrimaryPerson: z.boolean().nullable(),
  csrfToken: z.string().min(1).nullable(),
});

let csrfToken: string | null = null;

async function refreshSession(): Promise<SessionState> {
  let response: Response;
  try {
    response = await fetch("/api/v1/session", { credentials: "same-origin" });
  } catch (cause) {
    throw new ApiNetworkError({ cause });
  }
  if (!response.ok) throw await error(response);
  const envelope = successEnvelopeSchema.safeParse(await json(response));
  if (!envelope.success)
    throw new ApiContractError(response.status, "세션 응답 모양이 다르다");
  const parsed = sessionSchema.safeParse(envelope.data.data);
  if (!parsed.success)
    throw new ApiContractError(response.status, "세션 데이터 모양이 다르다");
  csrfToken = parsed.data.csrfToken;
  if (!parsed.data.authenticated) return { status: "signed_out" };
  if (!parsed.data.userId || parsed.data.hasPrimaryPerson === null) {
    throw new ApiContractError(
      response.status,
      "로그인 세션 데이터가 불완전하다",
    );
  }
  return {
    status: "signed_in",
    userId: parsed.data.userId,
    nickname: parsed.data.nickname,
    hasPrimaryPerson: parsed.data.hasPrimaryPerson,
  };
}

export const sessionCsrfSource: CsrfSource = {
  async current() {
    return csrfToken;
  },
  async refresh() {
    await refreshSession();
    return csrfToken;
  },
};

// 로그인 세션 전용 CSRF 출처. 업무 adapter는 이 객체를 createApiClient에 전달한다.
export function createSessionAdapter(): SessionPort {
  return {
    getSession: refreshSession,
    async startLogin(provider, returnTo) {
      if (provider !== "KAKAO")
        throw new Error(`${provider} 로그인은 아직 지원하지 않는다`);
      // Router navigation이 아닌 문서 이동이어야 카카오와 백엔드 callback의 쿠키/redirect 흐름을 보존한다.
      window.location.assign(
        `/api/v1/auth/kakao/authorize?returnTo=${encodeURIComponent(safeReturnTo(returnTo))}`,
      );
    },
    async logout() {
      if (csrfToken === null) await refreshSession();
      const send = () =>
        fetch("/api/v1/auth/logout", {
          method: "POST",
          credentials: "same-origin",
          headers: csrfToken ? { "X-CSRF-Token": csrfToken } : {},
        });
      let response = await send();
      if (response.status === 403) {
        await refreshSession();
        response = await send();
      }
      if (!response.ok) throw await error(response);
      csrfToken = null;
    },
  };
}

async function json(response: Response): Promise<unknown> {
  try {
    return await response.json();
  } catch {
    throw new ApiContractError(response.status, "JSON 응답이 아니다");
  }
}
async function error(response: Response): Promise<ApiError> {
  const parsed = errorEnvelopeSchema.safeParse(await json(response));
  if (!parsed.success)
    throw new ApiContractError(response.status, "오류 응답 모양이 다르다");
  return new ApiError({
    status: response.status,
    code: parsed.data.code,
    traceId: parsed.data.traceId,
    fieldErrors: parsed.data.fieldErrors,
    details: parsed.data.details,
  });
}
