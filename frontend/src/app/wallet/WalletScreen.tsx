"use client";

// "use client" 이유: 포트 호출(TanStack Query)과 결제창 이동은 브라우저에서 한다.
// PAY-01 등껍질 충전 · PG-3. 근거: TOPUP-DONE, I-05 (구매 의도당 키 하나), P-03 · P-09 (서버 값만 표시),
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
import { getFortunePort, getPaymentLauncher, getTopUpPort } from "@/lib/ports";
import type { FortunePort } from "@/lib/ports/fortune";
import type { PaymentLauncher } from "@/lib/ports/paymentLauncher";
import type { TopUpPort } from "@/lib/ports/topUp";
import { loadPurchaseSelection } from "@/lib/purchase/restore";

type Intent = { productCode: string; key: string };

const formatNumber = (value: number) => value.toLocaleString("ko-KR");

export function WalletScreen({
  port,
  launcher,
  fortunePort,
}: {
  // 테스트에서 주입한다. 기본값은 포트 선택(src/lib/ports) — 진짜 구현이 없으면 던져 오류 화면으로 간다.
  // 포트는 렌더 중이 아니라 요청할 때 고른다: 빌드의 정적 렌더에서 진짜 모드 오류로 빌드가 멈추지 않게.
  port?: TopUpPort;
  launcher?: PaymentLauncher;
  fortunePort?: FortunePort;
}) {
  const router = useRouter();
  const topUp = () => port ?? getTopUpPort();
  const pay = () => launcher ?? getPaymentLauncher((url) => router.push(url));

  const wallet = useQuery({
    queryKey: ["wallet"],
    queryFn: () => topUp().getWallet(),
  });
  const products = useQuery({
    queryKey: ["topUpProducts"],
    queryFn: () => topUp().listTopUpProducts(),
  });

  const [selected, setSelected] = useState<string | null>(null);

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

  // 추천 충전 상품은 서버 값 (P-06). 사용자가 아직 고르지 않았을 때 한 번만 미리 고른다
  const preselected = useRef(false);
  useEffect(() => {
    if (preselected.current || selected !== null || !shortageQuote) return;
    const code = shortageQuote.recommendedTopUp;
    if (code === null) return;
    if (!products.data?.some((p) => p.code === code && p.active)) return;
    preselected.current = true;
    setSelected(code);
  }, [selected, shortageQuote, products.data]);
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
      await pay().requestPayment(created);
    },
  });

  // 시끄럽게 실패: 조회 실패와 계약 위반은 오류 화면(error.tsx)으로
  if (wallet.error) throw wallet.error;
  if (quote.error && !quoteGone) throw quote.error;
  if (products.error) throw products.error;
  if (order.error instanceof ApiContractError) throw order.error;

  function onPay() {
    if (selected === null || order.isPending || submitting.current) return;
    if (intent.current?.productCode !== selected) {
      intent.current = { productCode: selected, key: createIdempotencyKey() };
    }
    submitting.current = true;
    order.mutate(intent.current, {
      onSettled: () => {
        submitting.current = false;
      },
    });
  }

  const errorKind = order.error ? classifyApiError(order.error) : null;

  // 고른 상품의 서버 가격 그대로 (P-09) — 계산 없음
  const chosen = products.data?.find((p) => p.code === selected) ?? null;

  return (
    // TODO(PD 문구): 제목
    <AppShell
      title="충전"
      backHref="/"
      // 결제 버튼은 하단 고정 CTA (LAYOUT-FIGMA, PAY-01 237:69): 가운데, 아래 여백 32px
      cta={
        <div className="flex justify-center pb-[32px]">
          <Button
            variant="cta"
            onClick={onPay}
            disabled={selected === null || order.isPending || order.isSuccess}
          >
            {/* TODO(PD 문구) */}
            결제하기
          </Button>
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

      <div
        className={`${shortageQuote ? "mt-[0px]" : "mt-[53px]"} ml-[31px] text-[20px] leading-[normal]`}
      >
        {shortageQuote ? null : (
          <p>
            {/* TODO(PD 문구) */}
            보유 {wallet.data ? formatNumber(wallet.data.balance) : null}
          </p>
        )}

        {products.data && !products.data.some((p) => p.active) ? (
          // BE-A 충전 상품은 PG 준비 전 비활성이라 목록이 비거나 전부 비활성일 수 있다 (FE_COMPATIBILITY.md)
          <p>
            {/* TODO(PD 문구) */}
            판매 중인 충전 상품이 없습니다
          </p>
        ) : products.data ? (
          <fieldset>
            {/* TODO(PD 문구) */}
            <legend>충전 상품</legend>
            {products.data.map((product) => (
              <label key={product.code} className="block">
                <input
                  type="radio"
                  name="topUpProduct"
                  value={product.code}
                  disabled={!product.active}
                  checked={selected === product.code}
                  onChange={() => setSelected(product.code)}
                />{" "}
                {/* 서버 값만 표시한다 (P-03 · P-09). TODO(PD 문구): 항목 이름 · 단위 */}
                {formatNumber(product.price.amount)} {product.price.currency} ·
                유료 {formatNumber(product.paidAmount)} · 보너스{" "}
                {formatNumber(product.bonusAmount)} · 총{" "}
                {formatNumber(product.creditedAmount)}
              </label>
            ))}
          </fieldset>
        ) : null}
      </div>

      <section className="mt-[64px] ml-[34px]">
        {/* TODO(PD 문구) */}
        <h2 className="text-[20px] font-semibold">주문 내용</h2>
        {/* TODO(PD 토큰 v0): 와이어 임시값 (237:69) */}
        <div
          data-slot="order-total"
          className="mt-[22px] flex h-[102px] w-[335px] items-end justify-end bg-[#d9d9d9] pr-[12px] pb-[15px]"
        >
          {chosen ? (
            // 고른 상품의 서버 가격 그대로 (P-09) — 계산 없음. TODO(PD 문구): 항목 이름
            <p className="text-[20px] font-semibold">
              {formatNumber(chosen.price.amount)} {chosen.price.currency}
            </p>
          ) : null}
        </div>
      </section>

      {/* TODO(Q-25): 결제 수단 · 동의 체크 자리 — 팀 확정 전이라 만들지 않는다 */}

      {errorKind === "login_required" || errorKind === "csrf_failed" ? (
        // 로그인 후 이 화면으로 복귀 (PG-2, safeReturnTo)
        <p>
          {/* TODO(PD 문구) */}
          <Link href={loginHref("/wallet")}>다시 로그인해 주세요</Link>
        </p>
      ) : errorKind ? (
        // TODO(PD 문구): 오류 종류별 안내
        <p role="alert">결제를 시작하지 못했습니다</p>
      ) : null}
    </AppShell>
  );
}
