"use client";

// "use client" 이유: 인물 조회(TanStack Query), 상대 선택 상태와 고르기 시트 열림, 화면 이동(useRouter)은 브라우저에서 한다.
// MATCH-01 · MATCH-02 궁합 사람 선택 (docs/FRONTEND.md 3장, Figma 248:798 · 248:827).
// 근거: Q-26 답 (v0.3: 궁합은 본인 + 상대, 상대가 저장돼 있지 않으면 새로 입력 MY-02 후 복귀),
// FIGMA-FINAL (와이어 MATCH-01 은 두 칸 모두 빈 칸이지만 팀 문서를 따라 첫 칸은 본인 고정),
// A-02 · A-03 (로그인 가드는 page.tsx 의 RequireSession), MOCK-PORT (포트는 렌더 중이 아니라 요청할 때 고른다), LAYOUT-FIGMA. 디자인 요소 없음 (PG-FIRST).
// 이 화면에는 상품 조회 · 견적 · ShellCheckout · 가격 · 오행분석이 없다 (CHECKOUT-POPUP — 차감은 질문 화면 MATCH-03).
import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { AppShell } from "@/components/AppShell";
import { BottomSheet } from "@/components/BottomSheet";
import { Button } from "@/components/Button";
import { Card } from "@/components/Card";
import { PersonCard } from "@/components/PersonCard";
import { FORTUNE_LABELS } from "@/lib/navigation";
import { getPersonPort } from "@/lib/ports";
import type { PersonPort } from "@/lib/ports/person";

export function MatchPeopleScreen({
  personPort,
}: {
  // 테스트에서 주입한다. 기본값은 포트 선택(src/lib/ports) — 요청할 때 고른다 (MOCK-PORT).
  personPort?: PersonPort;
}) {
  const router = useRouter();

  const people = useQuery({
    queryKey: ["people"],
    queryFn: () => (personPort ?? getPersonPort()).list(),
  });
  const [counterpartId, setCounterpartId] = useState<string | null>(null);
  const [sheetOpen, setSheetOpen] = useState(false);

  // 시끄럽게 실패: 조회 실패 · 계약과 다른 데이터는 오류 화면(error.tsx)으로
  if (people.error) throw people.error;
  if (people.data && people.data.length === 0) {
    throw new Error("인물 목록이 비어 있다");
  }
  const self = people.data?.find((p) => p.isSelf) ?? null;
  if (people.data && self === null) {
    // RequireSession 이 본인 정보 없는 경우를 막는다
    throw new Error("인물 목록에 본인이 없다");
  }
  const others = people.data?.filter((p) => !p.isSelf) ?? [];
  const counterpart = others.find((p) => p.personId === counterpartId) ?? null;

  return (
    // TODO(PD 문구)
    <AppShell
      title={FORTUNE_LABELS.compatibility}
      backHref="/"
      cta={
        // 하단 CTA(248:812): 가운데, 아래 여백 39px (safe-area 는 AppShell 이 더한다)
        <div className="flex justify-center pb-[39px]">
          {/* TODO(PD 문구) */}
          <Button
            variant="cta"
            disabled={self === null || counterpart === null}
            onClick={() => {
              if (self === null || counterpart === null) return;
              // 인물 ID 만 넘긴다 — 생년정보 · 이름 금지. 관측 도구에서는 maskUrl 이 쿼리를 지운다.
              // 질문 화면(MATCH-03)은 다음 단계
              router.push(
                `/fortune/compatibility/questions?personId=${encodeURIComponent(self.personId)}&counterpartId=${encodeURIComponent(counterpart.personId)}`,
              );
            }}
          >
            진행
          </Button>
        </div>
      }
    >
      {/* LAYOUT-FIGMA (248:798): 위치 · 글자는 와이어 값 */}
      {/* TODO(PD 문구) */}
      <p className="mt-[58px] text-center text-[20px] font-semibold leading-[24px]">
        두 사람 선택
      </p>

      {/* 첫 칸: 본인 고정 — 한 명이라 불러오기가 보이지 않는다 */}
      {self ? (
        <div className="mt-[26px]">
          <PersonCard person={self} people={[self]} onSelect={() => {}} />
        </div>
      ) : null}

      {/* 둘째 칸: 상대 */}
      {people.data ? (
        <div className="mt-[14px]">
          {counterpart ? (
            <PersonCard
              person={counterpart}
              people={others}
              onSelect={(next) => setCounterpartId(next.personId)}
            />
          ) : (
            <Card className="mx-auto flex min-h-[115px] w-[351px] items-center justify-center">
              {/* TODO(PD 문구) */}
              <button
                type="button"
                aria-label="상대 선택"
                onClick={() => setSheetOpen(true)}
                className="flex h-[24px] w-[24px] items-center justify-center"
              >
                {/* TODO(PD 아이콘): add 아이콘 24×24 자리 — 텍스트 글리프 임시 */}
                +
              </button>
            </Card>
          )}
        </div>
      ) : null}

      {/* TODO(PD 문구): 제목 */}
      <BottomSheet
        open={sheetOpen}
        onOpenChange={setSheetOpen}
        title="저장된 사용자"
      >
        <ul>
          {others.map((candidate) => (
            <li key={candidate.personId}>
              <Button
                onClick={() => {
                  setCounterpartId(candidate.personId);
                  setSheetOpen(false);
                }}
              >
                {candidate.name}
              </Button>
            </li>
          ))}
        </ul>
        {/* TODO(MY-02): 타인 입력 화면 · 입력 후 이 화면으로 복귀 — PD 권한 확인 문구 대기 */}
        {/* TODO(PD 문구) */}
        <Link href="/me/people/new">새로 입력</Link>
      </BottomSheet>
    </AppShell>
  );
}
