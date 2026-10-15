"use client";

// "use client" 이유: 견적 조회 · 주문 생성(TanStack Query)과 결제창 열기, 동의 체크 상태는 브라우저에서 한다.
// 결제하기 팝업 — 운세 한 건을 원화로 바로 결제한다 (DIRECT-PAY, 2026-10-16 사용자 결정. 등껍질 차감 팝업 ShellCheckout 을 대체).
// 이 팝업은 견적 → 주문 내용 · 안내 · 필수 동의 → 주문 생성 → 결제창 열기까지다. 결제창 복귀(/pay/success · /pay/fail)와
// 결제 뒤 운세 생성은 복귀 화면이 한다 — 이 팝업은 지갑 · 잔액 · 충전 · 구매(purchase) · 구매 상태 조회 · 결과 이동을 하지 않는다.
// 근거: P-09 (표시 값은 서버 견적 charged 값 그대로 — 화면이 금액을 계산하지 않는다), Q-07 (견적 재확인 · QUOTE_EXPIRED 면 선택 유지 + 새 견적),
// I-05 (구매 의도 = 견적 하나에 키 하나, 재시도에도 같은 키 — 결제창을 닫고 다시 눌러도 서버가 같은 주문을 준다),
// PURCHASE-RESTORE (주문 생성 전에 복원 저장 → 새로고침 · 복귀에도 같은 키로 이어 간다. 저장값은 키 · 주문 ID · 견적 ID · 선택만),
// CHECKOUT-POPUP (이 자리를 앞 화면 CSAT-01 · FORT-02 · 03 · MATCH-03 이 띄운다), 정책 v4 환불정책 3 · 4 · 5 · 8조 (결제 전 결제 화면 안내),
// BE 전달본 10/16 (주문 status 가 PAYMENT_PENDING 일 때만 결제창을 연다 · 이미 결제했을 수 있는 주문은 다시 결제하지 않는다).
// 디자인 요소 없음 (PG-FIRST).
import { useMutation, useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { type ReactNode, useRef, useState } from "react";
import { Button } from "@/components/Button";
import { Modal } from "@/components/Modal";
import { ApiContractError, classifyApiError } from "@/lib/api/errors";
import { createIdempotencyKey } from "@/lib/api/idempotency";
import { loginHref } from "@/lib/auth/returnTo";
import { BUSINESS_INFO } from "@/lib/business";
import {
  getDirectPaymentPort,
  getFortunePort,
  getPaymentLauncher,
} from "@/lib/ports";
import type { DirectPaymentPort } from "@/lib/ports/directPayment";
import type {
  FortunePort,
  FortuneQuote,
  FortuneSelection,
} from "@/lib/ports/fortune";
import {
  PaymentCanceledError,
  type PaymentLauncher,
} from "@/lib/ports/paymentLauncher";
import { savePurchaseSelection } from "@/lib/purchase/restore";
import { supportMailtoHref } from "@/lib/support/contact";

const formatNumber = (value: number) => value.toLocaleString("ko-KR");

// 정책 v4 3 · 4 · 5 · 8조 문장 그대로 — TODO(PD 문구) 화면용 말투. 바꿀 때는 이 배열 한 곳.
const DIRECT_NOTICE_LINES = [
  "결제 승인이 확인되면 운세와 부적을 만들어 화면으로 제공합니다.",
  "결제 후 7일 이내이고, 완성된 운세 결과 또는 부적을 아직 받지 않았다면 고객센터로 신청해 결제를 취소할 수 있습니다.",
  "완성된 운세 결과를 확인했거나 부적이 지급되는 등 디지털 콘텐츠 제공이 시작된 뒤에는 단순 변심으로 취소 · 환불할 수 없습니다.",
  "미성년자는 법정대리인의 동의를 받아 결제해 주세요.",
] as const;

// 팝업 배치 (LAYOUT-FIGMA, 248:267~271) — 내용 배치만
// TODO(PD 토큰 v0): 와이어 임시값
const INFO_CLASS = "mt-[19px] text-[16px] leading-[normal]";
const NOTE_CLASS = "my-[8px] text-[16px]";
const ACTIONS_CLASS = "mt-[18px] flex flex-col items-center";
const LINK_CLASS = "mt-[8px] text-[16px] underline";

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

// 확인 필요 블록 (COMMON-04 와 같은 문구) — 이미 결제했을 수 있는 주문이라 결제 버튼을 두지 않는다. 문의하기는 메일 앱 (Q-38 g · Q-47)
function ReviewBlock({
  orderId,
  onClose,
}: {
  orderId: string;
  onClose: () => void;
}) {
  const href = supportMailtoHref({ kind: "paymentOrder", id: orderId });
  return (
    <>
      <div className={NOTE_CLASS}>
        <p role="alert">확인이 필요한 구매예요</p>
        <p>고객센터로 문의해 주시면 빠르게 도와드릴게요.</p>
        {BUSINESS_INFO.email === null ? null : <p>{BUSINESS_INFO.email}</p>}
        {href === null ? null : (
          <a href={href} className={LINK_CLASS}>
            문의하기
          </a>
        )}
      </div>
      <div className={ACTIONS_CLASS}>
        <CloseButton onClick={onClose} />
      </div>
    </>
  );
}

type Intent = { quoteId: string; key: string; orderId: string | null };

// 결제하기를 누른 결과. review = 주문이 PAYMENT_PENDING 이 아니라 결제창을 열지 않았다 (BE 전달본 10/16)
type PayResult = { kind: "launched" } | { kind: "review"; orderId: string };

export function DirectCheckout({
  open,
  onOpenChange,
  selection,
  targetName,
  optionLabel,
  resumeQuoteId = null,
  resumeOrder = null,
  returnPath,
  fortunePort,
  directPort,
  launcher,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  selection: FortuneSelection;
  // 대상 인물 이름 — 앞 화면이 인물 포트에서 받은 값
  targetName: string;
  // 옵션 이름 — 앞 화면이 정한다. TODO(PD 문구)
  optionLabel: string;
  // 결제창에서 돌아온 뒤(PURCHASE-RESTORE) 저장해 둔 견적 ID — 새로 만들지 않고 GET /quotes/{quoteId} 로 재확인
  resumeQuoteId?: string | null;
  // 저장해 둔 주문 생성 키 · 서버 주문 ID. orderId 가 있으면 결제 버튼을 보이기 전에 주문 상태를 한 번 조회한다
  resumeOrder?: Readonly<{ orderKey: string; orderId: string | null }> | null;
  // 결제창에서 돌아올 앞 화면 경로 (safeReturnTo 를 거쳐 저장된다)
  returnPath: string;
  // 테스트에서 주입한다. 기본값은 포트 선택(src/lib/ports) — 요청할 때 고른다 (MOCK-PORT).
  fortunePort?: FortunePort;
  directPort?: DirectPaymentPort;
  launcher?: PaymentLauncher;
}) {
  const router = useRouter();
  const fortune = () => fortunePort ?? getFortunePort();
  const direct = () => directPort ?? getDirectPaymentPort();
  const launch = () =>
    launcher ?? getPaymentLauncher((url) => router.push(url));

  // 0 이면 첫 견적(복귀면 재확인), 올라가면 새 견적
  const [round, setRound] = useState(0);
  const [requoted, setRequoted] = useState(false);
  const [agreed, setAgreed] = useState(false);
  // 결제창을 열지 않고 확인 필요 블록을 보이는 주문 ID (주문 생성 응답이 PAYMENT_PENDING 이 아닐 때)
  const [reviewOrderId, setReviewOrderId] = useState<string | null>(null);

  // 재개: 저장해 둔 주문이 있으면 결제 버튼을 보이기 전에 상태를 한 번 본다. 이미 결제했을 수 있는 주문을 다시 결제하지 않는다
  const resumeOrderId = resumeOrder?.orderId ?? null;
  const lookup = useQuery({
    queryKey: ["directResumeOrder", resumeOrderId],
    queryFn: () => direct().getOrder(resumeOrderId as string),
    enabled: open && resumeOrderId !== null,
    retry: false,
    staleTime: 0,
    gcTime: 0,
    refetchOnWindowFocus: false,
  });
  const lookupKind = lookup.error ? classifyApiError(lookup.error) : null;
  const lookupLoginRequired =
    lookupKind === "login_required" || lookupKind === "csrf_failed";
  // 없는 주문(404)은 새 의도로 본다. 그 밖의 실패는 오류 화면(error.tsx)으로
  if (lookup.error && !lookupLoginRequired && lookupKind !== "not_found") {
    throw lookup.error;
  }

  type ResumeMode = "none" | "checking" | "continue" | "fresh" | "review";
  let resumeMode: ResumeMode = "none";
  if (resumeOrder !== null) {
    if (resumeOrderId === null) {
      // 주문 ID 가 없다 = 주문이 서버에 닿았는지 모른다. 조회 없이 저장한 키로 이어 간다 (같은 키면 서버가 같은 주문을 준다)
      resumeMode = "continue";
    } else if (lookup.data) {
      const status = lookup.data.status;
      resumeMode =
        status === "PAYMENT_PENDING"
          ? "continue"
          : status === "FAILED" || status === "CANCELED"
            ? "fresh"
            : "review"; // PAID · REVIEW_REQUIRED · UNKNOWN
    } else if (lookupKind === "not_found") {
      resumeMode = "fresh";
    } else if (!lookupLoginRequired) {
      resumeMode = "checking";
    }
  }
  const quoteEnabled =
    open &&
    !lookupLoginRequired &&
    resumeMode !== "checking" &&
    resumeMode !== "review";

  const quote = useQuery({
    queryKey: [
      "directQuote",
      selection.fortuneType,
      selection.productCode,
      selection.personId,
      selection.counterpartPersonId,
      // 질문 · 옵션 · 관계를 바꾸고 팝업을 다시 열면 새 견적을 받는다 (이전 질문의 견적을 재사용하지 않는다)
      selection.productOption,
      selection.questionKey,
      selection.relationType,
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
    enabled: quoteEnabled,
    // 견적은 매번 서버 값을 새로 받는다 — 저장한 값을 표시 근거로 쓰지 않는다
    retry: false,
    staleTime: 0,
    gcTime: 0,
  });

  // 구매 의도는 견적 하나 — 같은 견적으로 다시 누르면 같은 키 (I-05)
  const intent = useRef<Intent | null>(null);
  // 같은 틱의 두 번째 탭은 렌더 전이라 isPending 이 아직 false 다 — ref 로 한 번 더 막는다
  const submitting = useRef(false);

  const pay = useMutation({
    mutationFn: async (next: Intent): Promise<PayResult> => {
      try {
        const order = await direct().createOrder(
          { quoteId: next.quoteId },
          next.key,
        );
        next.orderId = order.orderId;
        // 서버 주문 ID 를 복원 저장에 더한다 — 결제창을 연 뒤 새로고침 · 복귀에도 이 주문으로 이어 간다
        savePurchaseSelection({
          returnPath,
          quoteId: next.quoteId,
          selection,
          orderKey: next.key,
          orderId: order.orderId,
        });
        if (order.status !== "PAYMENT_PENDING") {
          return { kind: "review", orderId: order.orderId };
        }
        await launch().requestPayment(order, { kind: "direct", returnPath });
        return { kind: "launched" };
      } finally {
        // onError 에서 reset 하면 mutate 단위 콜백이 사라지므로 여기서 푼다
        submitting.current = false;
      }
    },
    onSuccess: (result) => {
      if (result.kind === "review") setReviewOrderId(result.orderId);
    },
    onError: (error) => {
      // 구매자가 결제창을 닫음 — 안내 없이 버튼을 다시 연다. intent(키)는 그대로 둔다 (I-05)
      if (error instanceof PaymentCanceledError) {
        pay.reset();
        return;
      }
      const kind = classifyApiError(error);
      // 견적이 만료됐다 — 선택은 유지하고 새 견적을 받는다 (새 견적은 새 키)
      if (kind === "quote_expired") {
        pay.reset();
        setRequoted(true);
        setRound((n) => n + 1);
        return;
      }
      // 결과를 모르는 오류 — 키를 바꾸지 않고 버튼만 다시 연다. 다시 누르면 같은 키라 서버가 같은 주문을 준다 (I-05)
      if (kind === "request_processing" || kind === "outcome_unknown") {
        pay.reset();
      }
    },
  });

  // 시끄럽게 실패: 견적 조회 실패 · 계약 위반은 오류 화면(error.tsx)으로. 견적 401 은 팝업 안에서 로그인 안내 (A-03)
  const quoteErrorKind = quote.error ? classifyApiError(quote.error) : null;
  const quoteLoginRequired =
    quoteErrorKind === "login_required" || quoteErrorKind === "csrf_failed";
  if (quote.error && !quoteLoginRequired) throw quote.error;
  if (pay.error instanceof ApiContractError) throw pay.error;

  // 새 견적을 기다리는 동안에는 지난 견적 값을 보이지 않는다
  const current = quote.isFetching ? null : (quote.data ?? null);
  if (current !== null && current.charged.currency !== "KRW") {
    throw new Error("직접결제 견적이 KRW 가 아니다 (DIRECT-PAY)");
  }

  const payErrorKind = pay.error ? classifyApiError(pay.error) : null;
  const payLoginRequired =
    payErrorKind === "login_required" || payErrorKind === "csrf_failed";
  const busy = pay.isPending;

  function onPay(quoteNow: FortuneQuote) {
    // 동의 없이는 아무것도 보내지 않는다 (버튼 비활성과 이중 방어)
    if (!agreed || busy || pay.isSuccess || submitting.current) return;
    if (intent.current?.quoteId !== quoteNow.quoteId) {
      // 저장해 둔 주문을 이어 가는 경우(아직 결제 전) 그 키를 쓴다. 그 밖은 새 키 (I-05)
      const resumeKey =
        resumeOrder !== null &&
        round === 0 &&
        resumeMode === "continue" &&
        quoteNow.quoteId === resumeQuoteId
          ? resumeOrder.orderKey
          : createIdempotencyKey();
      intent.current = {
        quoteId: quoteNow.quoteId,
        key: resumeKey,
        orderId:
          resumeKey === resumeOrder?.orderKey
            ? (resumeOrder?.orderId ?? null)
            : null,
      };
    }
    submitting.current = true;
    setRequoted(false);
    // 주문을 만들기 전에 견적 ID 와 주문 키만 저장한다 — 결과를 모른 채 닫히거나 새로고침돼도 같은 키로 이어 간다 (PURCHASE-RESTORE)
    savePurchaseSelection({
      returnPath,
      quoteId: intent.current.quoteId,
      selection,
      orderKey: intent.current.key,
      orderId: intent.current.orderId,
    });
    pay.mutate(intent.current);
  }

  const reviewId = resumeMode === "review" ? resumeOrderId : reviewOrderId;

  return (
    <Modal
      open={open}
      onOpenChange={(next) => {
        // 주문 생성 · 결제창을 여는 동안에는 닫지 않는다 — 결과를 놓치지 않게
        if (!next && busy) return;
        onOpenChange(next);
      }}
      layout="popup"
      // TODO(PD 문구)
      title="결제하기"
    >
      {reviewId !== null ? (
        <ReviewBlock orderId={reviewId} onClose={() => onOpenChange(false)} />
      ) : quoteLoginRequired || lookupLoginRequired || payLoginRequired ? (
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
            <Row label="결제 금액">
              {formatNumber(current.charged.amount)}원
            </Row>
          </dl>
          {requoted ? (
            // TODO(PD 문구): 견적이 바뀌어 다시 확인받는 안내
            <p role="alert" className={NOTE_CLASS}>
              내용이 바뀌었습니다. 다시 확인해 주세요
            </p>
          ) : null}
          <div
            data-slot="direct-notice"
            className="mt-[12px] rounded-[8px] bg-[#f2f2f2] px-[12px] py-[14px] text-left text-[13px] font-medium leading-[normal]"
          >
            <ul>
              {DIRECT_NOTICE_LINES.map((line) => (
                <li key={line} className="before:content-['·_']">
                  {line}
                </li>
              ))}
            </ul>
            <p className="before:content-['·_']">
              <Link href="/refund">환불정책 보기 &gt;</Link>
            </p>
          </div>
          {/* 필수 동의 — 토스 결제창 안의 동의(결제 서비스 약관)와 다르다: 디지털 콘텐츠 청약철회 제한 · 환불정책 고지 (충전 화면과 같은 문장) */}
          <label className="mt-[12px] flex gap-[7px] text-left">
            <input
              type="checkbox"
              checked={agreed}
              onChange={(event) => setAgreed(event.target.checked)}
              className="h-[24px] w-[24px] shrink-0"
            />
            <span className="block text-[14px] font-medium leading-[normal]">
              주문 내용과 환불정책을 확인했으며 결제에 동의합니다. (필수)
            </span>
          </label>
          {pay.isError ? (
            // TODO(PD 문구): 오류 종류별 안내
            <p role="alert" className={NOTE_CLASS}>
              결제하지 못했습니다
            </p>
          ) : null}
          <div className={ACTIONS_CLASS}>
            <Button
              variant="popup"
              onClick={() => onPay(current)}
              disabled={!agreed || busy || pay.isSuccess}
            >
              {formatNumber(current.charged.amount)}원 결제하기
            </Button>
            <CloseButton onClick={() => onOpenChange(false)} disabled={busy} />
          </div>
        </>
      )}
    </Modal>
  );
}
