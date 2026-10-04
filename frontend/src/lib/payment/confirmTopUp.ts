// /pay/success 의 승인 → 충전 확인 흐름 (화면 없이 순서만).
// 근거: TOPUP-DONE — CREDITED 일 때만 완료, PAID · processing 은 처리 중, 결과가 불명확하면 주문 조회
// 2초 간격 최대 30초, 그래도 미확정이면 확인 중(pending). 새 결제 · 새 멱등 키로 유도하지 않는다.
// 승인 요청의 키는 포트 구현이 주문 ID 로 만든다 (CONFIRM-KEY). 이 파일은 키를 만들지 않는다.
// PG 복귀 값은 계산 · 변환 없이 그대로 넘긴다 (CLAUDE.md 원화 결제).
import {
  ApiContractError,
  type ApiErrorKind,
  classifyApiError,
} from "@/lib/api/errors";
import type {
  PaymentReturn,
  TopUpOrderState,
  TopUpPort,
} from "@/lib/ports/topUp";

export const POLL_INTERVAL_MS = 2_000;
export const POLL_LIMIT_MS = 30_000;

export type ConfirmTopUpResult =
  | Readonly<{
      kind: "credited";
      orderId: string;
      walletBalance: number | null;
    }>
  | Readonly<{
      kind: "failed";
      orderId: string;
      cause:
        | "order_failed"
        | "order_canceled"
        | "order_refunded"
        | ApiErrorKind;
    }>
  // 30초 안에 확정되지 않음 — 확인 중 안내 + 주문 확인 버튼 (pollTopUpOrder 를 다시 부른다)
  | Readonly<{ kind: "pending"; orderId: string }>
  // 로그인 만료 · CSRF 재시도 실패 — 로그인 후 같은 복귀 주소로 돌아와 같은 주문으로 다시 확인한다
  | Readonly<{ kind: "login_required"; orderId: string }>;

type Deps = {
  port: Pick<TopUpPort, "confirm" | "getOrder">;
  sleep?: (ms: number, signal?: AbortSignal) => Promise<void>;
  now?: () => number;
  signal?: AbortSignal;
  pollIntervalMs?: number;
  pollLimitMs?: number;
};

// PG 복귀 쿼리에서 세 값을 읽는다. 하나라도 없으면 null — 승인 요청을 보내지 않는다.
export function readPaymentReturn(
  params: URLSearchParams,
): PaymentReturn | null {
  const paymentKey = params.get("paymentKey");
  const orderId = params.get("orderId");
  const amount = params.get("amount");
  if (!paymentKey || !orderId || !amount) return null;
  return { paymentKey, orderId, amount };
}

function defaultSleep(ms: number, signal?: AbortSignal) {
  return new Promise<void>((resolve, reject) => {
    if (signal?.aborted) {
      reject(signal.reason);
      return;
    }
    const timer = setTimeout(resolve, ms);
    signal?.addEventListener(
      "abort",
      () => {
        clearTimeout(timer);
        reject(signal.reason);
      },
      { once: true },
    );
  });
}

// 확정된 상태면 결과, 아니면 null(계속 조회). processing:false 만으로 성공 판단하지 않는다.
function settle(state: TopUpOrderState): ConfirmTopUpResult | null {
  switch (state.status) {
    case "CREDITED":
      return {
        kind: "credited",
        orderId: state.orderId,
        walletBalance: state.walletBalance,
      };
    case "FAILED":
      return { kind: "failed", orderId: state.orderId, cause: "order_failed" };
    case "CANCELED":
      return {
        kind: "failed",
        orderId: state.orderId,
        cause: "order_canceled",
      };
    case "REFUNDED":
      return {
        kind: "failed",
        orderId: state.orderId,
        cause: "order_refunded",
      };
    default:
      return null;
  }
}

// 결과가 불명확한 오류는 조회로 확인한다. 확정적인 오류만 실패로 끝낸다.
const POLL_ON: ReadonlySet<ApiErrorKind> = new Set([
  "outcome_unknown",
  "request_processing",
  "server",
  "conflict",
  "rate_limited",
  "unknown",
]);

function onError(error: unknown, orderId: string): ConfirmTopUpResult | "poll" {
  // 응답 모양이 계약과 다르면 조용히 넘어가지 않고 오류 화면으로
  if (error instanceof ApiContractError) throw error;
  if (error instanceof DOMException && error.name === "AbortError") throw error;
  const kind = classifyApiError(error);
  if (kind === "login_required" || kind === "csrf_failed") {
    return { kind: "login_required", orderId };
  }
  if (POLL_ON.has(kind)) return "poll";
  return { kind: "failed", orderId, cause: kind };
}

export async function pollTopUpOrder(
  orderId: string,
  deps: Deps,
): Promise<ConfirmTopUpResult> {
  const sleep = deps.sleep ?? defaultSleep;
  const now = deps.now ?? Date.now;
  const interval = deps.pollIntervalMs ?? POLL_INTERVAL_MS;
  const limit = deps.pollLimitMs ?? POLL_LIMIT_MS;
  const start = now();

  while (now() - start + interval <= limit) {
    await sleep(interval, deps.signal);
    try {
      const result = settle(await deps.port.getOrder(orderId));
      if (result) return result;
    } catch (error) {
      const next = onError(error, orderId);
      if (next !== "poll") return next;
    }
  }
  return { kind: "pending", orderId };
}

export async function confirmTopUp(
  ret: PaymentReturn,
  deps: Deps,
): Promise<ConfirmTopUpResult> {
  try {
    const result = settle(await deps.port.confirm(ret));
    if (result) return result;
  } catch (error) {
    const next = onError(error, ret.orderId);
    if (next !== "poll") return next;
  }
  return pollTopUpOrder(ret.orderId, deps);
}
