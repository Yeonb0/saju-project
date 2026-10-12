"use client";

import * as Sentry from "@sentry/nextjs";
// "use client" 이유: 견적 조회 · 구매 명령(TanStack Query)과 충전 화면 이동은 브라우저에서 한다.
// 등껍질 차감 확인 팝업 + 잔액 부족 팝업 (CHECKOUT-POPUP, FRONTEND.md 3장 "Checkout — 등껍질 차감").
// 근거: P-06 · P-09 (부족분 · 추천 충전 · 구매 후 잔액은 서버 견적 값만), Q-07 (견적 재확인 · QUOTE_EXPIRED 면 선택 유지 + 새 견적),
// I-05 (구매 의도 = 견적 하나에 키 하나, 재시도에도 같은 키), PURCHASE-RESTORE (충전 가기 전 선택 저장 · 구매 성공 때 삭제),
// COMMON 7장 (409 IDEMPOTENCY_REQUEST_PROCESSING 이면 버튼을 다시 열지 않고 상태 확인), F-08 (결제 버튼 위 고지),
// Q-38 답 (API_SPEC 8장 상태표 — 화면 분기는 status, 버튼 · 안내는 nextAction), PURCHASE-STATUS-WAIT 해제,
// 결정 PURCHASE-STATUS-UI (2026-10-12, 사용자 — 처리 중 자동 반복 조회 없음, 결과 확인은 상태 조회), F-06, COMMON 4.8.
// 앞 화면(CSAT-01 · FORT-02 · 03 · MATCH-03 · FORT-07)이 띄운다. 디자인 요소 없음 (PG-FIRST).
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
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
  ReadingPurchaseState,
} from "@/lib/ports/fortune";
import type { TopUpPort } from "@/lib/ports/topUp";
import {
  clearPurchaseSelection,
  savePurchaseSelection,
} from "@/lib/purchase/restore";
import {
  isGenerationFailureError,
  purchaseOutcomeOf,
} from "@/lib/reading/generation";
import { WALLET_QUERY_KEY } from "@/lib/wallet/query";

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
  // 서버 상태가 완료(FULFILLED + readingId)일 때 결과 ID 만 넘긴다 (구매 응답이든 상태 조회든). 생성 실패(FAILED · REFUNDED)와
  // 결과를 아직 모르는 상태(처리 중 등)는 이 팝업이 안내하고 넘기지 않는다 (Q-38)
  onPurchased: (fulfilled: Readonly<{ readingId: string }>) => void;
  // 테스트에서 주입한다. 기본값은 포트 선택(src/lib/ports) — 요청할 때 고른다.
  port?: FortunePort;
  topUpPort?: TopUpPort;
  // 테스트에서 주입한다
  reportMismatch?: (productCode: string) => void;
}) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const fortune = () => port ?? getFortunePort();

  // 0 이면 첫 견적(복귀면 재확인), 올라가면 새 견적
  const [round, setRound] = useState(0);
  const [notice, setNotice] = useState<Notice>(null);

  const quote = useQuery({
    queryKey: [
      "fortuneQuote",
      selection.fortuneType,
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

  // 이번 구매 의도에서 마지막으로 안 서버 상태 (구매 응답 또는 상태 조회). 화면은 이 값의 status · nextAction 으로 가른다 (Q-38)
  const [known, setKnown] = useState<ReadingPurchaseState | null>(null);
  const outcome = known ? purchaseOutcomeOf(known) : null;
  const delivered = useRef(false);

  // 구매 상태 조회 (GET by-quote). auto = 오류 직후 한 번 자동으로 부른 조회 (PURCHASE-STATUS-UI: 자동 반복 없음)
  const lookup = useMutation({
    mutationFn: (v: { quoteId: string; auto: boolean }) =>
      fortune().getPurchaseStatus(v.quoteId),
    onSuccess: (state, v) => {
      if (state) {
        setKnown(state);
        queryClient.invalidateQueries({ queryKey: WALLET_QUERY_KEY });
        return;
      }
      // 서버에 이 견적의 구매 기록이 없다. 자동 조회면 처리 중 + 결과 확인을 그대로 둔다 (반복 방지).
      // 사용자가 누른 결과 확인이면 같은 의도(같은 견적 · 같은 키)로 다시 보낸다 — 기록이 없어 이중 차감이 없다 (I-05)
      if (!v.auto) send(v.quoteId);
    },
  });

  const purchase = useMutation({
    mutationFn: (next: Intent) =>
      fortune().purchase({ quoteId: next.quoteId, selection }, next.key),
    onSuccess: (result) => {
      // 구매 요청이 끝났다 — 잔액은 서버에서 다시 받는다 (생성 실패 환급 포함, P-09 · Q-22: 클라이언트에서 차감 · 합산하지 않는다)
      queryClient.invalidateQueries({ queryKey: WALLET_QUERY_KEY });
      setKnown(result);
    },
    onError: (error, next) => {
      const kind = classifyApiError(error);
      // 서버가 잔액 · 가격 · 견적이 달라졌다고 하면 새 견적으로 다시 확인받는다 (값은 새 견적)
      if (
        kind === "insufficient_balance" ||
        kind === "quote_expired" ||
        kind === "price_changed"
      ) {
        setNotice("requoted");
        setRound((n) => n + 1);
        return;
      }
      // 결과를 모르는 오류는 서버 상태를 한 번 조회한다. 생성 실패 오류도 환급 여부 · 다음 행동은 상태로 안다 (Q-38)
      if (
        kind === "request_processing" ||
        kind === "outcome_unknown" ||
        isGenerationFailureError(error)
      ) {
        lookup.mutate({ quoteId: next.quoteId, auto: true });
      }
    },
  });

  // 서버 상태가 완료가 되면 한 번만 결과를 넘긴다
  const fulfilledReadingId =
    outcome?.kind === "fulfilled" ? outcome.readingId : null;
  useEffect(() => {
    if (fulfilledReadingId === null || delivered.current) return;
    delivered.current = true;
    clearPurchaseSelection();
    onPurchased({ readingId: fulfilledReadingId });
  }, [fulfilledReadingId, onPurchased]);

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
  if (lookup.error instanceof ApiContractError) throw lookup.error;

  const errorKind = purchase.error ? classifyApiError(purchase.error) : null;
  const requoting =
    errorKind === "insufficient_balance" ||
    errorKind === "quote_expired" ||
    errorKind === "price_changed";
  const lookupKind = lookup.error ? classifyApiError(lookup.error) : null;
  const lookupLoginRequired =
    lookupKind === "login_required" || lookupKind === "csrf_failed";
  // 결과를 아직 모른다 — 버튼을 다시 열지 않고 상태 확인만 한다. 서버 상태가 처리 중이거나(GENERATING 등, FULFILLED 인데 readingId null,
  // 보상 진행 중), 상태를 아직 못 안 채 결과 불명 오류 · 생성 실패 오류가 났을 때 (Q-38)
  const outcomePending =
    outcome?.kind === "pending" ||
    (known === null &&
      (errorKind === "request_processing" ||
        errorKind === "outcome_unknown" ||
        isGenerationFailureError(purchase.error)));
  const busy = purchase.isPending || lookup.isPending;

  function send(quoteId: string) {
    if (purchase.isPending || submitting.current) return;
    if (intent.current?.quoteId !== quoteId) {
      intent.current = { quoteId, key: createIdempotencyKey() };
    }
    submitting.current = true;
    purchase.mutate(intent.current, {
      onSettled: () => {
        submitting.current = false;
      },
    });
  }

  function onConfirm(current: FortuneQuote) {
    if (outcomePending || busy) return;
    setNotice(null);
    send(current.quoteId);
  }

  function onTopUp(current: FortuneQuote) {
    savePurchaseSelection({
      returnPath,
      quoteId: current.quoteId,
      selection,
    });
    router.push("/wallet");
  }

  // 사용자가 누른 결과 확인 — 상태 조회, 기록이 없으면 같은 키로 재전송 (lookup.onSuccess)
  function onCheckResult() {
    if (busy || intent.current === null) return;
    lookup.mutate({ quoteId: intent.current.quoteId, auto: false });
  }

  const failure = outcome?.kind === "failed" ? outcome.failure : null;

  // 생성이 끝난 구매는 다시 보내지 않는다 — 다시 시도는 새 견적(새 구매 의도 · 새 키)으로
  function retryAfterFailure() {
    purchase.reset();
    lookup.reset();
    setKnown(null);
    delivered.current = false;
    setNotice(null);
    setRound((n) => n + 1);
  }

  return (
    <Modal
      open={open}
      onOpenChange={(next) => {
        // 구매 명령이 나간 동안에는 닫지 않는다 — 결과를 놓치지 않게
        if (!next && (busy || outcomePending)) return;
        onOpenChange(next);
      }}
      layout={purchase.isPending ? "screen" : "popup"}
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
              {failure.next === "retry" ? (
                <Button variant="popup" onClick={retryAfterFailure}>
                  {/* TODO(PD 문구) */}
                  다시 시도
                </Button>
              ) : null}
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
            {/* 서버 견적의 productName (Q-28 해결, PR #19). null 은 가짜 · 옛 응답 대비 — 줄을 그리지 않는다 */}
            {current.productName !== null ? (
              <Row label="상품">{current.productName}</Row>
            ) : null}
            <Row label="대상">{targetName}</Row>
            <Row label="옵션">{optionLabel}</Row>
            <Row label="보유">{formatNumber(current.walletBalance)}</Row>
            <Row label="사용">
              {formatNumber(current.charged.amount)} {current.charged.currency}
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
              {lookupLoginRequired ? (
                <p className={NOTE_CLASS}>
                  {/* TODO(PD 문구) */}
                  <Link href={loginHref(returnPath)}>다시 로그인해 주세요</Link>
                </p>
              ) : lookup.isError ? (
                // TODO(PD 문구)
                <p role="alert" className={NOTE_CLASS}>
                  확인하지 못했습니다. 다시 확인해 주세요
                </p>
              ) : null}
              {/* 상태 조회 → 서버에 기록이 없으면 같은 키 · 같은 본문으로 다시 보낸다 (I-05, PURCHASE-STATUS-UI) */}
              <div className="flex justify-center">
                <Button variant="popup" onClick={onCheckResult} disabled={busy}>
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
          ) : errorKind !== null && !requoting ? (
            // TODO(PD 문구): 오류 종류별 안내
            <p role="alert" className={NOTE_CLASS}>
              구매하지 못했습니다
            </p>
          ) : null}
          <div className={ACTIONS_CLASS}>
            <Button
              variant="popup"
              onClick={() => onConfirm(current)}
              disabled={busy || outcomePending || known !== null}
            >
              {/* TODO(PD 문구) */}
              사용하기
            </Button>
            <CloseButton
              onClick={() => onOpenChange(false)}
              disabled={busy || outcomePending}
            />
          </div>
        </>
      )}
    </Modal>
  );
}
