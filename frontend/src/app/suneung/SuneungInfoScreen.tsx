"use client";

// "use client" 이유: 인물 · 상품 조회(TanStack Query), 대상 선택과 팝업 열림 상태, 충전 후 복귀(sessionStorage), 화면 이동(useRouter)은 브라우저에서 한다.
// CSAT-01 수능운 정보 확인 (docs/FRONTEND.md 3장). 근거: CHECKOUT-POPUP (차감 확인은 이 화면 안 Modal),
// P-03 (수능운은 부적 포함 단일 상품 — 옵션 선택 UI 없음), Q-04 (시험 종류 · 시험일 선택 UI 없음),
// F-07 (판매 마감 판단은 서버 active 기준 — 화면이 날짜로 판단하지 않는다), F-09 (오행분석은 결제 전 노출 — 데이터 API 없음, BE-B),
// PURCHASE-RESTORE (충전 후 복귀하면 선택을 되살리고 저장한 견적을 재확인), P-09 (금액은 표시하지 않는다 — 팝업이 서버 견적으로 보인다),
// MOCK-PORT (포트는 렌더 중이 아니라 요청할 때 고른다). 디자인 요소 없음 (PG-FIRST).
// 로그인 가드는 page.tsx 의 RequireSession (A-02 · A-03).
import { useQuery } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { AppShell } from "@/components/AppShell";
import { Button } from "@/components/Button";
import { PersonCard } from "@/components/PersonCard";
import { ShellCheckout } from "@/components/ShellCheckout";
import { getFortunePort, getPersonPort } from "@/lib/ports";
import type { FortunePort } from "@/lib/ports/fortune";
import type { PersonPort } from "@/lib/ports/person";
import type { TopUpPort } from "@/lib/ports/topUp";
import {
  clearPurchaseSelection,
  loadPurchaseSelection,
} from "@/lib/purchase/restore";

const RETURN_PATH = "/suneung";

export function SuneungInfoScreen({
  personPort,
  fortunePort,
  topUpPort,
}: {
  // 테스트에서 주입한다. 기본값은 포트 선택(src/lib/ports) — 요청할 때 고른다 (MOCK-PORT).
  personPort?: PersonPort;
  fortunePort?: FortunePort;
  topUpPort?: TopUpPort;
}) {
  const router = useRouter();

  const people = useQuery({
    queryKey: ["people"],
    queryFn: () => (personPort ?? getPersonPort()).list(),
  });
  const products = useQuery({
    queryKey: ["fortuneProducts", "SUNEUNG"],
    queryFn: () => (fortunePort ?? getFortunePort()).listProducts("SUNEUNG"),
  });

  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [checkoutOpen, setCheckoutOpen] = useState(false);
  // 충전 후 복귀면 저장해 둔 견적 ID — 팝업을 닫으면 비운다 (다음 열기는 새 견적)
  const [resumeQuoteId, setResumeQuoteId] = useState<string | null>(null);
  const restored = useRef(false);

  // 판매 중인 상품은 서버 active 기준이다 (F-07). 날짜로 판단하지 않는다
  const activeProducts = products.data?.filter((p) => p.active);
  const product = activeProducts?.length === 1 ? activeProducts[0] : null;

  // 충전 후 복귀 (PURCHASE-RESTORE): 인물 · 상품이 모두 온 뒤 한 번만 읽는다
  useEffect(() => {
    if (restored.current || !people.data || !products.data) return;
    restored.current = true;
    const saved = loadPurchaseSelection();
    // 다른 화면의 저장값은 건드리지 않는다
    if (saved === null || saved.returnPath !== RETURN_PATH) return;
    const target = people.data.find(
      (p) => p.personId === saved.selection.personId,
    );
    if (
      product === null ||
      saved.selection.productCode !== product.code ||
      !target
    ) {
      clearPurchaseSelection();
      return;
    }
    setSelectedId(target.personId);
    setResumeQuoteId(saved.quoteId);
    setCheckoutOpen(true);
  }, [people.data, products.data, product]);

  // 시끄럽게 실패: 조회 실패 · 계약과 다른 데이터는 오류 화면(error.tsx)으로
  if (people.error) throw people.error;
  if (products.error) throw products.error;
  if (activeProducts && activeProducts.length > 1) {
    // P-03: 수능운은 단일 상품
    throw new Error("SUNEUNG 활성 상품이 하나가 아니다");
  }
  if (people.data && people.data.length === 0) {
    // RequireSession 이 본인 정보 없는 경우를 막는다
    throw new Error("인물 목록이 비어 있다");
  }

  const person =
    people.data?.find((p) => p.personId === selectedId) ??
    people.data?.[0] ??
    null;
  const soldOut = activeProducts !== undefined && activeProducts.length === 0;

  return (
    // TODO(PD 문구)
    <AppShell
      title="수능운"
      backHref="/"
      cta={
        soldOut ? null : (
          // 하단 CTA(195:216): 가운데, 아래 여백 39px (safe-area 는 AppShell 이 더한다)
          <div className="flex justify-center pb-[39px]">
            <Button
              variant="cta"
              onClick={() => setCheckoutOpen(true)}
              disabled={person === null || product === null}
            >
              {/* TODO(PD 문구) */}
              진행
            </Button>
          </div>
        )
      }
    >
      {/* LAYOUT-FIGMA (195:207 · 218~223): 위치 · 글자는 와이어 값 */}
      {/* TODO(PD 문구) */}
      <p className="mt-[58px] text-center text-[20px] font-semibold">
        정보 확인
      </p>

      {person && people.data ? (
        <div className="mt-[38px]">
          <PersonCard
            person={person}
            people={people.data}
            onSelect={(next) => setSelectedId(next.personId)}
          />
        </div>
      ) : null}

      <section data-slot="five-elements" className="mt-[38px] ml-[37px]">
        {/* TODO(PD 문구) */}
        <h2 className="text-[20px] font-semibold">오행분석</h2>
        {/* TODO(F-09 · BE-B): 오행분석 값 자리 — 데이터 API 없음 */}
        <div aria-hidden className="mt-[19px] flex flex-col gap-[28px]">
          {[0, 1, 2, 3, 4].map((slot) => (
            <div key={slot} className="h-[31px] w-[327px] bg-[#d9d9d9]" />
          ))}
        </div>
      </section>

      {soldOut ? (
        // TODO(PD 문구 · Q-24 판매 종료 ✚)
        <p>지금은 구매할 수 없습니다</p>
      ) : null}

      {/* TODO(F-07 · PD 문구): 판매 마감 안내 — 가격 · saleEndsAt 은 이 화면에 표시하지 않는다 (P-09) */}
      {person && product ? (
        <ShellCheckout
          open={checkoutOpen}
          onOpenChange={(next) => {
            setCheckoutOpen(next);
            if (!next) setResumeQuoteId(null);
          }}
          selection={{
            productCode: product.code,
            personId: person.personId,
            counterpartPersonId: null,
          }}
          targetName={person.name}
          // TODO(PD 문구)
          optionLabel="사주+부적"
          resumeQuoteId={resumeQuoteId}
          returnPath={RETURN_PATH}
          onPurchased={(result) =>
            router.push(`/suneung/r/${encodeURIComponent(result.readingId)}`)
          }
          port={fortunePort}
          topUpPort={topUpPort}
        />
      ) : null}
    </AppShell>
  );
}
