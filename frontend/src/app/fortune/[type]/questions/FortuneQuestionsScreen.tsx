"use client";

// "use client" 이유: 인물 · 상품 조회(TanStack Query), 옵션 선택과 팝업 열림 상태, 충전 후 복귀(sessionStorage), 화면 이동(useRouter)은 브라우저에서 한다.
// FORT-02 · FORT-03 · MATCH-03 유료 운세 질문 + 옵션 선택 (docs/FRONTEND.md 3장, Figma 195:611 · 248:956 · 248:849 · 195:620 · 245:240).
// 궁합(MATCH-03)은 본인 + 상대 (Q-26) — 상대 ID 를 견적 · 복원 · 복귀 경로에 넣는다.
// 근거: CHECKOUT-POPUP (차감 확인은 이 화면 안 Modal), P-03 (옵션 둘: 부적 포함 / 사주만), P-09 (가격 · 등껍질 수량은 이 화면에서 표시하지 않는다 — 팝업이 서버 견적으로 보인다),
// PURCHASE-RESTORE (충전 후 복귀하면 질문 선택까지 되살리고 저장한 견적을 재확인), F-07 (판매 판단은 서버 active 기준),
// Q-26 · FUNCTIONAL_SPEC 8장 "정보·질문" (선택지 칩 — 질문(questionKey)은 모든 운세, 관계(relationType)는 궁합. 고르기 전에는 구매 버튼 비활성. 고민 입력은 F-04 로 1차 없음),
// A-02 · A-03 (로그인 가드는 page.tsx 의 RequireSession), MOCK-PORT (포트는 렌더 중이 아니라 요청할 때 고른다), LAYOUT-FIGMA. 디자인 요소 없음 (PG-FIRST).
import { useQuery } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import { AppShell } from "@/components/AppShell";
import { Button } from "@/components/Button";
import { Chip } from "@/components/Chip";
import { ShellCheckout } from "@/components/ShellCheckout";
import {
  isQuestionKeyOf,
  QUESTION_SETS,
  type QuestionFortuneType,
  type QuestionOption,
  RELATION_QUESTION,
} from "@/lib/fortune/questions";
import {
  FORTUNE_LABELS,
  FORTUNE_TYPE_OF_SLUG,
  type FortuneSlug,
} from "@/lib/navigation";
import { getFortunePort, getPersonPort } from "@/lib/ports";
import type {
  FortunePort,
  FortuneProduct,
  RelationType,
} from "@/lib/ports/fortune";
import type { PersonPort } from "@/lib/ports/person";
import type { TopUpPort } from "@/lib/ports/topUp";
import {
  clearPurchaseSelection,
  loadPurchaseSelection,
} from "@/lib/purchase/restore";

// TODO(PD 문구): 버튼 문구 — 팝업의 옵션 이름도 같은 문구를 쓴다
const TALISMAN_LABEL = "사주 보고 부적도 받기";
const READING_ONLY_LABEL = "사주만 보기";

// 질문 제목 · 칩 묶음 배치 (LAYOUT-FIGMA FORT-02 347:203 · MATCH-03 347:264). TODO(PD 토큰 v0): 와이어 임시값
const QUESTION_TITLE_CLASS = "ml-[24px] text-[15px] font-bold leading-[normal]";
const CHIPS_CLASS =
  "mt-[12px] ml-[24px] flex w-[354px] flex-wrap gap-x-[8px] gap-y-[10px]";

// 칩 묶음 — 하나만 고른다 (다른 칩을 누르면 바뀌고, 같은 칩을 다시 눌러도 해제하지 않는다)
function QuestionGroup({
  id,
  title,
  options,
  selectedKey,
  onSelect,
  className,
}: {
  id: string;
  title: string;
  options: readonly QuestionOption[];
  selectedKey: string | null;
  onSelect: (key: string) => void;
  className: string;
}) {
  return (
    <>
      <p id={id} className={`${className} ${QUESTION_TITLE_CLASS}`}>
        {title}
      </p>
      {/* biome-ignore lint/a11y/useSemanticElements: 질문 제목(<p id>)을 aria-labelledby 로 잇는 묶음 구조로 정했다 (칩 묶음 — fieldset 아님) */}
      <div role="group" aria-labelledby={id} className={CHIPS_CLASS}>
        {options.map((option) => (
          <Chip
            key={option.key}
            selected={selectedKey === option.key}
            onClick={() => onSelect(option.key)}
          >
            {option.label}
          </Chip>
        ))}
      </div>
    </>
  );
}

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
  counterpartId,
  personPort,
  fortunePort,
  topUpPort,
}: {
  slug: FortuneSlug;
  // FORT-01 · MATCH-01 이 쿼리로 넘긴 인물 ID — ID 만 받는다 (생년정보 · 이름 금지)
  personId: string;
  // 궁합만 상대가 있다 (Q-26: 본인 + 상대). 다른 운세는 null
  counterpartId: string | null;
  // 테스트에서 주입한다. 기본값은 포트 선택(src/lib/ports) — 요청할 때 고른다 (MOCK-PORT).
  personPort?: PersonPort;
  fortunePort?: FortunePort;
  topUpPort?: TopUpPort;
}) {
  const router = useRouter();
  const fortuneType = FORTUNE_TYPE_OF_SLUG[slug];
  if (fortuneType === "UNKNOWN") {
    // slug 표에는 UNKNOWN 이 없다 — 있으면 코드 오류
    throw new Error("운세 종류가 UNKNOWN 이다");
  }
  const isCompat = slug === "compatibility";

  const people = useQuery({
    queryKey: ["people"],
    queryFn: () => (personPort ?? getPersonPort()).list(),
  });
  const products = useQuery({
    queryKey: ["fortuneProducts", fortuneType],
    queryFn: () => (fortunePort ?? getFortunePort()).listProducts(fortuneType),
  });

  const [chosenCode, setChosenCode] = useState<string | null>(null);
  // 선택지 칩 (처음엔 아무것도 고르지 않는다). 관계는 궁합만
  const [questionKey, setQuestionKey] = useState<string | null>(null);
  const [relationType, setRelationType] = useState<RelationType | null>(null);
  const [checkoutOpen, setCheckoutOpen] = useState(false);
  // 충전 후 복귀면 저장해 둔 견적 ID — 팝업을 닫으면 비운다 (다음 열기는 새 견적)
  const [resumeQuoteId, setResumeQuoteId] = useState<string | null>(null);
  // 저장해 둔 구매 키 (PURCHASE-KEY-STORE) — 견적 ID 와 함께 팝업에 넘기면 상태 조회부터 한다
  const [resumePurchaseKey, setResumePurchaseKey] = useState<string | null>(
    null,
  );
  const relationPrefilled = useRef(false);
  const restored = useRef(false);
  const redirected = useRef(false);

  // 충전 후 돌아와도 같은 대상이 오도록 쿼리를 포함한다
  const returnPath =
    isCompat && counterpartId !== null
      ? `/fortune/compatibility/questions?personId=${encodeURIComponent(personId)}&counterpartId=${encodeURIComponent(counterpartId)}`
      : `/fortune/${slug}/questions?personId=${encodeURIComponent(personId)}`;
  // 견적 · 복원에 쓰는 상대 ID (궁합만)
  const counterpartPersonId = isCompat ? counterpartId : null;

  const person = people.data?.find((p) => p.personId === personId) ?? null;
  const counterpart =
    isCompat && counterpartId !== null
      ? (people.data?.find((p) => p.personId === counterpartId) ?? null)
      : null;
  // 잘못된 대상 — 인물 목록은 왔는데 (1) 대상이 없다 (2) 궁합인데 첫 사람이 본인이 아니다
  // (3) 궁합인데 상대가 없다 (4) 궁합인데 상대가 본인이거나 첫 사람과 같다
  const targetInvalid =
    people.data !== undefined &&
    (person === null ||
      (isCompat &&
        (!person.isSelf ||
          counterpart === null ||
          counterpart.isSelf ||
          counterpart.personId === personId)));

  // 옛 링크 · 지운 인물 — 앞 화면(FORT-01, 궁합은 MATCH-01)으로 돌아가 대상을 다시 고른다.
  // 오류 화면으로 던지지 않는다(정상 사용에서도 생기는 경우).
  // notFound() 는 서버 컴포넌트 · 서버 함수 · 라우트 핸들러에서만 부른다 (next 문서 04-functions/not-found.md)
  useEffect(() => {
    if (!targetInvalid || redirected.current) return;
    redirected.current = true;
    router.replace(`/fortune/${slug}`);
  }, [targetInvalid, router, slug]);

  // 판매 판단은 서버 active 만 본다 (F-07). 새 enum 값(UNKNOWN)은 무시한다
  const list = products.data ?? null;
  const talisman = list ? onlyActive(list, "READING_WITH_TALISMAN") : null;
  const readingOnly = list ? onlyActive(list, "READING_ONLY") : null;

  // 관계 미리 고르기 (궁합만): 상대의 저장 관계가 애인(LOVER)이면 「연인」을 미리 고른다. 인물 목록이 온 뒤 한 번만.
  // 그 밖의 관계는 미리 고르지 않는다 — 저장한 사람의 관계와 서버 relationType 목록이 달라 자동으로 맞추지 않는다 (FUNCTIONAL_SPEC 8장).
  // 아래 복원 effect 보다 앞에 둔다 — 복원값이 있으면 복원이 덮어쓴다. 질문 칩은 고르지 않은 채라 옵션 버튼은 여전히 비활성이다.
  const counterpartRelation = counterpart?.relationType ?? null;
  useEffect(() => {
    if (relationPrefilled.current || !people.data || targetInvalid) return;
    relationPrefilled.current = true;
    if (counterpartRelation === "LOVER") {
      setRelationType((current) => current ?? "LOVER");
    }
  }, [people.data, targetInvalid, counterpartRelation]);

  // 충전 후 복귀 (PURCHASE-RESTORE): 인물 · 상품이 모두 온 뒤 한 번만 읽는다
  useEffect(() => {
    if (restored.current || !people.data || !products.data) return;
    if (targetInvalid) return;
    restored.current = true;
    const saved = loadPurchaseSelection();
    // 다른 화면의 저장값은 건드리지 않는다
    if (saved === null || saved.returnPath !== returnPath) return;
    const codes = [talisman?.code, readingOnly?.code];
    const savedProduct = [talisman, readingOnly].find(
      (p) => p?.code === saved.selection.productCode,
    );
    if (
      saved.selection.fortuneType !== fortuneType ||
      saved.selection.personId !== personId ||
      saved.selection.counterpartPersonId !== counterpartPersonId ||
      !codes.includes(saved.selection.productCode) ||
      // 저장한 질문이 이 운세의 키가 아니거나, 저장한 옵션이 저장한 상품의 옵션과 다르면 되살리지 않는다
      saved.selection.questionKey === null ||
      !isQuestionKeyOf(fortuneType, saved.selection.questionKey) ||
      savedProduct?.option !== saved.selection.productOption
    ) {
      clearPurchaseSelection();
      return;
    }
    setQuestionKey(saved.selection.questionKey);
    setRelationType(saved.selection.relationType);
    setChosenCode(saved.selection.productCode);
    setResumeQuoteId(saved.quoteId);
    setResumePurchaseKey(saved.purchaseKey);
    setCheckoutOpen(true);
  }, [
    people.data,
    products.data,
    targetInvalid,
    returnPath,
    fortuneType,
    personId,
    counterpartPersonId,
    talisman,
    readingOnly,
  ]);

  // 시끄럽게 실패: 조회 실패 · 계약과 다른 데이터는 오류 화면(error.tsx)으로
  if (people.error) throw people.error;
  if (products.error) throw products.error;
  if (!isCompat && counterpartId !== null) {
    // 페이지가 막는 경우 — 궁합이 아닌데 상대가 왔다 (계약 위반)
    throw new Error("궁합이 아닌 운세에 상대 ID 가 왔다");
  }
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
  // 고르기 전에는 구매 버튼이 비활성이다 (FUNCTIONAL_SPEC 8장) — 질문은 모든 운세, 관계는 궁합만
  const canChoose =
    questionKey !== null && (!isCompat || relationType !== null);
  const questionSet = QUESTION_SETS[fortuneType as QuestionFortuneType];
  const soldOut = list !== null && talisman === null && readingOnly === null;
  const ready = person !== null && !targetInvalid && list !== null && !soldOut;
  // TODO(PD 문구): 두 사람 표시
  const targetName =
    isCompat && person && counterpart
      ? `${person.name} · ${counterpart.name}`
      : (person?.name ?? "");

  function choose(product: FortuneProduct) {
    // 방어 — 버튼이 비활성이라 보통 오지 않는다
    if (!canChoose) return;
    setChosenCode(product.code);
    setCheckoutOpen(true);
  }

  return (
    // TODO(PD 문구)
    <AppShell
      title={FORTUNE_LABELS[slug]}
      // 앞 화면(FORT-01, 궁합은 MATCH-01)으로
      backHref={`/fortune/${slug}`}
      cta={
        ready ? (
          // TODO(P-09 · Q-22): 버튼 둘째 줄 가격 표시 — 서버 가격 표시 방식 확정 후
          <div className="flex flex-col items-center gap-[17px] pb-[32px]">
            {/* 결제 CTA 위 고지 (FUNCTIONAL_SPEC 3장 · F-08 · D-05, 와이어 347:227) */}
            <p className="text-center text-[12px] leading-[normal] text-[#737373]">
              운세와 부적은 재미와 참고를 위한 콘텐츠예요.
            </p>
            {talisman ? (
              <Button
                variant="cta"
                onClick={() => choose(talisman)}
                disabled={!canChoose}
              >
                {/* TODO(PD 문구) */}
                {TALISMAN_LABEL}
              </Button>
            ) : null}
            {readingOnly ? (
              <Button
                variant="cta"
                onClick={() => choose(readingOnly)}
                disabled={!canChoose}
              >
                {/* TODO(PD 문구) */}
                {READING_ONLY_LABEL}
              </Button>
            ) : null}
          </div>
        ) : null
      }
    >
      {targetInvalid ? null : (
        <>
          {/* 선택지 칩 (LAYOUT-FIGMA 347:203 · 347:264). 궁합은 관계 묶음 다음에 질문 묶음.
              상대의 저장 관계가 애인이면 「연인」을 미리 골라 둔다 (위 effect, FUNCTIONAL_SPEC 8장 · API_SPEC 4장 relationType) */}
          {isCompat ? (
            <QuestionGroup
              id="question-relation"
              title={RELATION_QUESTION.title}
              options={RELATION_QUESTION.options}
              selectedKey={relationType}
              onSelect={(key) => setRelationType(key as RelationType)}
              className="mt-[32px]"
            />
          ) : null}
          <QuestionGroup
            id="question-main"
            title={questionSet.title}
            options={questionSet.options}
            selectedKey={questionKey}
            onSelect={setQuestionKey}
            className={isCompat ? "mt-[36px]" : "mt-[32px]"}
          />

          {soldOut ? (
            // TODO(PD 문구 · Q-24 판매 종료 ✚)
            <p>지금은 구매할 수 없습니다</p>
          ) : null}

          {person && chosen && !targetInvalid ? (
            <ShellCheckout
              open={checkoutOpen}
              onOpenChange={(next) => {
                setCheckoutOpen(next);
                if (!next) {
                  setResumeQuoteId(null);
                  setResumePurchaseKey(null);
                }
              }}
              selection={{
                fortuneType,
                productCode: chosen.code,
                personId,
                counterpartPersonId,
                productOption:
                  chosen.option === "UNKNOWN" ? null : chosen.option,
                questionKey,
                relationType: isCompat ? relationType : null,
              }}
              targetName={targetName}
              optionLabel={chosenLabel}
              resumeQuoteId={resumeQuoteId}
              resumePurchaseKey={resumePurchaseKey}
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
