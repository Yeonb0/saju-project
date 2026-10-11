"use client";

// "use client" 이유: 인물 조회(TanStack Query), 대상 선택 상태, 화면 이동(useRouter)은 브라우저에서 한다.
// FORT-01 유료 운세 정보 확인 (docs/FRONTEND.md 3장, Figma 195:180). 근거: A-02 · A-03 (로그인 가드는 page.tsx 의 RequireSession),
// F-09 · Q-30 (오행분석은 결제 전 노출 — FiveElementsSection, 진짜 adapter 는 Q-35 · OpenAPI 후), MOCK-PORT (포트는 렌더 중이 아니라 요청할 때 고른다),
// LAYOUT-FIGMA (CSAT-01 과 같은 좌표). 디자인 요소 없음 (PG-FIRST).
// 이 화면은 상품 조회 · 견적 · ShellCheckout · 구매 선택 복원(PURCHASE-RESTORE) · 가격 표시를 하지 않는다.
// 차감 확인은 질문 화면(FORT-02 · 03)의 몫이다 (CHECKOUT-POPUP).
import { useQuery } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { AppShell } from "@/components/AppShell";
import { Button } from "@/components/Button";
import { FiveElementsSection } from "@/components/FiveElementsSection";
import { PersonCard } from "@/components/PersonCard";
import { FORTUNE_LABELS, type FortuneSlug } from "@/lib/navigation";
import { getPersonPort } from "@/lib/ports";
import type { BasicSajuPort } from "@/lib/ports/basicSaju";
import type { PersonPort } from "@/lib/ports/person";

export function FortuneInfoScreen({
  slug,
  personPort,
  basicSajuPort,
}: {
  slug: Exclude<FortuneSlug, "compatibility">;
  // 테스트에서 주입한다. 기본값은 포트 선택(src/lib/ports) — 요청할 때 고른다 (MOCK-PORT).
  personPort?: PersonPort;
  // 테스트에서 주입한다. 기본값은 포트 선택(src/lib/ports) — 요청할 때 고른다 (MOCK-PORT).
  basicSajuPort?: BasicSajuPort;
}) {
  const router = useRouter();

  const people = useQuery({
    queryKey: ["people"],
    queryFn: () => (personPort ?? getPersonPort()).list(),
  });
  const [selectedId, setSelectedId] = useState<string | null>(null);

  // 시끄럽게 실패: 조회 실패 · 계약과 다른 데이터는 오류 화면(error.tsx)으로
  if (people.error) throw people.error;
  if (people.data && people.data.length === 0) {
    throw new Error("인물 목록이 비어 있다");
  }
  // 기본 대상은 본인이다 (목록 순서에 기대지 않는다)
  const self = people.data?.find((p) => p.isSelf) ?? null;
  if (people.data && self === null) {
    // RequireSession 이 본인 정보 없는 경우를 막는다
    throw new Error("인물 목록에 본인이 없다");
  }
  const person =
    people.data?.find((p) => p.personId === selectedId) ?? self ?? null;

  return (
    // TODO(PD 문구)
    <AppShell
      title={FORTUNE_LABELS[slug]}
      backHref="/"
      cta={
        // 하단 CTA(195:194): 가운데, 아래 여백 39px (safe-area 는 AppShell 이 더한다)
        <div className="flex justify-center pb-[39px]">
          {/* TODO(PD 문구) */}
          <Button
            variant="cta"
            disabled={person === null}
            onClick={() => {
              if (person === null) return;
              // 선택 인물은 질문 화면(FORT-02 · 03)으로 쿼리 personId 로 넘긴다 — 인물 ID 만, 생년정보 · 이름 금지.
              // 관측 도구에서는 maskUrl 이 쿼리를 지운다
              router.push(
                `/fortune/${slug}/questions?personId=${encodeURIComponent(person.personId)}`,
              );
            }}
          >
            진행
          </Button>
        </div>
      }
    >
      {/* LAYOUT-FIGMA (195:180): 위치 · 글자는 와이어 값 — CSAT-01 과 같은 좌표 */}
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

      <FiveElementsSection
        personId={person?.personId ?? null}
        port={basicSajuPort}
      />
    </AppShell>
  );
}
