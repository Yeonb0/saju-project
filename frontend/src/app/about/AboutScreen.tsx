"use client";

// "use client" 이유: 충전 상품 조회(TanStack Query)는 브라우저에서 한다.
// PG-4 /about 서비스 · 상품 소개 (로그인 불필요). 근거: R-01 (심사 상품 = 충전), P-09 · Q-22 (금액 · 수량은 서버 값만, 계산 · 하드코딩 없음),
// docs/FRONTEND.md 1-2 (MOCK-PORT). 디자인 요소 없음 (PG-FIRST) — 기본 HTML 요소만.
// 제공 내용 · 제공 기간 · 환불 요약은 R-01 · Q-21 · PD 원고 대기 — 자리만 둔다.
import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { AppShell } from "@/components/AppShell";
import { getTopUpPort } from "@/lib/ports";
import type { TopUpPort } from "@/lib/ports/topUp";

const formatNumber = (value: number) => value.toLocaleString("ko-KR");

export function AboutScreen({
  port,
}: {
  // 테스트에서 주입한다. 기본값은 포트 선택(src/lib/ports) — 진짜 구현이 없으면 던져 오류 화면으로 간다.
  // 포트는 렌더 중이 아니라 요청할 때 고른다: 빌드의 정적 렌더에서 진짜 모드 오류로 빌드가 멈추지 않게.
  port?: TopUpPort;
}) {
  const products = useQuery({
    queryKey: ["topUpProducts"],
    queryFn: () => (port ?? getTopUpPort()).listTopUpProducts(),
  });

  // 시끄럽게 실패: 조회 실패와 계약 위반은 오류 화면(error.tsx)으로
  if (products.error) throw products.error;

  // 비활성 상품은 어떤 경우에도 보이지 않는다. 서버 순서 그대로
  const active = products.data?.filter((p) => p.active);

  return (
    // 서비스명이 화면 제목(AppShell 의 h1) — NAME (D-11). h1 은 하나만 둔다
    <AppShell title="뿌기사주">
      {/* TODO(PD 문구 · R-01): 서비스 · 상품 소개 */}
      <p data-slot="intro" />

      <section>
        {/* TODO(PD 문구) */}
        <h2>충전 상품</h2>
        {active && active.length > 0 ? (
          <ul>
            {active.map((product) => (
              <li key={product.code}>
                {/* 서버 값만 표시한다 (P-09). TODO(PD 문구): 항목 이름 · 단위 */}
                {formatNumber(product.price.amount)} {product.price.currency} ·
                유료 {formatNumber(product.paidAmount)} · 보너스{" "}
                {formatNumber(product.bonusAmount)} · 총{" "}
                {formatNumber(product.creditedAmount)}
              </li>
            ))}
          </ul>
        ) : active ? (
          // TODO(PD 문구)
          <p>판매 중인 충전 상품이 없습니다</p>
        ) : null}
      </section>

      {/* TODO(R-01 · P-04 · PD 문구): 제공 기간 · 보너스 유효기간 표기 */}
      <p data-slot="period" />

      {/* TODO(Q-21): 확정 원고 전 넣지 않는다 */}
      <p data-slot="refund-summary" />
      <p>
        {/* TODO(PD 문구) */}
        <Link href="/refund">환불정책</Link>
      </p>

      <p>
        {/* TODO(PD 문구) */}
        <Link href="/wallet">충전하러 가기</Link>
      </p>
    </AppShell>
  );
}
