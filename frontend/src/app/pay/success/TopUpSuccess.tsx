"use client";

// "use client" 이유: 승인 요청 · 주문 조회는 브라우저에서 PG 복귀 직후에 한다.
// /pay/success 충전 승인 확인 (PG-3). 근거: TOPUP-DONE — CREDITED 일 때만 완료 · 잔액 표시,
// 처리 중 · 결과 불명확은 조회, 30초 초과는 확인 중 + 주문 확인 버튼. 새 결제 · 새 키로 유도하지 않는다.
// 승인은 주문 하나에 한 번 — React StrictMode 의 이중 실행에도 inflight 로 한 번만 보낸다 (CONFIRM-KEY).
// 디자인 요소 없음 (PG-FIRST). 배치는 LAYOUT-FIGMA PAY-03 · 04 · 05.
// 문구는 PD 확정 (FUNCTIONAL_SPEC 7장 결제 결과 화면 v0.4, Q-33 · Q-50). 다시 결제하기는 서버가 재결제를 허용할 때만 (nextAction RETRY_PAYMENT).
import { useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useEffect, useState } from "react";
import { ApiContractError } from "@/lib/api/errors";
import {
  type ConfirmTopUpResult,
  confirmTopUp,
  pollTopUpOrder,
} from "@/lib/payment/confirmTopUp";
import { getTopUpPort } from "@/lib/ports";
import type { PaymentReturn, TopUpPort } from "@/lib/ports/topUp";
import { loadPurchaseSelection } from "@/lib/purchase/restore";
import { supportMailtoHref } from "@/lib/support/contact";
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
  port?: Pick<TopUpPort, "confirm" | "getOrder" | "getWallet">;
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

  // 유료 · 보너스 내역은 지갑 응답 그대로 (P-09 · Q-22). 완료 뒤에만 받는다 — 렌더 중에 포트를 고르지 않는다
  const wallet = useQuery({
    queryKey: WALLET_QUERY_KEY,
    queryFn: () => topUp().getWallet(),
    enabled: view.kind === "credited",
  });

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
  // 계약 위반은 시끄럽게, 그 밖의 조회 실패는 내역 칸만 비운다
  if (wallet.error instanceof ApiContractError) throw wallet.error;

  function recheck() {
    setView({ kind: "confirming" });
    Promise.resolve()
      .then(() => pollTopUpOrder(ret.orderId, { port: topUp(), ...deps }))
      .then(setView, (error: unknown) => setView({ kind: "error", error }));
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

  if (view.kind === "credited") {
    return (
      <PayResultLayout
        variant="done"
        title={<output className="block">충전 완료!</output>}
        // 서버가 준 이번 지급 수량만 (TOPUP-DONE, Q-17 해결). null 이면 부제를 비운다
        sub={
          <span data-slot="credited-amount">
            {view.grantedShellAmount !== null
              ? `등껍질 ${view.grantedShellAmount.toLocaleString("ko-KR")}개가 들어왔어요`
              : null}
          </span>
        }
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
            {/* 현재 유료 · 보너스 잔액 — 지갑 응답 그대로. 없으면 비운다. TODO(PD 문구) */}
            <p
              data-slot="wallet-breakdown"
              className="mt-[14px] min-h-[16px] text-[13px] leading-[16px] text-[#737373]"
            >
              {wallet.data
                ? `유료 ${wallet.data.paidBalance.toLocaleString("ko-KR")} · 보너스 ${wallet.data.bonusBalance.toLocaleString("ko-KR")}`
                : null}
            </p>
          </>
        }
        actions={
          <>
            {resumePath !== null ? (
              // 앞 화면이 저장한 선택으로 차감 확인 팝업을 다시 연다 (PURCHASE-RESTORE).
              <Link href={resumePath} className={PAY_ACTION_PRIMARY}>
                이어서 사주 보러 가기
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
            onClick={recheck}
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

  // 서버가 확인 필요(CONTACT_SUPPORT)로 답했다 — 다시 결제하기 없이 문의 (FUNCTIONAL_SPEC 7장)
  if (view.next === "contact") {
    const contactHref = supportMailtoHref({
      kind: "topUpOrder",
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

  return (
    <PayResultLayout
      variant="failed"
      title={<p role="alert">결제가 완료되지 않았어요</p>}
      sub={<p>결제 중 문제가 생겼어요. 잠시 후 다시 시도해 주세요.</p>}
      box={<p>결제가 완료되지 않아 등껍질은 지급되지 않았어요.</p>}
      actions={
        <>
          {/* 서버가 재결제를 허용할 때만 (Q-50). 고른 상품 유지는 다음 단계 TOPUP-RETRY-URL — 지금은 충전 화면 처음으로 간다 */}
          {view.next === "retry" ? (
            <Link href="/wallet" className={PAY_ACTION_PRIMARY}>
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
