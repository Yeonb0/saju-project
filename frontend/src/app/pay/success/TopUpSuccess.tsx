"use client";

// "use client" 이유: 승인 요청 · 주문 조회는 브라우저에서 PG 복귀 직후에 한다.
// /pay/success 충전 승인 확인 (PG-3). 근거: TOPUP-DONE — CREDITED 일 때만 완료 · 잔액 표시,
// 처리 중 · 결과 불명확은 조회, 30초 초과는 확인 중 + 주문 확인 버튼. 새 결제 · 새 키로 유도하지 않는다.
// 승인은 주문 하나에 한 번 — React StrictMode 의 이중 실행에도 inflight 로 한 번만 보낸다 (CONFIRM-KEY).
// 디자인 요소 없음 (PG-FIRST).
import Link from "next/link";
import { useEffect, useState } from "react";
import { AppShell } from "@/components/AppShell";
import { Button } from "@/components/Button";
import {
  type ConfirmTopUpResult,
  confirmTopUp,
  pollTopUpOrder,
} from "@/lib/payment/confirmTopUp";
import { getTopUpPort } from "@/lib/ports";
import type { PaymentReturn, TopUpPort } from "@/lib/ports/topUp";
import { loadPurchaseSelection } from "@/lib/purchase/restore";

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
  const [view, setView] = useState<View>({ kind: "confirming" });
  // 충전 전 앞 화면 (PURCHASE-RESTORE). 저장소는 렌더가 아니라 effect 에서 읽는다 — 서버 렌더와 어긋나지 않게
  const [resumePath, setResumePath] = useState<string | null>(null);
  useEffect(() => {
    if (view.kind !== "credited") return;
    setResumePath(loadPurchaseSelection()?.returnPath ?? null);
  }, [view.kind]);

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

  return (
    // TODO(PD 문구): 제목
    <AppShell title="충전" backHref="/wallet">
      {/* 본문 좌우 34px · 헤더 아래 32px · 요소 사이 16px · 16px (LAYOUT-FIGMA) */}
      <div className="mx-[34px] mt-[32px] flex flex-col gap-y-[16px] text-[16px]">
        {view.kind === "confirming" ? (
          // TODO(PD 문구)
          <output className="block">결제를 확인하고 있습니다</output>
        ) : view.kind === "credited" ? (
          <>
            {/* TODO(PD 문구) */}
            <output className="block">충전이 완료되었습니다</output>
            {view.walletBalance !== null ? (
              // 서버가 준 잔액만 표시한다 (P-09). TODO(PD 문구)
              <p>보유 {view.walletBalance.toLocaleString("ko-KR")}</p>
            ) : null}
            {resumePath !== null ? (
              // 앞 화면이 저장한 선택으로 차감 확인 팝업을 다시 연다 (PURCHASE-RESTORE). TODO(PD 문구)
              <Link href={resumePath} className="underline">
                이어서 하기
              </Link>
            ) : (
              // TODO(PD 문구)
              <Link href="/wallet" className="underline">
                확인
              </Link>
            )}
          </>
        ) : view.kind === "pending" ? (
          <>
            {/* TODO(PD 문구) — 새 결제를 권하지 않는다 (TOPUP-DONE) */}
            <output className="block">결제 확인이 늦어지고 있습니다</output>
            {/* TODO(PD 문구) */}
            <Button className="self-start" onClick={recheck}>
              주문 확인
            </Button>
          </>
        ) : view.kind === "login_required" ? (
          // TODO(PG-2): 로그인 후 이 주소로 복귀 (returnTo) — 같은 주문으로 다시 확인한다
          <p>
            {/* TODO(PD 문구) */}
            <Link href="/login" className="underline">
              다시 로그인해 주세요
            </Link>
          </p>
        ) : (
          <>
            {/* TODO(PD 문구): 실패 원인별 안내 */}
            <p role="alert">충전이 완료되지 않았습니다</p>
            {/* TODO(PD 문구) */}
            <Link href="/wallet" className="underline">
              충전으로 돌아가기
            </Link>
          </>
        )}
      </div>
    </AppShell>
  );
}
