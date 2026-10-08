"use client";

// "use client" 이유: 포트 호출(TanStack Query)과 결제창 이동은 브라우저에서 한다.
// PAY-01 등껍질 충전 · PG-3. 근거: TOPUP-DONE, I-05 (구매 의도당 키 하나), P-03 · P-09 (서버 값만 표시),
// docs/FRONTEND.md 1-2 (MOCK-PORT). 디자인 요소 없음 (PG-FIRST) — 기본 HTML 요소의 최소 레이아웃만.
// 로그인 가드는 page.tsx 의 RequireSession (PG-2 · A-02 · A-03).
import { useMutation, useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useRef, useState } from "react";
import { AppShell } from "@/components/AppShell";
import { Button } from "@/components/Button";
import { ApiContractError, classifyApiError } from "@/lib/api/errors";
import { createIdempotencyKey } from "@/lib/api/idempotency";
import { loginHref } from "@/lib/auth/returnTo";
import { getPaymentLauncher, getTopUpPort } from "@/lib/ports";
import type { PaymentLauncher } from "@/lib/ports/paymentLauncher";
import type { TopUpPort } from "@/lib/ports/topUp";

type Intent = { productCode: string; key: string };

const formatNumber = (value: number) => value.toLocaleString("ko-KR");

export function WalletScreen({
  port,
  launcher,
}: {
  // 테스트에서 주입한다. 기본값은 포트 선택(src/lib/ports) — 진짜 구현이 없으면 던져 오류 화면으로 간다.
  // 포트는 렌더 중이 아니라 요청할 때 고른다: 빌드의 정적 렌더에서 진짜 모드 오류로 빌드가 멈추지 않게.
  port?: TopUpPort;
  launcher?: PaymentLauncher;
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

  return (
    // TODO(PD 문구): 제목
    <AppShell title="충전" backHref="/">
      <p>
        {/* TODO(PD 문구) */}
        보유 {wallet.data ? formatNumber(wallet.data.balance) : null}
      </p>

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

      <Button
        onClick={onPay}
        disabled={selected === null || order.isPending || order.isSuccess}
      >
        {/* TODO(PD 문구) */}
        결제하기
      </Button>
    </AppShell>
  );
}
