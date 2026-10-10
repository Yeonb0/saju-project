"use client";

// "use client" 이유: 승인 요청 · 주문 조회는 브라우저에서 PG 복귀 직후에 한다.
// /pay/success 충전 승인 확인 (PG-3). 근거: TOPUP-DONE — CREDITED 일 때만 완료 · 잔액 표시,
// 처리 중 · 결과 불명확은 조회, 30초 초과는 확인 중 + 주문 확인 버튼. 새 결제 · 새 키로 유도하지 않는다.
// 승인은 주문 하나에 한 번 — React StrictMode 의 이중 실행에도 inflight 로 한 번만 보낸다 (CONFIRM-KEY).
// 디자인 요소 없음 (PG-FIRST). 배치는 LAYOUT-FIGMA PAY-03 · 04 · 05.
import { useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useEffect, useState } from "react";
import {
  type ConfirmTopUpResult,
  confirmTopUp,
  pollTopUpOrder,
} from "@/lib/payment/confirmTopUp";
import { getTopUpPort } from "@/lib/ports";
import type { PaymentReturn, TopUpPort } from "@/lib/ports/topUp";
import { loadPurchaseSelection } from "@/lib/purchase/restore";
import { WALLET_QUERY_KEY } from "@/lib/wallet/query";
import {
  PAY_ACTION_PRIMARY,
  PAY_ACTION_SECONDARY,
  PayResultLayout,
} from "../PayResultLayout";

type View =
  | { kind: "confirming" }
  | ConfirmTopUpResult
  | { kind: "error"; error: unknown };

type Deps = {
  sleep?: (ms: number, signal?: AbortSignal) => Promise<void>;
  now?: () => number;
};

// 페이지를 새로 불러오면 비워진다 — 그때 다시 보내는 승인은 같은 주문 ID 키라 서버가 처음 결과를 준다
const inflight = new Map<string, Promise<ConfirmTopUpResult>>();

export function TopUpSuccess({
  ret,
  port,
  deps,
}: {
  ret: PaymentReturn;
  // 테스트에서 주입한다. 기본값은 포트 선택(src/lib/ports)
  port?: Pick<TopUpPort, "confirm" | "getOrder">;
  deps?: Deps;
}) {
  // 포트는 렌더 중이 아니라 요청할 때 고른다 (진짜 구현이 없으면 오류 화면으로)
  const topUp = () => port ?? getTopUpPort();
  const queryClient = useQueryClient();
  const [view, setView] = useState<View>({ kind: "confirming" });
  // 충전 전 앞 화면 (PURCHASE-RESTORE). 저장소는 렌더가 아니라 effect 에서 읽는다 — 서버 렌더와 어긋나지 않게
  const [resumePath, setResumePath] = useState<string | null>(null);
  useEffect(() => {
    if (view.kind !== "credited") return;
    setResumePath(loadPurchaseSelection()?.returnPath ?? null);
    // CREDITED 일 때만 — 잔액은 서버에서 다시 받는다 (TOPUP-DONE · P-09 · Q-22: 클라이언트에서 합산하지 않는다)
    queryClient.invalidateQueries({ queryKey: WALLET_QUERY_KEY });
  }, [view.kind, queryClient]);

  useEffect(() => {
    let alive = true;
    let pending = inflight.get(ret.orderId);
    if (!pending) {
      pending = Promise.resolve().then(() =>
        confirmTopUp(ret, { port: port ?? getTopUpPort(), ...deps }),
      );
      inflight.set(ret.orderId, pending);
    }
    pending.then(
      (result) => {
        if (alive) setView(result);
      },
      (error: unknown) => {
        if (alive) setView({ kind: "error", error });
      },
    );
    return () => {
      alive = false;
    };
  }, [ret, port, deps]);

  // 시끄럽게 실패: 계약 위반 · 예상 밖 오류는 오류 화면(error.tsx)으로
  if (view.kind === "error") throw view.error;

  function recheck() {
    setView({ kind: "confirming" });
    Promise.resolve()
      .then(() => pollTopUpOrder(ret.orderId, { port: topUp(), ...deps }))
      .then(setView, (error: unknown) => setView({ kind: "error", error }));
  }

  if (view.kind === "confirming") {
    // 처음엔 위쪽만 (PD 메모 301:189)
    return (
      <PayResultLayout
        variant="waiting"
        // TODO(PD 문구)
        title={<output className="block">결제를 확인하고 있습니다</output>}
      />
    );
  }

  if (view.kind === "credited") {
    return (
      <PayResultLayout
        variant="done"
        // TODO(PD 문구)
        title={<output className="block">충전이 완료되었습니다</output>}
        // TODO(Q-17): 이번에 지급된 수량 — 승인 · 주문 응답에 필드가 생기면 서버 값만. 문구 TODO(PD 문구)
        sub={<span data-slot="credited-amount" />}
        box={
          <>
            <div className="flex items-end justify-between">
              {/* TODO(PD 문구) */}
              <span className="text-[15px] leading-[18px] text-[#737373]">
                보유
              </span>
              {/* 서버가 준 잔액만 표시한다 (P-09) */}
              <span
                data-slot="wallet-balance"
                className="text-[22px] leading-[26px] font-bold"
              >
                {view.walletBalance !== null
                  ? view.walletBalance.toLocaleString("ko-KR")
                  : null}
              </span>
            </div>
            {/* TODO(Q-17): 유료 · 보너스 내역 — 지갑 응답에 필드가 생기면 서버 값만 */}
            <p
              data-slot="wallet-breakdown"
              className="mt-[14px] min-h-[16px] text-[13px] leading-[16px] text-[#737373]"
            />
          </>
        }
        actions={
          <>
            {resumePath !== null ? (
              // 앞 화면이 저장한 선택으로 차감 확인 팝업을 다시 연다 (PURCHASE-RESTORE).
              // TODO(PD 문구): 원래 구매에 맞는 문구(PD 메모 301:179)
              <Link href={resumePath} className={PAY_ACTION_PRIMARY}>
                이어서 하기
              </Link>
            ) : null}
            {/* TODO(PD 문구) */}
            <Link href="/" className={PAY_ACTION_SECONDARY}>
              홈으로
            </Link>
          </>
        }
      />
    );
  }

  if (view.kind === "pending") {
    return (
      <PayResultLayout
        variant="waiting"
        // TODO(PD 문구)
        title={<output className="block">결제를 확인하고 있습니다</output>}
        // TODO(PD 문구) — 새 결제를 권하지 않는다 (TOPUP-DONE)
        box={<p>결제 확인이 늦어지고 있습니다</p>}
        actions={
          // TODO(PD 문구)
          <button
            type="button"
            className={PAY_ACTION_PRIMARY}
            onClick={recheck}
          >
            주문 확인
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

  return (
    <PayResultLayout
      variant="failed"
      // TODO(PD 문구)
      title={<p role="alert">충전이 완료되지 않았습니다</p>}
      // TODO(PD 문구 · Q-33): 원인별 안내 — 서버 · PG 원문은 쓰지 않는다
      sub={null}
      // TODO(PD 문구): 지급 · 청구 안내 — 원인별로 사실이 달라 확정 전 비운다
      box={<p data-slot="pay-result-note" />}
      actions={
        <>
          {/* TODO(PD 메모 301:201): 고른 상품 유지 — 저장 방식 미정이라 충전 화면 처음으로 간다. TODO(PD 문구) */}
          <Link href="/wallet" className={PAY_ACTION_PRIMARY}>
            충전으로 돌아가기
          </Link>
          {/* TODO(PD 문구) */}
          <Link href="/" className={PAY_ACTION_SECONDARY}>
            홈으로
          </Link>
        </>
      }
    />
  );
}
