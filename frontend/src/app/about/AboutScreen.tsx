"use client";

// "use client" 이유: 충전 상품 조회(TanStack Query)는 브라우저에서 한다.
// PG-4 /about 서비스 · 상품 소개 (로그인 불필요). 근거: R-01 (심사 상품 = 충전), P-09 · Q-22 (금액 · 수량은 서버 값만, 계산 · 하드코딩 없음),
// docs/FRONTEND.md 1-2 (MOCK-PORT). 디자인 요소 없음 (PG-FIRST) — 기본 HTML 요소만. 배치는 LAYOUT-FIGMA INFO-01 (302:129).
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
      <div className="mx-[34px] pb-[28px]">
        {/* TODO(캐릭터): 뿌기 — PG 심사 후 */}
        <div
          aria-hidden
          data-slot="character"
          className="mx-auto mt-[26px] h-[100px] w-[100px]"
        />

        <section className="mt-[20px]">
          {/* TODO(PD 문구) */}
          <h2 className="text-[18px] leading-[22px] font-bold">서비스 소개</h2>
          {/* TODO(PD 문구 · R-01): 서비스 소개 */}
          <p
            data-slot="intro"
            className="mt-[12px] min-h-[102px] text-[14px] leading-[17px]"
          />
        </section>

        <section className="mt-[28px]">
          {/* TODO(PD 문구) */}
          <h2 className="text-[18px] leading-[22px] font-bold">충전 상품</h2>
          {/* 서버 값만 표시한다 (P-09, PD 메모 302:154 "상품 목록 · 가격은 서버 값") */}
          {/* TODO(PD 토큰 v0): 와이어 임시값 (302:129) */}
          <div
            data-slot="top-up-products"
            className="mt-[12px] min-h-[150px] rounded-[8px] bg-[#f2f2f2] px-[14px] pt-[16px] text-[13px] leading-[16px]"
          >
            {active && active.length > 0 ? (
              <ul>
                {active.map((product) => (
                  <li key={product.code}>
                    {/* 서버 값만 표시한다 (P-09). TODO(PD 문구): 항목 이름 · 단위 */}
                    {formatNumber(product.price.amount)}{" "}
                    {product.price.currency} · 유료{" "}
                    {formatNumber(product.paidAmount)} · 보너스{" "}
                    {formatNumber(product.bonusAmount)} · 총{" "}
                    {formatNumber(product.creditedAmount)}
                  </li>
                ))}
              </ul>
            ) : active ? (
              // TODO(PD 문구)
              <p>판매 중인 충전 상품이 없습니다</p>
            ) : null}
          </div>
          {/* TODO(Q-22 · P-03): 등껍질로 이용하는 콘텐츠 가격 — 운세 상품 서버 값(정가 · 메타데이터) 계약 전이라 비운다. 하드코딩 금지 */}
          <div
            data-slot="content-prices"
            className="mt-[20px] min-h-[150px] rounded-[8px] bg-[#f2f2f2] px-[14px] pt-[16px] text-[13px] leading-[16px]"
          />
        </section>

        <section className="mt-[26px]">
          {/* TODO(PD 문구) */}
          <h2 className="text-[18px] leading-[22px] font-bold">
            제공 방법 · 기간
          </h2>
          {/* TODO(R-01 · P-04 · PD 문구): 제공 기간 · 보너스 유효기간 표기 */}
          <p
            data-slot="period"
            className="mt-[12px] min-h-[68px] text-[14px] leading-[17px]"
          />
        </section>

        <section className="mt-[28px]">
          {/* TODO(PD 문구) */}
          <h2 className="text-[18px] leading-[22px] font-bold">유효기간</h2>
          {/* TODO(P-04 · Q-21 · PD 문구): 유료 · 보너스 유효기간 */}
          <p
            data-slot="validity"
            className="mt-[12px] min-h-[34px] text-[14px] leading-[17px]"
          />
        </section>

        <section className="mt-[28px]">
          {/* TODO(PD 문구) */}
          <h2 className="text-[18px] leading-[22px] font-bold">환불</h2>
          {/* TODO(Q-21): 확정 원고 전 넣지 않는다 */}
          <p
            data-slot="refund-summary"
            className="mt-[12px] min-h-[85px] text-[14px] leading-[17px]"
          />
          <p className="text-[14px] leading-[17px]">
            {/* TODO(PD 문구) */}
            <Link href="/refund" className="underline">
              환불정책
            </Link>
          </p>
        </section>

        <section className="mt-[28px]">
          {/* TODO(PD 문구) */}
          <h2 className="text-[18px] leading-[22px] font-bold">고객센터</h2>
          {/* TODO(R-07 · PD 문구): 고객센터 이메일 · 운영 시간 — 사업자 값은 src/lib/business.ts 에서만, 지금은 값 없음 */}
          <p
            data-slot="support"
            className="mt-[12px] min-h-[34px] text-[14px] leading-[17px]"
          />
        </section>

        {/* 와이어에는 없다 — 심사자가 결제 화면으로 가는 길이라 남긴다 */}
        <p className="mt-[28px] text-[14px] leading-[17px]">
          {/* TODO(PD 문구) */}
          <Link href="/wallet" className="underline">
            충전하러 가기
          </Link>
        </p>
      </div>
    </AppShell>
  );
}
