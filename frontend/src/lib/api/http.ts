// 같은 출처 /api/v1 요청의 공통 처리: 공통 응답 · 오류 껍데기, CSRF 1회 재시도, Idempotency-Key.
// 근거: frontend/CLAUDE.md "API 호출" · "멱등성" · "CSRF", docs/FRONTEND.md 1-1 · 1-2 (MOCK-PORT),
// COMMON 7장, I-05, Q-02.
// 이 파일은 업무 API 를 모른다. 업무 요청 · 응답은 adapters 가 OpenAPI 생성 타입으로 만든다 (OpenAPI 수령 후).
import { errorEnvelopeSchema, successEnvelopeSchema } from "./envelope";
import { ApiContractError, ApiError, ApiNetworkError } from "./errors";
import type { IdempotentCommand } from "./idempotency";

export type HttpMethod = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";

// CSRF 토큰 출처. GET /session 응답의 토큰 필드 이름은 OpenAPI 생성본으로 확인한 뒤
// 세션 adapter 가 이 모양으로 넘긴다 (Q-02). 이 파일은 세션 응답 모양을 모른다.
export type CsrfSource = {
  // 지금 쓸 토큰 (없으면 null)
  current(): Promise<string | null>;
  // GET /session 을 다시 불러 새 토큰을 받는다. 403 CSRF_FAILED 때 한 번만 부른다.
  refresh(): Promise<string | null>;
};

export type ApiRequest = {
  method: HttpMethod;
  // 같은 출처 경로만 (next.config.ts rewrites, I-02). 쿼리는 호출하는 쪽이 붙인다.
  path: string;
  // 멱등 명령 (구매 · 충전 주문 · 승인 등). 키와 본문은 만들 때 고정된 값을 그대로 보낸다.
  command?: IdempotentCommand;
  // 멱등 키가 필요 없는 변경 요청의 본문. 처음 한 번만 직렬화해 CSRF 재시도에도 같은 문자열을 보낸다.
  json?: unknown;
  signal?: AbortSignal;
};

export type ApiSuccess = Readonly<{
  status: number;
  data: unknown;
  // 204 는 본문이 없어 null
  traceId: string | null;
}>;

const MUTATING: ReadonlySet<HttpMethod> = new Set([
  "POST",
  "PUT",
  "PATCH",
  "DELETE",
]);
const PATH_PREFIX = "/api/v1/";

function isJsonResponse(response: Response) {
  return (response.headers.get("content-type") ?? "").includes(
    "application/json",
  );
}

function parseRetryAfter(response: Response): number | null {
  const raw = response.headers.get("retry-after");
  if (raw === null || !/^\d+$/.test(raw.trim())) return null;
  return Number(raw.trim());
}

async function readJson(response: Response): Promise<unknown> {
  const text = await response.text();
  try {
    return JSON.parse(text);
  } catch {
    throw new ApiContractError(response.status, "JSON 을 읽을 수 없다");
  }
}

async function toError(response: Response): Promise<ApiError> {
  const retryAfterSeconds = parseRetryAfter(response);
  // 오류 본문이 JSON 이 아니면(프록시 · 게이트웨이 오류) code 없이 status 로 처리한다 (COMMON 7장 fallback)
  if (!isJsonResponse(response)) {
    return new ApiError({
      status: response.status,
      code: null,
      traceId: null,
      retryAfterSeconds,
    });
  }
  const parsed = errorEnvelopeSchema.safeParse(await readJson(response));
  if (!parsed.success) {
    throw new ApiContractError(response.status, "오류 껍데기 모양이 다르다");
  }
  return new ApiError({
    status: response.status,
    code: parsed.data.code,
    traceId: parsed.data.traceId,
    fieldErrors: parsed.data.fieldErrors,
    details: parsed.data.details,
    retryAfterSeconds,
  });
}

async function toSuccess(response: Response): Promise<ApiSuccess> {
  if (response.status === 204) {
    return { status: 204, data: null, traceId: null };
  }
  if (!isJsonResponse(response)) {
    throw new ApiContractError(response.status, "성공 응답이 JSON 이 아니다");
  }
  const parsed = successEnvelopeSchema.safeParse(await readJson(response));
  if (!parsed.success) {
    throw new ApiContractError(response.status, "성공 껍데기 모양이 다르다");
  }
  return {
    status: response.status,
    data: parsed.data.data,
    traceId: parsed.data.traceId,
  };
}

export function createApiClient(deps: {
  csrf: CsrfSource;
  fetch?: typeof fetch;
}) {
  const doFetch: typeof fetch =
    deps.fetch ??
    ((...args: Parameters<typeof fetch>) => globalThis.fetch(...args));

  async function request(req: ApiRequest): Promise<ApiSuccess> {
    if (!req.path.startsWith(PATH_PREFIX) || req.path.includes("://")) {
      throw new Error(`같은 출처 ${PATH_PREFIX} 경로만 부를 수 있다`);
    }
    if (req.command && req.json !== undefined) {
      throw new Error("command 와 json 을 함께 줄 수 없다");
    }
    const mutating = MUTATING.has(req.method);
    if (!mutating && (req.command || req.json !== undefined)) {
      throw new Error(`${req.method} 요청에는 본문을 보내지 않는다`);
    }

    // 본문은 여기서 한 번만 정한다. CSRF 재시도도 같은 문자열 · 같은 키를 보낸다 (I-05)
    const body =
      req.command?.body ??
      (req.json === undefined ? undefined : JSON.stringify(req.json));

    async function send(csrfToken: string | null): Promise<Response> {
      const headers: Record<string, string> = { Accept: "application/json" };
      if (body !== undefined) headers["Content-Type"] = "application/json";
      if (req.command) headers["Idempotency-Key"] = req.command.key;
      if (mutating && csrfToken) headers["X-CSRF-Token"] = csrfToken;
      try {
        return await doFetch(req.path, {
          method: req.method,
          headers,
          body,
          credentials: "same-origin",
          signal: req.signal,
        });
      } catch (cause) {
        throw new ApiNetworkError({ cause });
      }
    }

    let response = await send(mutating ? await deps.csrf.current() : null);
    if (response.ok) return toSuccess(response);

    let error = await toError(response);
    // 403 CSRF_FAILED: 세션을 한 번 다시 불러 새 토큰으로 1회만 재시도 (Q-02). 다시 실패하면 그대로 던진다
    if (mutating && error.status === 403 && error.code === "CSRF_FAILED") {
      response = await send(await deps.csrf.refresh());
      if (response.ok) return toSuccess(response);
      error = await toError(response);
    }
    throw error;
  }

  return { request };
}

export type ApiClient = ReturnType<typeof createApiClient>;
