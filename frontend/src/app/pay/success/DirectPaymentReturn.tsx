"use client";

// "use client" 이유: 승인 요청 · 주문 조회 · 운세 생성 요청은 브라우저에서 PG 복귀 직후에 한다.
// /pay/success?flow=direct 직접결제 복귀 (DIRECT-PAY). 순서: 승인 확인 → PAID 일 때만 운세 생성 → 완료 「준비가 끝났어요!」 → 「결과 보러 가기」.
// 근거: CONFIRM-KEY (승인 키는 포트가 주문 ID 로 만든다 — 이 화면은 승인 키를 만들지 않는다), 승인은 주문 하나에 한 번 — React StrictMode 의 이중 실행에도 inflight 로 한 번만,
// I-05 (생성 키는 복원값에 저장해 새로고침해도 같은 키 · 같은 입력으로 보낸다. 결과가 불명확하다는 이유로 새 키 · 새 주문을 만들지 않는다),
// Q-38 (완료는 FULFILLED + readingId 일 때만, 화면 분기는 status), PURCHASE-STATUS-UI (처리 중 자동 반복 조회 없음 — 오류 직후 한 번, 그 뒤는 「다시 확인하기」),
// BE 전달본 10/16 (PAID 일 때만 생성, PAYMENT_PENDING 은 조회, REVIEW_REQUIRED 는 새 주문으로 덮지 않고 문의),
// b3d2051 (완료되어도 결과 화면으로 자동 이동하지 않는다 — 버튼을 누를 때 한 번만),
// Q-65 (직접결제 생성 실패는 「다시 시도」 대신 문의 — 서버 nextAction 이 RETRY_PURCHASE 여도 두 번 결제로 이어질 수 있다. TODO(Q-65) 서버 값 확인 뒤 재검토).
// 화면에 paymentKey · error.message · 서버 원문은 쓰지 않는다. 디자인 요소 없음 (PG-FIRST). 배치는 LAYOUT-FIGMA PAY-04 · 05.
// 결제 단계 문구는 FUNCTIONAL_SPEC 7장 PD 확정 문구다 (옛 충전 복귀 화면과 같은 문구).
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { AppShell } from "@/components/AppShell";
import { Button } from "@/components/Button";
import { GenerationDone, GenerationScene } from "@/components/GenerationScene";
import { ApiContractError, ApiError, classifyApiError } from "@/lib/api/errors";
import { createIdempotencyKey } from "@/lib/api/idempotency";
import {
  type ConfirmDirectPaymentResult,
  confirmDirectPayment,
  pollDirectPayment,
} from "@/lib/payment/confirmDirectPayment";
import { getDirectPaymentPort, getFortunePort } from "@/lib/ports";
import type { DirectPaymentPort } from "@/lib/ports/directPayment";
import type {
  FortunePort,
  FortuneSelection,
  ReadingPurchaseState,
} from "@/lib/ports/fortune";
import type { PaymentReturn } from "@/lib/ports/topUp";
import {
  clearPurchaseSelection,
  loadPurchaseSelection,
  savePurchaseSelection,
} from "@/lib/purchase/restore";
import { purchaseOutcomeOf } from "@/lib/reading/generation";
import { readingResultPath } from "@/lib/reading/resultPath";
import { supportMailtoHref } from "@/lib/support/contact";
import {
  PAY_ACTION_PRIMARY,
  PAY_ACTION_SECONDARY,
  PayResultLayout,
} from "../PayResultLayout";

type FulfillView =
  // 생성 응답을 기다리거나 결제 확인이 끝나 생성을 보내는 중
  | { kind: "fulfilling" }
  | {
      kind: "done";
      readingId: string;
      fortuneType: FortuneSelection["fortuneType"];
    }
  // 구매 결과를 아직 모른다 (COMMON-03)
  | { kind: "purchase_pending" }
  // 생성 실패 확정 — 다시 시도 없이 문의 (Q-65)
  | { kind: "purchase_failed"; purchaseId: string | null };

type View =
  | { kind: "confirming" }
  | ConfirmDirectPaymentResult
  | FulfillView
  | { kind: "error"; error: unknown };

type Deps = {
  sleep?: (ms: number, signal?: AbortSignal) => Promise<void>;
  now?: () => number;
};

// 결제는 됐지만 생성을 이어 갈 수 없는 서버 응답 — 새 주문 · 새 결제 없이 문의로 (BE 전달본 10/16)
const REVIEW_CODES = new Set([
  "PAYMENT_ORDER_NOT_CONFIRMABLE",
  "PAYMENT_CONFIRMATION_CONFLICT",
  "INVALID_REQUEST",
  "RESOURCE_NOT_FOUND",
]);

// 페이지를 새로 불러오면 비워진다 — 그때 다시 보내는 승인은 같은 주문 ID 키라 서버가 처음 결과를 준다
const inflight = new Map<string, Promise<ConfirmDirectPaymentResult>>();
// 주문 하나의 첫 생성 요청 (StrictMode 이중 실행에도 한 번)
const fulfilling = new Map<string, Promise<View>>();

type Ctx = {
  quoteId: string;
  selection: FortuneSelection;
  key: string;
};

export function DirectPaymentReturn({
  ret,
  directPort,
  fortunePort,
  deps,
}: {
  ret: PaymentReturn;
  // 테스트에서 주입한다. 기본값은 포트 선택(src/lib/ports) — 요청할 때 고른다 (MOCK-PORT)
  directPort?: Pick<
    DirectPaymentPort,
    "confirm" | "getOrder" | "fulfillReading"
  >;
  fortunePort?: Pick<FortunePort, "getPurchaseStatus">;
  deps?: Deps;
}) {
  const router = useRouter();
  const direct = () => directPort ?? getDirectPaymentPort();
  const fortune = () => fortunePort ?? getFortunePort();
  const [view, setView] = useState<View>({ kind: "confirming" });
  // 결제가 일어나지 않았을 때 「다시 결제하기」 가 돌아갈 앞 화면 — 이 주문의 복원값일 때만 (PURCHASE-RESTORE)
  const [retryPath, setRetryPath] = useState<string | null>(null);
  const ctx = useRef<Ctx | null>(null);
  const delivered = useRef(false);

  // 이 화면이 만든 생성 요청 정보. 같은 탭에서 화면이 다시 그려져도 복원값의 생성 키로 이어 간다 (I-05)
  function currentCtx(): Ctx | null {
    if (ctx.current) return ctx.current;
    const saved = loadPurchaseSelection();
    if (saved === null || saved.orderId !== ret.orderId) return null;
    if (saved.fulfillmentKey === null) return null;
    ctx.current = {
      quoteId: saved.quoteId,
      selection: saved.selection,
      key: saved.fulfillmentKey,
    };
    return ctx.current;
  }

  // 서버 구매 상태를 화면 상태로 바꾼다 (Q-38)
  function fromState(state: ReadingPurchaseState, current: Ctx): View {
    const outcome = purchaseOutcomeOf(state);
    if (outcome.kind === "fulfilled") {
      return {
        kind: "done",
        readingId: outcome.readingId,
        fortuneType: current.selection.fortuneType,
      };
    }
    if (outcome.kind === "failed") {
      // 서버 nextAction 이 RETRY_PURCHASE 여도 다시 시도를 열지 않는다 (Q-65)
      return { kind: "purchase_failed", purchaseId: state.purchaseId };
    }
    return { kind: "purchase_pending" };
  }

  // 오류 직후 · 완료가 아닌 응답 직후 서버 구매 상태를 한 번 본다. 기록이 없으면 resend 가 true 일 때만 같은 키 · 같은 입력으로 다시 보낸다
  async function checkStatus(resend: boolean): Promise<View> {
    const current = currentCtx();
    if (!current) return { kind: "review", orderId: ret.orderId };
    try {
      const state = await fortune().getPurchaseStatus(current.quoteId);
      if (state) return fromState(state, current);
      return resend ? await send() : { kind: "purchase_pending" };
    } catch (error) {
      if (error instanceof ApiContractError) throw error;
      if (isLogin(error))
        return { kind: "login_required", orderId: ret.orderId };
      // 상태 조회 실패 — 결과를 모른 채 확인 버튼만 남긴다 (새 요청을 만들지 않는다)
      return { kind: "purchase_pending" };
    }
  }

  // 생성 요청: 같은 키 · 같은 입력 (I-05)
  async function send(): Promise<View> {
    const current = currentCtx();
    if (!current) return { kind: "review", orderId: ret.orderId };
    try {
      const result = await direct().fulfillReading(
        {
          orderId: ret.orderId,
          quoteId: current.quoteId,
          selection: current.selection,
        },
        current.key,
      );
      if (result.status === "FULFILLED" && result.readingId !== null) {
        return {
          kind: "done",
          readingId: result.readingId,
          fortuneType: current.selection.fortuneType,
        };
      }
      // 그 밖의 응답 — 서버 구매 상태를 한 번 본다 (resend 하지 않는다: 방금 보냈다)
      return await checkStatus(false);
    } catch (error) {
      if (error instanceof ApiContractError) throw error;
      if (isLogin(error))
        return { kind: "login_required", orderId: ret.orderId };
      if (
        error instanceof ApiError &&
        error.code !== null &&
        REVIEW_CODES.has(error.code)
      ) {
        return { kind: "review", orderId: ret.orderId };
      }
      // request_processing · outcome_unknown · 생성 실패 오류(isGenerationFailureError) · 그 밖 — 모두 서버 구매 상태를 본다 (Q-38)
      return await checkStatus(false);
    }
  }

  // PAID 뒤: 복원값을 읽어 생성 키를 정하고 보낸다
  async function startFulfillment(): Promise<View> {
    const saved = loadPurchaseSelection();
    // 결제는 됐지만 무엇을 만들지 모른다 — 문의로
    if (saved === null || saved.orderId !== ret.orderId) {
      return { kind: "review", orderId: ret.orderId };
    }
    const key = saved.fulfillmentKey ?? createIdempotencyKey();
    if (saved.fulfillmentKey === null) {
      // 보내기 전에 키를 저장한다 — 새로고침해도 같은 키 (I-05)
      savePurchaseSelection({ ...saved, fulfillmentKey: key });
    }
    ctx.current = { quoteId: saved.quoteId, selection: saved.selection, key };
    return send();
  }

  // biome-ignore lint/correctness/useExhaustiveDependencies: 포트 선택 · 생성 함수는 요청할 때 쓰는 클로저라 ret · 주입 포트가 바뀔 때만 다시 돈다
  useEffect(() => {
    let alive = true;
    let pending = inflight.get(ret.orderId);
    if (!pending) {
      pending = Promise.resolve().then(() =>
        confirmDirectPayment(ret, { port: direct(), ...deps }),
      );
      inflight.set(ret.orderId, pending);
    }
    pending
      .then(async (result): Promise<View> => {
        if (result.kind !== "paid") return result;
        // PAID 일 때만 생성한다. 첫 생성 요청은 주문 하나에 한 번
        if (alive) setView({ kind: "fulfilling" });
        let started = fulfilling.get(ret.orderId);
        if (!started) {
          started = startFulfillment();
          fulfilling.set(ret.orderId, started);
        }
        return started;
      })
      .then(
        (next) => {
          if (alive) setView(next);
        },
        (error: unknown) => {
          if (alive) setView({ kind: "error", error });
        },
      );
    return () => {
      alive = false;
    };
  }, [ret, directPort, fortunePort, deps]);

  // 결제가 일어나지 않았을 때 돌아갈 앞 화면 — 저장소는 렌더가 아니라 effect 에서 읽는다
  useEffect(() => {
    if (view.kind !== "failed") return;
    const saved = loadPurchaseSelection();
    setRetryPath(saved?.orderId === ret.orderId ? saved.returnPath : null);
  }, [view.kind, ret.orderId]);

  // 생성 실패가 확정되면 복원값을 지운다. 완료는 「결과 보러 가기」 를 누를 때 지운다 — 누르기 전 새로고침해도 같은 생성 키로 같은 결과를 다시 받는다 (I-05)
  useEffect(() => {
    if (view.kind === "purchase_failed") {
      clearPurchaseSelection();
    }
  }, [view.kind]);

  // 시끄럽게 실패: 계약 위반 · 예상 밖 오류는 오류 화면(error.tsx)으로
  if (view.kind === "error") throw view.error;

  function recheckPayment() {
    setView({ kind: "confirming" });
    Promise.resolve()
      .then(() => pollDirectPayment(ret.orderId, { port: direct(), ...deps }))
      .then(async (result): Promise<View> => {
        if (result.kind !== "paid") return result;
        setView({ kind: "fulfilling" });
        let started = fulfilling.get(ret.orderId);
        if (!started) {
          started = startFulfillment();
          fulfilling.set(ret.orderId, started);
        }
        return started;
      })
      .then(setView, (error: unknown) => setView({ kind: "error", error }));
  }

  // 「다시 확인하기」 — 서버 구매 상태. 기록이 없으면 같은 키 · 같은 입력으로 다시 보낸다 (PURCHASE-STATUS-UI)
  function recheckPurchase() {
    setView({ kind: "fulfilling" });
    checkStatus(true).then(setView, (error: unknown) =>
      setView({ kind: "error", error }),
    );
  }

  // 「결과 보러 가기」 — 두 번 눌러도 이동은 한 번. 자동 이동 없음 (b3d2051)
  function onView() {
    if (view.kind !== "done" || delivered.current) return;
    delivered.current = true;
    clearPurchaseSelection();
    router.push(readingResultPath(view.fortuneType, view.readingId));
  }

  if (view.kind === "confirming") {
    return (
      <PayResultLayout
        variant="waiting"
        title={<output className="block">결제를 확인하고 있어요</output>}
        sub={<p>잠시만 기다려 주세요. 이 화면을 닫지 말아 주세요.</p>}
      />
    );
  }

  if (view.kind === "pending") {
    return (
      <PayResultLayout
        variant="waiting"
        title={<output className="block">결제를 확인하고 있어요</output>}
        sub={<p>잠시만 기다려 주세요. 이 화면을 닫지 말아 주세요.</p>}
        // 새 결제를 권하지 않는다 (TOPUP-DONE · Q-50)
        box={
          <p>
            확인이 조금 늦어지고 있어요. 다시 결제하지 말고 아래 버튼으로 주문
            상태를 확인해 주세요.
          </p>
        }
        actions={
          <button
            type="button"
            className={PAY_ACTION_PRIMARY}
            onClick={recheckPayment}
          >
            주문 확인하기
          </button>
        }
      />
    );
  }

  if (view.kind === "login_required") {
    return (
      <PayResultLayout
        variant="waiting"
        title={
          // TODO(PG-2): 로그인 후 이 주소로 복귀 (returnTo) — 같은 주문으로 다시 확인한다
          <p>
            {/* TODO(PD 문구) */}
            <Link href="/login" className="underline">
              다시 로그인해 주세요
            </Link>
          </p>
        }
      />
    );
  }

  if (view.kind === "review") {
    const contactHref = supportMailtoHref({
      kind: "paymentOrder",
      id: view.orderId,
    });
    return (
      <PayResultLayout
        variant="failed"
        title={
          <p role="alert">확인이 필요한 결제예요. 고객센터로 문의해 주세요.</p>
        }
        actions={
          <>
            {contactHref === null ? null : (
              <a href={contactHref} className={PAY_ACTION_PRIMARY}>
                문의하기
              </a>
            )}
            <Link href="/" className={PAY_ACTION_SECONDARY}>
              홈으로
            </Link>
          </>
        }
      />
    );
  }

  if (view.kind === "failed") {
    return (
      <PayResultLayout
        variant="failed"
        title={<p role="alert">결제가 완료되지 않았어요</p>}
        sub={<p>결제 중 문제가 생겼어요. 잠시 후 다시 시도해 주세요.</p>}
        // TODO(PD 문구) — Figma 직접결제 수정안 PAY-05 초안
        box={<p>결제가 완료되지 않아 결제 금액은 청구되지 않았어요.</p>}
        actions={
          <>
            {retryPath !== null ? (
              <Link href={retryPath} className={PAY_ACTION_PRIMARY}>
                다시 결제하기
              </Link>
            ) : null}
            <Link href="/" className={PAY_ACTION_SECONDARY}>
              홈으로
            </Link>
          </>
        }
      />
    );
  }

  // 여기부터 생성 단계 (헤더 없는 AppShell 안 — 대기 · 완료 장면)
  if (view.kind === "done") {
    return (
      <AppShell header={false}>
        <GenerationDone onView={onView} />
      </AppShell>
    );
  }

  if (view.kind === "purchase_pending") {
    return (
      <AppShell header={false}>
        <div className="pt-[367px] text-center text-[20px] leading-[normal]">
          {/* COMMON-03 */}
          <output className="block">
            <span className="block">구매 결과를 아직 확인하지 못했어요</span>
            <span className="block">잠시 후 다시 확인해 주세요.</span>
          </output>
          <div className="mt-[24px] flex justify-center">
            <Button variant="popup" onClick={recheckPurchase}>
              다시 확인하기
            </Button>
          </div>
        </div>
      </AppShell>
    );
  }

  if (view.kind === "purchase_failed") {
    const contactHref =
      view.purchaseId === null || view.purchaseId === ""
        ? supportMailtoHref({ kind: "paymentOrder", id: ret.orderId })
        : supportMailtoHref({ kind: "purchase", id: view.purchaseId });
    return (
      <AppShell header={false}>
        <div className="pt-[367px] text-center text-[20px] leading-[normal]">
          <GenerationScene
            failure={{ refunded: false, next: "contact_support" }}
            actions={
              <div className="mt-[18px] flex flex-col items-center">
                {contactHref === null ? null : (
                  <a
                    href={contactHref}
                    className="mt-[8px] text-[16px] underline"
                  >
                    문의하기
                  </a>
                )}
                <Link href="/" className="mt-[8px] text-[16px] underline">
                  홈으로
                </Link>
              </div>
            }
          />
        </div>
      </AppShell>
    );
  }

  // fulfilling
  return (
    <AppShell header={false}>
      <GenerationScene failure={null} />
    </AppShell>
  );
}

function isLogin(error: unknown): boolean {
  const kind = classifyApiError(error);
  return kind === "login_required" || kind === "csrf_failed";
}
