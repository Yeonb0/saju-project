"use client";

import * as Sentry from "@sentry/nextjs";
// "use client" 이유: 견적 조회 · 구매 명령(TanStack Query)과 충전 화면 이동은 브라우저에서 한다.
// 등껍질 차감 확인 팝업 + 잔액 부족 팝업 (CHECKOUT-POPUP, FRONTEND.md 3장 "Checkout — 등껍질 차감").
// 근거: P-06 · P-09 (부족분 · 추천 충전 · 구매 후 잔액은 서버 견적 값만), Q-07 (견적 재확인 · QUOTE_EXPIRED 면 선택 유지 + 새 견적),
// I-05 (구매 의도 = 견적 하나에 키 하나, 재시도에도 같은 키), PURCHASE-RESTORE (충전 가기 전 선택 저장 · 구매 성공 때 삭제),
// COMMON 7장 (409 IDEMPOTENCY_REQUEST_PROCESSING 이면 버튼을 다시 열지 않고 같은 키로 상태 확인), F-08 (결제 버튼 위 고지).
// 앞 화면(CSAT-01 · FORT-02 · 03 · MATCH-03 · FORT-07)이 띄운다. 디자인 요소 없음 (PG-FIRST).
import { useMutation, useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { type ReactNode, useEffect, useRef, useState } from "react";
import { Button } from "@/components/Button";
import { GenerationScene } from "@/components/GenerationScene";
import { Modal } from "@/components/Modal";
import { ApiContractError, classifyApiError } from "@/lib/api/errors";
import { createIdempotencyKey } from "@/lib/api/idempotency";
import { loginHref } from "@/lib/auth/returnTo";
import { getFortunePort, getTopUpPort } from "@/lib/ports";
import type {
  FortunePort,
  FortuneQuote,
  FortuneSelection,
  ReadingPurchaseResult,
} from "@/lib/ports/fortune";
import type { TopUpPort } from "@/lib/ports/topUp";
import {
  clearPurchaseSelection,
  savePurchaseSelection,
} from "@/lib/purchase/restore";
import {
  generationFailureOfError,
  generationFailureOfResult,
} from "@/lib/reading/generation";

const formatNumber = (value: number) => value.toLocaleString("ko-KR");

// 팝업 배치 (LAYOUT-FIGMA, 248:267~271) — 내용 배치만, 표시 항목은 CHECKOUT-POPUP 그대로
// TODO(PD 토큰 v0): 와이어 임시값
const INFO_CLASS = "mt-[19px] text-[16px] leading-[normal]";
const NOTE_CLASS = "my-[8px] text-[16px]";
const ACTIONS_CLASS = "mt-[18px] flex flex-col items-center";

// 한 줄 "이름 : 값" 가운데 정렬. 구분자는 dt 의 ::after 라 화면 글자(dt · dd)는 그대로다
function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex flex-wrap justify-center gap-x-[4px]">
      <dt className="shrink-0 whitespace-nowrap after:ml-[4px] after:content-[':']">
        {label}
      </dt>
      {/* 긴 값은 상자 안에서 줄바꿈한다 */}
      <dd className="min-w-0 [overflow-wrap:anywhere]">{children}</dd>
    </div>
  );
}

// 와이어에 없는 닫기 — 주 버튼 아래 8px 의 글자 버튼
function CloseButton({
  onClick,
  disabled,
}: {
  onClick: () => void;
  disabled?: boolean;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className="mt-[8px] text-[16px] underline disabled:opacity-50"
    >
      {/* TODO(PD 문구) */}
      닫기
    </button>
  );
}

// 추천 충전 상품이 상품 목록에 없거나 비활성일 때의 경고 — 상품 code 만 보낸다 (본문 · 인적정보 금지, CLAUDE.md 관측)
function reportMismatchToSentry(productCode: string) {
  Sentry.captureMessage("recommended top-up not in product list", {
    level: "warning",
    tags: { productCode },
  });
}

type Intent = { quoteId: string; key: string };

// 구매 명령 뒤 다시 받은 견적에 붙이는 안내 (값은 서버 견적, 문구는 PD)
type Notice = "requoted" | null;

export function ShellCheckout({
  open,
  onOpenChange,
  selection,
  targetName,
  optionLabel,
  resumeQuoteId = null,
  returnPath,
  onPurchased,
  port,
  topUpPort,
  reportMismatch = reportMismatchToSentry,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  selection: FortuneSelection;
  // 대상 인물 이름 — 앞 화면이 인물 포트에서 받은 값
  targetName: string;
  // 옵션 이름 — 앞 화면이 정한다. TODO(PD 문구)
  optionLabel: string;
  // 충전 후 복귀(PURCHASE-RESTORE)면 저장해 둔 견적 ID — 새로 만들지 않고 GET /quotes/{quoteId} 로 재확인
  resumeQuoteId?: string | null;
  // 충전 후 돌아올 앞 화면 경로 (safeReturnTo 를 거쳐 저장된다)
  returnPath: string;
  // 구매 응답 그대로 (결과 화면으로 보낼 때). 생성 실패(FAILED · READING_GENERATION_FAILED)는 이 팝업이 안내하고 넘기지 않는다
  onPurchased: (result: ReadingPurchaseResult) => void;
  // 테스트에서 주입한다. 기본값은 포트 선택(src/lib/ports) — 요청할 때 고른다.
  port?: FortunePort;
  topUpPort?: TopUpPort;
  // 테스트에서 주입한다
  reportMismatch?: (productCode: string) => void;
}) {
  const router = useRouter();
  const fortune = () => port ?? getFortunePort();

  // 0 이면 첫 견적(복귀면 재확인), 올라가면 새 견적
  const [round, setRound] = useState(0);
  const [notice, setNotice] = useState<Notice>(null);

  const quote = useQuery({
    queryKey: [
      "fortuneQuote",
      selection.productCode,
      selection.personId,
      selection.counterpartPersonId,
      resumeQuoteId,
      round,
    ],
    queryFn: async (): Promise<FortuneQuote> => {
      if (round === 0 && resumeQuoteId !== null) {
        try {
          return await fortune().getQuote(resumeQuoteId);
        } catch (error) {
          // 만료 · 없는 견적이면 선택은 유지하고 새 견적을 받는다 (Q-07)
          const kind = classifyApiError(error);
          if (kind !== "quote_expired" && kind !== "not_found") throw error;
        }
      }
      return fortune().createQuote(selection);
    },
    enabled: open,
    // 견적은 매번 서버 값을 새로 받는다 — 저장한 값을 표시 근거로 쓰지 않는다
    retry: false,
    staleTime: 0,
    gcTime: 0,
  });

  // 구매 의도는 견적 하나 — 같은 견적으로 다시 누르면 같은 키 (I-05)
  const intent = useRef<Intent | null>(null);
  // 같은 틱의 두 번째 탭은 렌더 전이라 isPending 이 아직 false 다 — ref 로 한 번 더 막는다
  const submitting = useRef(false);

  const purchase = useMutation({
    mutationFn: (next: Intent) =>
      fortune().purchase({ quoteId: next.quoteId, selection }, next.key),
    onSuccess: (result) => {
      // 생성 실패는 팝업 안에서 환급 · 재시도 안내 (F-06 · COMMON 4.8)
      if (generationFailureOfResult(result)) return;
      clearPurchaseSelection();
      onPurchased(result);
    },
    onError: (error) => {
      const kind = classifyApiError(error);
      // 서버가 잔액 · 가격 · 견적이 달라졌다고 하면 새 견적으로 다시 확인받는다 (값은 새 견적)
      if (
        kind === "insufficient_balance" ||
        kind === "quote_expired" ||
        kind === "price_changed"
      ) {
        setNotice("requoted");
        setRound((n) => n + 1);
      }
    },
  });

  // 새 견적을 기다리는 동안에는 지난 견적 값을 보이지 않는다
  const current = quote.isFetching ? null : (quote.data ?? null);
  const insufficient = current !== null && current.shortage > 0;

  // 추천 충전 상품 (P-06 · P-09): 부족하고 서버가 추천 code 를 줬을 때만 상품 목록을 받는다.
  // 추천 code 는 서버 값이고 금액 · 수량은 목록(서버)의 값 그대로 — 화면 계산 없음. 포트는 요청할 때 고른다.
  const recommendedCode = insufficient ? current.recommendedTopUp : null;
  const topUps = useQuery({
    queryKey: ["topUpProducts"],
    queryFn: () => (topUpPort ?? getTopUpPort()).listTopUpProducts(),
    enabled: open && recommendedCode !== null,
  });
  const recommended =
    topUps.data?.find((p) => p.code === recommendedCode && p.active) ?? null;
  const mismatchCode =
    recommendedCode !== null && topUps.data && recommended === null
      ? recommendedCode
      : null;
  useEffect(() => {
    if (mismatchCode !== null) reportMismatch(mismatchCode);
  }, [mismatchCode, reportMismatch]);

  // 견적 조회가 401 이면 오류 화면 대신 팝업 안에서 로그인 안내 (A-03). 그 밖의 실패는 오류 화면으로
  const quoteErrorKind = quote.error ? classifyApiError(quote.error) : null;
  const quoteLoginRequired =
    quoteErrorKind === "login_required" || quoteErrorKind === "csrf_failed";

  // 시끄럽게 실패: 견적 조회 실패 · 충전 상품 조회 실패 · 계약 위반은 오류 화면(error.tsx)으로
  if (quote.error && !quoteLoginRequired) throw quote.error;
  if (topUps.error) throw topUps.error;
  if (purchase.error instanceof ApiContractError) throw purchase.error;

  const errorKind = purchase.error ? classifyApiError(purchase.error) : null;
  const requoting =
    errorKind === "insufficient_balance" ||
    errorKind === "quote_expired" ||
    errorKind === "price_changed";
  // 결과를 아직 모른다 — 버튼을 다시 열지 않고 같은 키로 확인만 한다
  const outcomePending =
    errorKind === "request_processing" || errorKind === "outcome_unknown";

  function send(current: FortuneQuote) {
    if (purchase.isPending || submitting.current) return;
    if (intent.current?.quoteId !== current.quoteId) {
      intent.current = {
        quoteId: current.quoteId,
        key: createIdempotencyKey(),
      };
    }
    submitting.current = true;
    purchase.mutate(intent.current, {
      onSettled: () => {
        submitting.current = false;
      },
    });
  }

  function onConfirm(current: FortuneQuote) {
    if (outcomePending) return;
    setNotice(null);
    send(current);
  }

  function onTopUp(current: FortuneQuote) {
    savePurchaseSelection({
      returnPath,
      quoteId: current.quoteId,
      selection,
    });
    router.push("/wallet");
  }

  const failure = purchase.data
    ? generationFailureOfResult(purchase.data)
    : purchase.error
      ? generationFailureOfError(purchase.error)
      : null;

  // 생성이 끝난 구매는 다시 보내지 않는다 — 다시 시도는 새 견적(새 구매 의도 · 새 키)으로
  function retryAfterFailure() {
    purchase.reset();
    setNotice(null);
    setRound((n) => n + 1);
  }

  return (
    <Modal
      open={open}
      onOpenChange={(next) => {
        // 구매 명령이 나간 동안에는 닫지 않는다 — 결과를 놓치지 않게
        if (!next && (purchase.isPending || outcomePending)) return;
        onOpenChange(next);
      }}
      // TODO(PD 문구): 제목 — 잔액 부족이면 다른 제목
      title={insufficient ? "등껍질 부족" : "등껍질 사용 확인"}
    >
      {purchase.isPending ? (
        // 생성은 구매 요청 안에서 끝난다 (F-06) — 기다리는 동안 대기 장면
        <GenerationScene failure={null} />
      ) : failure ? (
        <GenerationScene
          failure={failure}
          actions={
            <div className={ACTIONS_CLASS}>
              <Button variant="popup" onClick={retryAfterFailure}>
                {/* TODO(PD 문구) */}
                다시 시도
              </Button>
              <CloseButton onClick={() => onOpenChange(false)} />
            </div>
          }
        />
      ) : quoteLoginRequired ? (
        <>
          <p className={NOTE_CLASS}>
            {/* TODO(PD 문구) */}
            <Link href={loginHref(returnPath)}>다시 로그인해 주세요</Link>
          </p>
          <div className={ACTIONS_CLASS}>
            <CloseButton onClick={() => onOpenChange(false)} />
          </div>
        </>
      ) : !current ? (
        // TODO(PD 문구)
        <output className={`block ${NOTE_CLASS}`}>확인하고 있습니다</output>
      ) : insufficient ? (
        <>
          {/* 서버 견적 값만 표시한다 (P-06 · P-09). TODO(PD 문구): 항목 이름 · 단위 */}
          <dl className={INFO_CLASS}>
            <Row label="보유">{formatNumber(current.walletBalance)}</Row>
            <Row label="부족">{formatNumber(current.shortage)}</Row>
            {recommended ? (
              <>
                {/* TODO(PD 문구): 항목 이름 · 단위 */}
                <Row label="추천 충전">
                  {formatNumber(recommended.price.amount)}{" "}
                  {recommended.price.currency}
                </Row>
                {/* TODO(PD 문구): 항목 이름 · 단위 */}
                <Row label="받는 수">
                  {formatNumber(recommended.creditedAmount)}
                </Row>
              </>
            ) : null}
          </dl>
          {notice === "requoted" ? (
            // TODO(PD 문구)
            <p role="alert" className={NOTE_CLASS}>
              잔액이 바뀌었습니다
            </p>
          ) : null}
          <div className={ACTIONS_CLASS}>
            <Button variant="popup" onClick={() => onTopUp(current)}>
              {/* TODO(PD 문구) */}
              충전하기
            </Button>
            <CloseButton onClick={() => onOpenChange(false)} />
          </div>
        </>
      ) : (
        <>
          {/* 서버 견적 값만 표시한다 (P-03 · P-09). TODO(PD 문구): 항목 이름 · 단위 */}
          <dl className={INFO_CLASS}>
            <Row label="상품">{current.productName}</Row>
            <Row label="대상">{targetName}</Row>
            <Row label="옵션">{optionLabel}</Row>
            <Row label="보유">{formatNumber(current.walletBalance)}</Row>
            <Row label="사용">
              {formatNumber(current.price.amount)} {current.price.currency}
            </Row>
            {current.balanceAfter !== null ? (
              <Row label="구매 후">{formatNumber(current.balanceAfter)}</Row>
            ) : null}
          </dl>
          {notice === "requoted" ? (
            // TODO(PD 문구): 가격 · 잔액이 바뀌어 다시 확인받는 안내
            <p role="alert" className={NOTE_CLASS}>
              내용이 바뀌었습니다. 다시 확인해 주세요
            </p>
          ) : null}
          {/* TODO(F-08 · PD 문구): 결제 버튼 위 재미 · 참고용 콘텐츠 고지 */}
          <p data-slot="disclaimer" className={NOTE_CLASS} />
          {outcomePending ? (
            <>
              {/* TODO(PD 문구) */}
              <output className={`block ${NOTE_CLASS}`}>처리 중입니다</output>
              {/* 같은 키 · 같은 본문으로 다시 보내 결과를 확인한다 (I-05) */}
              <div className="flex justify-center">
                <Button
                  variant="popup"
                  onClick={() => send(current)}
                  disabled={purchase.isPending}
                >
                  {/* TODO(PD 문구) */}
                  결과 확인
                </Button>
              </div>
            </>
          ) : errorKind === "login_required" || errorKind === "csrf_failed" ? (
            <p className={NOTE_CLASS}>
              {/* TODO(PD 문구) */}
              <Link href={loginHref(returnPath)}>다시 로그인해 주세요</Link>
            </p>
          ) : errorKind !== null && !requoting && failure === null ? (
            // TODO(PD 문구): 오류 종류별 안내
            <p role="alert" className={NOTE_CLASS}>
              구매하지 못했습니다
            </p>
          ) : null}
          <div className={ACTIONS_CLASS}>
            <Button
              variant="popup"
              onClick={() => onConfirm(current)}
              disabled={
                purchase.isPending || outcomePending || purchase.isSuccess
              }
            >
              {/* TODO(PD 문구) */}
              사용하기
            </Button>
            <CloseButton
              onClick={() => onOpenChange(false)}
              disabled={purchase.isPending || outcomePending}
            />
          </div>
        </>
      )}
    </Modal>
  );
}
