// API 오류 객체와 화면 처리 분류.
// 근거: docs/COMMON_RESPONSE_AND_ERROR_CODES.md 4장 · 7장, frontend/docs/FRONTEND.md 1-1 · 1-2 (MOCK-PORT).
// 분기는 code 로만 한다. message 문자열은 비교하지 않고 보관하지도 않는다 — 화면에 서버 원문을 내지 않기 위해서다.
// 알 수 없는 code 는 HTTP status 로 분류한다 (COMMON 7장).

export type FieldError = Readonly<{ field: string; reason: string }>;

export type ApiErrorDetails = Readonly<
  Record<string, string | number | boolean | null>
>;

// 서버가 오류 응답을 준 경우. code 가 null 이면 오류 본문이 JSON 이 아니었다 (프록시 · 게이트웨이 오류 등).
export class ApiError extends Error {
  readonly status: number;
  readonly code: string | null;
  readonly traceId: string | null;
  readonly fieldErrors: readonly FieldError[];
  readonly details: ApiErrorDetails | undefined;
  // 429 의 Retry-After(초). 없거나 숫자가 아니면 null
  readonly retryAfterSeconds: number | null;

  constructor(init: {
    status: number;
    code: string | null;
    traceId: string | null;
    fieldErrors?: readonly FieldError[];
    details?: ApiErrorDetails;
    retryAfterSeconds?: number | null;
  }) {
    // Error.message 에는 서버 문구를 넣지 않는다 (CLAUDE.md 오류 화면 — 서버 원문 노출 금지)
    super(`API ${init.status} ${init.code ?? "NO_CODE"}`);
    this.name = "ApiError";
    this.status = init.status;
    this.code = init.code;
    this.traceId = init.traceId;
    this.fieldErrors = init.fieldErrors ?? [];
    this.details = init.details;
    this.retryAfterSeconds = init.retryAfterSeconds ?? null;
  }
}

// 요청이 서버에 닿았는지 알 수 없는 경우 (네트워크 끊김 · 중단 등).
// 결과가 불명확하다는 이유로 새 Idempotency-Key 를 만들지 않는다 — 같은 키로 다시 보내거나 상태를 조회한다 (I-05).
export class ApiNetworkError extends Error {
  constructor(options?: { cause?: unknown }) {
    super("API 요청 결과를 알 수 없다", options);
    this.name = "ApiNetworkError";
  }
}

// 응답 모양이 공통 응답 계약과 다른 경우. 조용히 넘어가지 않고 오류 화면(error.tsx)으로 던진다.
export class ApiContractError extends Error {
  readonly status: number;

  constructor(status: number, reason: string) {
    super(`API 응답 모양이 계약과 다르다 (${status}): ${reason}`);
    this.name = "ApiContractError";
    this.status = status;
  }
}

// 화면이 고를 처리 종류 (COMMON 7장 프론트 처리 계약)
export type ApiErrorKind =
  | "login_required" // 401 — 로그인 후 원 경로 복귀
  | "csrf_failed" // 403 CSRF_FAILED — 1회 재시도 후에도 실패 = 로그인 만료 · 보안 오류
  | "forbidden" // 403 그 밖
  | "insufficient_balance" // 409 INSUFFICIENT_BALANCE — 부족분 · 추천 충전 모달 (서버 값만)
  | "request_processing" // 409 IDEMPOTENCY_REQUEST_PROCESSING — 버튼을 다시 열지 않고 상태 조회
  | "quote_expired" // 409 QUOTE_EXPIRED — 선택 유지 + 새 견적
  | "price_changed" // 409 PRICE_CHANGED — 다시 확인받기
  | "conflict" // 409 그 밖
  | "not_found" // 404
  | "gone" // 410 — 상세 없이 만료 · 폐기 화면
  | "invalid_input" // 400 · 422 — 입력 · 상품 선택 화면으로
  | "birth_time_required" // 422 BIRTH_TIME_REQUIRED_AT_TERM — 절기 경계일 시간 미상, 출생 시간 입력 안내 (API_SPEC 8장 · COMMON 4.4)
  | "rate_limited" // 429 — Retry-After 동안 CTA 비활성
  | "server" // 5xx — 일반 오류 안내 + traceId
  | "outcome_unknown" // 네트워크 — 결과 불명확, 같은 키로 재시도 또는 상태 조회
  | "unknown"; // 그 밖의 status

// PURCHASE_DEBIT_MISMATCH(500) · READING_FULFILLMENT_UNAVAILABLE(503) 은 처리 방식 확인 전이라 status 로 "server" — TODO(Q-35)
const KIND_BY_CODE: Readonly<Record<string, ApiErrorKind>> = {
  CSRF_FAILED: "csrf_failed",
  INSUFFICIENT_BALANCE: "insufficient_balance",
  IDEMPOTENCY_REQUEST_PROCESSING: "request_processing",
  QUOTE_EXPIRED: "quote_expired",
  PRICE_CHANGED: "price_changed",
  BIRTH_TIME_REQUIRED_AT_TERM: "birth_time_required",
};

function kindByStatus(status: number): ApiErrorKind {
  if (status === 401) return "login_required";
  if (status === 403) return "forbidden";
  if (status === 404) return "not_found";
  if (status === 409) return "conflict";
  if (status === 410) return "gone";
  if (status === 400 || status === 422) return "invalid_input";
  if (status === 429) return "rate_limited";
  if (status >= 500 && status <= 599) return "server";
  return "unknown";
}

export function classifyApiError(error: unknown): ApiErrorKind {
  if (error instanceof ApiNetworkError) return "outcome_unknown";
  if (!(error instanceof ApiError)) return "unknown";
  // 401 은 code 와 무관하게 로그인 처리 (SESSION_EXPIRED · KAKAO_OAUTH_FAILED 포함, COMMON 7장)
  if (error.status === 401) return "login_required";
  const byCode = error.code === null ? undefined : KIND_BY_CODE[error.code];
  return byCode ?? kindByStatus(error.status);
}
