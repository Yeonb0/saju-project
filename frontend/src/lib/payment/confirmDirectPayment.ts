// /pay/success?flow=direct 의 승인 → 결제 확인 흐름 (화면 없이 순서만). confirmTopUp 과 같은 구조.
// 근거: DIRECT-PAY · BE 전달본 10/16 — PAID 일 때만 다음 단계(운세 생성), PAYMENT_PENDING 은 주문 조회 (TOPUP-DONE 과 같은 규칙: 2초 간격 최대 30초),
// REVIEW_REQUIRED 는 새 주문으로 덮지 않고 문의로 보낸다. 결제 여부를 모르는 실패에 다시 결제를 권하지 않는다.
// 승인 요청의 키는 포트 구현이 주문 ID 로 만든다 (CONFIRM-KEY). 이 파일은 키를 만들지 않는다.
// PG 복귀 값은 계산 · 변환 없이 그대로 넘긴다 (CLAUDE.md 원화 결제).
import {
  ApiContractError,
  type ApiErrorKind,
  classifyApiError,
} from "@/lib/api/errors";
import type {
  DirectPaymentOrder,
  DirectPaymentPort,
} from "@/lib/ports/directPayment";
import type { PaymentReturn } from "@/lib/ports/topUp";
import { defaultSleep, POLL_INTERVAL_MS, POLL_LIMIT_MS } from "./confirmTopUp";

export type ConfirmDirectPaymentResult =
  // 결제 승인이 확인됐다 — 이때만 운세 생성을 보낸다
  | Readonly<{ kind: "paid"; orderId: string }>
  // 결제가 일어나지 않았다 (FAILED · CANCELED)
  | Readonly<{ kind: "failed"; orderId: string }>
  // 결제 여부를 사람이 확인해야 한다 (REVIEW_REQUIRED · 모르는 상태 · 없는 주문 · 확정적 오류) — 다시 결제하지 않는다
  | Readonly<{ kind: "review"; orderId: string }>
  // 30초 안에 확정되지 않음 — 확인 중 안내 + 주문 확인 버튼 (pollDirectPayment 를 다시 부른다)
  | Readonly<{ kind: "pending"; orderId: string }>
  // 로그인 만료 · CSRF 재시도 실패
  | Readonly<{ kind: "login_required"; orderId: string }>;

type Deps = {
  port: Pick<DirectPaymentPort, "confirm" | "getOrder">;
  sleep?: (ms: number, signal?: AbortSignal) => Promise<void>;
  now?: () => number;
  signal?: AbortSignal;
  pollIntervalMs?: number;
  pollLimitMs?: number;
};

// 확정된 상태면 결과, 아직 결제 전(PAYMENT_PENDING)이면 null(계속 조회)
function settle(order: DirectPaymentOrder): ConfirmDirectPaymentResult | null {
  const { orderId } = order;
  switch (order.status) {
    case "PAID":
      return { kind: "paid", orderId };
    case "FAILED":
    case "CANCELED":
      return { kind: "failed", orderId };
    case "PAYMENT_PENDING":
      return null;
    default:
      // REVIEW_REQUIRED · UNKNOWN
      return { kind: "review", orderId };
  }
}

// 결과가 불명확한 오류는 조회로 확인한다 (confirmTopUp 의 POLL_ON 과 같은 종류)
const POLL_ON: ReadonlySet<ApiErrorKind> = new Set([
  "outcome_unknown",
  "request_processing",
  "server",
  "conflict",
  "rate_limited",
  "unknown",
]);

function onError(
  error: unknown,
  orderId: string,
): ConfirmDirectPaymentResult | "poll" {
  // 응답 모양이 계약과 다르면 조용히 넘어가지 않고 오류 화면으로
  if (error instanceof ApiContractError) throw error;
  if (error instanceof DOMException && error.name === "AbortError") throw error;
  const kind = classifyApiError(error);
  if (kind === "login_required" || kind === "csrf_failed") {
    return { kind: "login_required", orderId };
  }
  if (POLL_ON.has(kind)) return "poll";
  // 없는 주문(404)을 포함한 확정적 오류 — 결제 여부를 모르므로 다시 결제를 권하지 않고 문의로 (review)
  return { kind: "review", orderId };
}

export async function pollDirectPayment(
  orderId: string,
  deps: Deps,
): Promise<ConfirmDirectPaymentResult> {
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

export async function confirmDirectPayment(
  ret: PaymentReturn,
  deps: Deps,
): Promise<ConfirmDirectPaymentResult> {
  try {
    const result = settle(await deps.port.confirm(ret));
    if (result) return result;
  } catch (error) {
    const next = onError(error, ret.orderId);
    if (next !== "poll") return next;
  }
  return pollDirectPayment(ret.orderId, deps);
}
