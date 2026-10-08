"use client";

// "use client" 이유: 인물 · 상품 조회(TanStack Query), 옵션 선택과 팝업 열림 상태, 충전 후 복귀(sessionStorage), 화면 이동(useRouter)은 브라우저에서 한다.
// FORT-02 · FORT-03 유료 운세 질문 + 옵션 선택 (docs/FRONTEND.md 3장, Figma 195:611 · 248:956 · 195:620 · 245:240).
// 근거: CHECKOUT-POPUP (차감 확인은 이 화면 안 Modal), P-03 (옵션 둘: 부적 포함 / 사주만), P-09 (가격 · 등껍질 수량은 이 화면에서 표시하지 않는다 — 팝업이 서버 견적으로 보인다),
// PURCHASE-RESTORE (충전 후 복귀하면 선택을 되살리고 저장한 견적을 재확인), F-07 (판매 판단은 서버 active 기준), Q-26 · F-04 (질문 입력은 확정 전이라 자리만),
// A-02 · A-03 (로그인 가드는 page.tsx 의 RequireSession), MOCK-PORT (포트는 렌더 중이 아니라 요청할 때 고른다), LAYOUT-FIGMA. 디자인 요소 없음 (PG-FIRST).
import { useQuery } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { AppShell } from "@/components/AppShell";
import { Button } from "@/components/Button";
import { ShellCheckout } from "@/components/ShellCheckout";
import {
  FORTUNE_LABELS,
  FORTUNE_TYPE_OF_SLUG,
  type FortuneSlug,
} from "@/lib/navigation";
import { getFortunePort, getPersonPort } from "@/lib/ports";
import type { FortunePort, FortuneProduct } from "@/lib/ports/fortune";
import type { PersonPort } from "@/lib/ports/person";
import type { TopUpPort } from "@/lib/ports/topUp";
import {
  clearPurchaseSelection,
  loadPurchaseSelection,
} from "@/lib/purchase/restore";

// TODO(PD 문구): 버튼 문구 — 팝업의 옵션 이름도 같은 문구를 쓴다
const TALISMAN_LABEL = "사주 보고 부적도 받기";
const READING_ONLY_LABEL = "사주만 보기";

// 질문 자리 (입력 아님) — 와이어의 회색 상자. TODO(PD 토큰 v0): 와이어 임시값
const LABEL_CLASS = "text-[20px] font-semibold leading-[24px]";
const BOX_CLASS = "bg-[#d9d9d9] text-[20px] font-semibold";

// 같은 option 의 활성 상품은 하나여야 한다 (P-03). 둘 이상이면 계약과 다르다
function onlyActive(
  products: readonly FortuneProduct[],
  option: FortuneProduct["option"],
): FortuneProduct | null {
  const found = products.filter((p) => p.active && p.option === option);
  if (found.length > 1) {
    throw new Error(`활성 상품이 하나가 아니다: ${option}`);
  }
  return found[0] ?? null;
}

export function FortuneQuestionsScreen({
  slug,
  personId,
  personPort,
  fortunePort,
  topUpPort,
}: {
  slug: Exclude<FortuneSlug, "compatibility">;
  // FORT-01 이 쿼리로 넘긴 인물 ID — ID 만 받는다 (생년정보 · 이름 금지)
  personId: string;
  // 테스트에서 주입한다. 기본값은 포트 선택(src/lib/ports) — 요청할 때 고른다 (MOCK-PORT).
  personPort?: PersonPort;
  fortunePort?: FortunePort;
  topUpPort?: TopUpPort;
}) {
  const router = useRouter();
  const fortuneType = FORTUNE_TYPE_OF_SLUG[slug];

  const people = useQuery({
    queryKey: ["people"],
    queryFn: () => (personPort ?? getPersonPort()).list(),
  });
  const products = useQuery({
    queryKey: ["fortuneProducts", fortuneType],
    queryFn: () => (fortunePort ?? getFortunePort()).listProducts(fortuneType),
  });

  const [chosenCode, setChosenCode] = useState<string | null>(null);
  const [checkoutOpen, setCheckoutOpen] = useState(false);
  // 충전 후 복귀면 저장해 둔 견적 ID — 팝업을 닫으면 비운다 (다음 열기는 새 견적)
  const [resumeQuoteId, setResumeQuoteId] = useState<string | null>(null);
  const restored = useRef(false);
  const redirected = useRef(false);

  // 충전 후 돌아와도 같은 대상이 오도록 쿼리를 포함한다
  const returnPath = `/fortune/${slug}/questions?personId=${encodeURIComponent(personId)}`;

  const person = people.data?.find((p) => p.personId === personId) ?? null;
  // 옛 링크 · 지운 인물 — 인물 목록은 왔는데 대상이 없다
  const personMissing = people.data !== undefined && person === null;

  // 옛 링크 · 지운 인물 — FORT-01 로 돌아가 대상을 다시 고른다. 오류 화면으로 던지지 않는다(정상 사용에서도 생기는 경우).
  // notFound() 는 서버 컴포넌트 · 서버 함수 · 라우트 핸들러에서만 부른다 (next 문서 04-functions/not-found.md)
  useEffect(() => {
    if (!personMissing || redirected.current) return;
    redirected.current = true;
    router.replace(`/fortune/${slug}`);
  }, [personMissing, router, slug]);

  // 판매 판단은 서버 active 만 본다 (F-07). 새 enum 값(UNKNOWN)은 무시한다
  const list = products.data ?? null;
  const talisman = list ? onlyActive(list, "READING_WITH_TALISMAN") : null;
  const readingOnly = list ? onlyActive(list, "READING_ONLY") : null;

  // 충전 후 복귀 (PURCHASE-RESTORE): 인물 · 상품이 모두 온 뒤 한 번만 읽는다
  useEffect(() => {
    if (restored.current || !people.data || !products.data) return;
    if (personMissing) return;
    restored.current = true;
    const saved = loadPurchaseSelection();
    // 다른 화면의 저장값은 건드리지 않는다
    if (saved === null || saved.returnPath !== returnPath) return;
    const codes = [talisman?.code, readingOnly?.code];
    if (
      saved.selection.personId !== personId ||
      !codes.includes(saved.selection.productCode)
    ) {
      clearPurchaseSelection();
      return;
    }
    setChosenCode(saved.selection.productCode);
    setResumeQuoteId(saved.quoteId);
    setCheckoutOpen(true);
  }, [
    people.data,
    products.data,
    personMissing,
    returnPath,
    personId,
    talisman,
    readingOnly,
  ]);

  // 시끄럽게 실패: 조회 실패 · 계약과 다른 데이터는 오류 화면(error.tsx)으로
  if (people.error) throw people.error;
  if (products.error) throw products.error;
  if (list?.some((p) => p.fortuneType !== fortuneType)) {
    throw new Error("다른 운세 종류의 상품이 섞여 왔다");
  }

  const chosen =
    chosenCode === null
      ? null
      : ([talisman, readingOnly].find((p) => p?.code === chosenCode) ?? null);
  const chosenLabel =
    chosen === null
      ? ""
      : chosen.option === "READING_WITH_TALISMAN"
        ? TALISMAN_LABEL
        : READING_ONLY_LABEL;
  const soldOut = list !== null && talisman === null && readingOnly === null;
  const ready = person !== null && list !== null && !soldOut;

  function choose(product: FortuneProduct) {
    setChosenCode(product.code);
    setCheckoutOpen(true);
  }

  return (
    // TODO(PD 문구)
    <AppShell
      title={FORTUNE_LABELS[slug]}
      // 앞 화면(FORT-01)으로
      backHref={`/fortune/${slug}`}
      cta={
        ready ? (
          // TODO(P-09 · PD 문구): 버튼 둘째 줄 — 서버 가격 표시 방식 확정 후
          <div className="flex flex-col items-center gap-[17px] pb-[32px]">
            {talisman ? (
              <Button variant="cta" onClick={() => choose(talisman)}>
                {/* TODO(PD 문구) */}
                {TALISMAN_LABEL}
              </Button>
            ) : null}
            {readingOnly ? (
              <Button variant="cta" onClick={() => choose(readingOnly)}>
                {/* TODO(PD 문구) */}
                {READING_ONLY_LABEL}
              </Button>
            ) : null}
          </div>
        ) : null
      }
    >
      {personMissing ? null : (
        <>
          {/* LAYOUT-FIGMA: 질문 자리 — 입력 칸이 아니라 aria-hidden 회색 상자 */}
          {slug === "love" ? (
            <>
              {/* TODO(PD 문구) */}
              <p className={`mt-[59px] ml-[37px] ${LABEL_CLASS}`}>연애 상태</p>
              {/* TODO(Q-26): 선택지 목록 · 서버 키 대기 */}
              <div
                aria-hidden
                className={`mt-[22px] ml-[37px] h-[57px] w-[335px] ${BOX_CLASS}`}
              />
              {/* TODO(PD 문구) */}
              <p className={`mt-[48px] ml-[37px] ${LABEL_CLASS}`}>
                알고 싶은 부분
              </p>
            </>
          ) : (
            // TODO(PD 문구)
            <p className={`mt-[46px] ml-[42px] ${LABEL_CLASS}`}>
              알고 싶은 부분
            </p>
          )}
          {/* TODO(F-04 · Q-26): 고민 입력 — 결과 미반영 기준, 확정 전 입력 칸 없음 */}
          <div
            aria-hidden
            className={`mt-[22px] ${slug === "love" ? "ml-[37px]" : "ml-[42px]"} h-[125px] w-[335px] ${BOX_CLASS}`}
          />

          {soldOut ? (
            // TODO(PD 문구 · Q-24 판매 종료 ✚)
            <p>지금은 구매할 수 없습니다</p>
          ) : null}

          {person && chosen ? (
            <ShellCheckout
              open={checkoutOpen}
              onOpenChange={(next) => {
                setCheckoutOpen(next);
                if (!next) setResumeQuoteId(null);
              }}
              selection={{
                productCode: chosen.code,
                personId,
                counterpartPersonId: null,
              }}
              targetName={person.name}
              optionLabel={chosenLabel}
              resumeQuoteId={resumeQuoteId}
              returnPath={returnPath}
              onPurchased={(result) =>
                router.push(
                  `/fortune/r/${encodeURIComponent(result.readingId)}`,
                )
              }
              port={fortunePort}
              topUpPort={topUpPort}
            />
          ) : null}
        </>
      )}
    </AppShell>
  );
}
