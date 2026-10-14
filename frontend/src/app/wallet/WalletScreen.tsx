"use client";

// "use client" 이유: 포트 호출(TanStack Query)과 결제창 이동은 브라우저에서 한다.
// PAY-01 등껍질 충전 (수정안 301:128) · PG-3. 근거: TOPUP-DONE, I-05 (구매 의도당 키 하나), P-03 · P-09 (서버 값만 표시),
// docs/FRONTEND.md 1-2 (MOCK-PORT). 디자인 요소 없음 (PG-FIRST) — 기본 HTML 요소의 최소 레이아웃만.
// 로그인 가드는 page.tsx 의 RequireSession (PG-2 · A-02 · A-03).
import { useMutation, useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { AppShell } from "@/components/AppShell";
import { Button } from "@/components/Button";
import { ApiContractError, classifyApiError } from "@/lib/api/errors";
import { createIdempotencyKey } from "@/lib/api/idempotency";
import { loginHref } from "@/lib/auth/returnTo";
import { BUSINESS_INFO } from "@/lib/business";
import { getFortunePort, getPaymentLauncher, getTopUpPort } from "@/lib/ports";
import type { FortunePort } from "@/lib/ports/fortune";
import {
  PaymentCanceledError,
  type PaymentLauncher,
} from "@/lib/ports/paymentLauncher";
import type { TopUpPort } from "@/lib/ports/topUp";
import { loadPurchaseSelection } from "@/lib/purchase/restore";
import { supportMailtoHref } from "@/lib/support/contact";
import { WALLET_QUERY_KEY } from "@/lib/wallet/query";

type Intent = { productCode: string; key: string };

const formatNumber = (value: number) => value.toLocaleString("ko-KR");

// 안내 상자 문장 (FUNCTIONAL_SPEC 7장 PAY-01 v0.4). 바꿀 때는 이 배열 한 곳.
// 첫 줄: Q-56 — 정책 v3 에서 '승인 확인 후 지급' 으로 바뀔 수 있다 (BE-A 요청)
const TOP_UP_NOTICE_LINES = [
  "등껍질은 결제 승인 즉시 지급돼요. 유효기간은 유료 5년, 보너스 1년이에요.",
  "유효기간이 먼저 끝나는 등껍질부터 사용돼요.",
  "충전 후 7일 이내, 그 충전의 등껍질을 하나도 쓰지 않았다면 고객센터로 신청해 전액 환불받을 수 있어요.",
  "남은 유료 등껍질 환불도 고객센터 이메일로 신청해 주세요.",
  "미성년자는 법정대리인 동의를 받아 결제해 주세요. 동의 없이 결제하면 취소될 수 있어요.",
] as const;

export function WalletScreen({
  port,
  launcher,
  fortunePort,
  retryProductCode = null,
}: {
  // 테스트에서 주입한다. 기본값은 포트 선택(src/lib/ports) — 진짜 구현이 없으면 던져 오류 화면으로 간다.
  // 포트는 렌더 중이 아니라 요청할 때 고른다: 빌드의 정적 렌더에서 진짜 모드 오류로 빌드가 멈추지 않게.
  port?: TopUpPort;
  launcher?: PaymentLauncher;
  fortunePort?: FortunePort;
  // 결제 실패 · 취소 뒤 다시 결제하기가 넘긴 상품 코드 (TOPUP-RETRY-URL, FUNCTIONAL_SPEC 7장). 모양만 검사된 값 — 판매 중일 때만 미리 고른다
  retryProductCode?: string | null;
}) {
  const router = useRouter();
  const topUp = () => port ?? getTopUpPort();
  const pay = () => launcher ?? getPaymentLauncher((url) => router.push(url));

  const wallet = useQuery({
    queryKey: WALLET_QUERY_KEY,
    queryFn: () => topUp().getWallet(),
  });
  const products = useQuery({
    queryKey: ["topUpProducts"],
    queryFn: () => topUp().listTopUpProducts(),
  });

  const [selected, setSelected] = useState<string | null>(null);
  // 필수 동의 (Q-25) — 컴포넌트 상태로만 둔다. 저장소에 두지 않는다
  const [agreed, setAgreed] = useState(false);

  // 잔액 부족 팝업에서 왔으면 저장된 구매 선택의 견적 ID 를 읽는다 (PURCHASE-RESTORE).
  // /wallet 은 정적 프리렌더라 렌더 중에 sessionStorage 를 읽지 않는다 — 하이드레이션 불일치.
  // 저장값은 지우지 않는다: 충전 후 앞 화면 복귀에 쓴다 (TopUpSuccess).
  const [savedQuoteId, setSavedQuoteId] = useState<string | null>(null);
  useEffect(() => {
    setSavedQuoteId(loadPurchaseSelection()?.quoteId ?? null);
  }, []);

  const quote = useQuery({
    queryKey: ["fortuneQuote", savedQuoteId],
    queryFn: () =>
      (fortunePort ?? getFortunePort()).getQuote(savedQuoteId ?? ""),
    enabled: savedQuoteId !== null,
    // 견적은 매번 서버 값을 새로 받는다 — 만료는 재시도로 낫지 않는다
    retry: false,
  });
  const quoteErrorKind = quote.error ? classifyApiError(quote.error) : null;
  // 만료 · 없는 견적 — 복귀한 앞 화면이 새 견적을 받는다 (Q-07)
  const quoteGone =
    quoteErrorKind === "quote_expired" || quoteErrorKind === "not_found";

  // 부족 값은 서버 견적의 짝 그대로 (P-09) — 화면에서 계산하지 않는다
  const shortageQuote =
    quote.data && quote.data.shortage > 0 ? quote.data : null;

  // 다시 결제하기의 상품 유지 (TOPUP-RETRY-URL): 서버 목록에 있고 판매 중일 때만, 사용자가 아직 고르지 않았을 때 한 번만 미리 고른다.
  // 없거나 비활성이면 아무것도 고르지 않는다(오류 아님). 잔액 부족 추천보다 앞선다. 동의 체크는 미리 하지 않는다.
  const retryPreselected = useRef(false);
  useEffect(() => {
    if (retryPreselected.current || retryProductCode === null) return;
    if (selected !== null || !products.data) return;
    retryPreselected.current = true;
    if (products.data.some((p) => p.code === retryProductCode && p.active)) {
      // 같은 렌더에서 아래 추천 미리 고르기가 덮어쓰지 않게 막는다
      preselected.current = true;
      setSelected(retryProductCode);
    }
  }, [retryProductCode, selected, products.data]);

  // 추천 충전 상품은 서버 값 (P-06). 사용자가 아직 고르지 않았을 때 한 번만 미리 고른다
  const preselected = useRef(false);
  useEffect(() => {
    if (preselected.current || selected !== null || !shortageQuote) return;
    // 다시 결제하기 상품이 있으면 그쪽이 먼저다 — 목록을 받은 뒤 그 판정이 끝날 때까지 기다린다
    if (retryProductCode !== null && !retryPreselected.current) return;
    const code = shortageQuote.recommendedTopUp;
    if (code === null) return;
    if (!products.data?.some((p) => p.code === code && p.active)) return;
    preselected.current = true;
    setSelected(code);
  }, [selected, shortageQuote, products.data, retryProductCode]);
  // 구매 의도 하나에 키 하나 — 실패 후 다시 눌러도 같은 상품이면 같은 키 (I-05)
  const intent = useRef<Intent | null>(null);
  // 같은 틱의 두 번째 탭은 렌더 전이라 isPending 이 아직 false 다 — ref 로 한 번 더 막는다
  const submitting = useRef(false);

  const order = useMutation({
    mutationFn: async (next: Intent) => {
      const created = await topUp().createOrder(
        { productCode: next.productCode },
        next.key,
      );
      await pay().requestPayment(created, {
        retryProductCode: next.productCode,
      });
    },
  });

  // 시끄럽게 실패: 조회 실패와 계약 위반은 오류 화면(error.tsx)으로
  if (wallet.error) throw wallet.error;
  if (quote.error && !quoteGone) throw quote.error;
  if (products.error) throw products.error;
  if (order.error instanceof ApiContractError) throw order.error;

  function onPay() {
    // 동의 없이는 아무것도 보내지 않는다 (버튼 비활성과 이중 방어)
    if (!agreed || selected === null || order.isPending || submitting.current)
      return;
    if (intent.current?.productCode !== selected) {
      intent.current = { productCode: selected, key: createIdempotencyKey() };
    }
    submitting.current = true;
    order.mutate(intent.current, {
      onError: (error) => {
        // 구매자가 결제창을 닫음 — 오류 안내 없이 버튼을 다시 연다. intent(키)는 그대로 둔다 (I-05)
        if (error instanceof PaymentCanceledError) order.reset();
      },
      onSettled: () => {
        submitting.current = false;
      },
    });
  }

  // 판매 중인 상품이 없다 (목록이 비거나 전부 비활성) = PAY-06. 아직 못 받았으면 해당 없음
  const noProducts =
    products.data !== undefined && !products.data.some((p) => p.active);
  const contactHref = supportMailtoHref();

  const canceled = order.error instanceof PaymentCanceledError;
  const errorKind =
    order.error && !canceled ? classifyApiError(order.error) : null;

  return (
    // TODO(PD 문구): 제목
    <AppShell
      title="충전"
      backHref="/"
      // 결제 버튼은 하단 고정 CTA (LAYOUT-FIGMA, PAY-01 237:69): 가운데, 아래 여백 32px
      // 결제 수단 칸 없음 — 결제하기 → 바로 결제창 (PD 수정안 301:128, Q-25 부분). 결제창 결제수단(method)은 TODO(Q-25 · BE-A 토스 계약). 와이어 CTA 는 327×60 — 공용 Button cta(327×69)는 디자인 시스템 적용 때 맞춘다
      cta={
        <div className="flex justify-center pb-[32px]">
          {noProducts ? (
            <Link href="/" className="underline">
              홈으로
            </Link>
          ) : (
            <Button
              variant="cta"
              onClick={onPay}
              disabled={
                !agreed ||
                selected === null ||
                order.isPending ||
                order.isSuccess ||
                errorKind === "review_required"
              }
            >
              {/* TODO(PD 문구) */}
              결제하기
            </Button>
          )}
        </div>
      }
    >
      {shortageQuote ? (
        // 잔액 부족 팝업에서 온 경우 (PAY-02 · FORT-04, LAYOUT-FIGMA 240:98 · 244:237)
        <div
          data-slot="shortage"
          className="mt-[40px] text-center text-[20px] leading-[27px]"
        >
          {/* TODO(PD 문구) */}
          <p>보유 {formatNumber(shortageQuote.walletBalance)}</p>
          {/* TODO(PD 문구) */}
          <p>부족 {formatNumber(shortageQuote.shortage)}</p>
        </div>
      ) : null}

      {shortageQuote ? null : (
        // TODO(PD 토큰 v0): 와이어 임시값 (301:128)
        <p className="mt-[18px] ml-[34px] text-[15px] leading-[18px] text-[#737373]">
          {/* TODO(PD 문구) */}
          보유 {wallet.data ? formatNumber(wallet.data.balance) : null}
        </p>
      )}

      {noProducts ? (
        // PAY-06. BE-A 충전 상품은 PG 준비 전 비활성이라 목록이 비거나 전부 비활성일 수 있다 (FE_COMPATIBILITY.md)
        <div className="mt-[12px] ml-[34px]">
          <p>지금은 충전할 수 있는 상품이 없어요</p>
          <p>잠시 후 다시 확인해 주세요.</p>
        </div>
      ) : products.data ? (
        <fieldset className="mt-[12px] ml-[34px] flex w-[335px] flex-col gap-[8px]">
          {/* TODO(PD 문구) */}
          <legend className="sr-only">충전 상품</legend>
          {products.data.map((product) => (
            <label
              key={product.code}
              data-selected={selected === product.code ? "true" : "false"}
              className={`flex h-[50px] items-center rounded-[8px] border border-black bg-white px-[16px] text-[17px] leading-[normal] data-[selected=true]:bg-[#d9d9d9] ${product.active ? "" : "opacity-50"}`}
            >
              <input
                type="radio"
                name="topUpProduct"
                value={product.code}
                disabled={!product.active}
                checked={selected === product.code}
                onChange={() => setSelected(product.code)}
                className="sr-only"
              />
              {/* 서버 값만 표시한다 (P-03 · P-09) — 계산 없음. TODO(PD 문구): 단위 · 항목 이름 */}
              <span className="w-[70px] font-bold">
                {formatNumber(product.creditedAmount)}
              </span>
              {product.bonusAmount > 0 ? (
                <span className="text-[13px] text-[#737373]">
                  {formatNumber(product.paidAmount)} + 보너스{" "}
                  {formatNumber(product.bonusAmount)}
                </span>
              ) : null}
              <span className="ml-auto font-bold">
                {formatNumber(product.price.amount)} {product.price.currency}
              </span>
            </label>
          ))}
        </fieldset>
      ) : null}

      {noProducts ? null : (
        <>
          {/* 안내 상자 (FUNCTIONAL_SPEC 7장 PAY-01). 내용이 길면 늘어나도록 min-h */}
          <div
            data-slot="top-up-notice"
            className="mt-[24px] ml-[34px] min-h-[188px] w-[335px] rounded-[8px] bg-[#f2f2f2] pt-[14px] pr-[12px] pb-[14px] pl-[12px] text-[13px] font-medium leading-[normal]"
          >
            <ul>
              {TOP_UP_NOTICE_LINES.map((line) => (
                <li key={line} className="before:content-['·_']">
                  {line}
                </li>
              ))}
            </ul>
            <p className="before:content-['·_']">
              <Link href="/refund">환불정책 보기 &gt;</Link>
            </p>
          </div>

          {/* 필수 동의 (Q-25) — 토스 결제창 안의 동의(결제 서비스 약관)와 다르다: 디지털 콘텐츠 청약철회 제한 · 환불정책 고지 */}
          <div
            data-slot="top-up-agreement"
            className="mt-[26px] ml-[37px] flex gap-[7px]"
          >
            <label className="flex gap-[7px]">
              <input
                type="checkbox"
                checked={agreed}
                onChange={(event) => setAgreed(event.target.checked)}
                className="h-[24px] w-[24px] shrink-0"
              />
              <span className="block min-h-[34px] w-[290px] text-[14px] font-medium leading-[normal]">
                주문 내용과 환불정책을 확인했으며 결제에 동의합니다. (필수)
              </span>
            </label>
          </div>
        </>
      )}

      {errorKind === "login_required" || errorKind === "csrf_failed" ? (
        // 로그인 후 이 화면으로 복귀 (PG-2, safeReturnTo)
        <p>
          {/* TODO(PD 문구) */}
          <Link href={loginHref("/wallet")}>다시 로그인해 주세요</Link>
        </p>
      ) : errorKind === "review_required" ? (
        // 다시 시도 없이 문의 안내 (Q-41 5) — COMMON-04. 주문이 만들어지기 전 거절이라 번호 없음 (Q-38 g · Q-47)
        <div>
          <p role="alert">확인이 필요한 구매예요</p>
          <p>고객센터로 문의해 주시면 빠르게 도와드릴게요.</p>
          {BUSINESS_INFO.email === null ? null : <p>{BUSINESS_INFO.email}</p>}
          {contactHref === null ? null : <a href={contactHref}>문의하기</a>}
        </div>
      ) : errorKind ? (
        // TODO(PD 문구): 오류 종류별 안내
        <p role="alert">결제를 시작하지 못했습니다</p>
      ) : null}
    </AppShell>
  );
}
