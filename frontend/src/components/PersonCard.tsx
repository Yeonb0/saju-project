"use client";

// "use client" 이유: "불러오기" 바텀시트(vaul)를 연다.
// PersonCard — 대상 인물 카드 + 수정 + "저장된 다른 사용자 불러오기" (FORT-01 · CSAT-01 · MY-01 · MATCH-02, FRONTEND.md 3장).
// 인물 목록은 앞 화면이 인물 포트(list)에서 받아 넘긴다. 카드에는 서버가 준 이름만 보인다 — 생년정보는 포트 모델에 없다.
// 관계 표시(A-07)는 타인 입력(MY-02) 확정 후. 디자인 요소 없음 (PG-FIRST).
import Link from "next/link";
import { useState } from "react";
import { BottomSheet } from "@/components/BottomSheet";
import { Button } from "@/components/Button";
import { Card } from "@/components/Card";
import type { PersonSummary } from "@/lib/ports/person";

export function PersonCard({
  person,
  people,
  onSelect,
}: {
  person: PersonSummary;
  // 고를 수 있는 인물 (본인 포함). 한 명뿐이면 불러오기를 보이지 않는다
  people: readonly PersonSummary[];
  onSelect: (person: PersonSummary) => void;
}) {
  const [open, setOpen] = useState(false);

  return (
    <Card>
      <p>
        {person.name}
        {person.isSelf ? (
          // TODO(PD 문구)
          <span> (본인)</span>
        ) : null}
      </p>
      {/* MY-02 수정 (/me/people/[id]) — 수정 화면은 Phase 3 체크박스. TODO(PD 문구) */}
      <Link href={`/me/people/${encodeURIComponent(person.personId)}`}>
        수정
      </Link>
      {people.length > 1 ? (
        <>
          <Button onClick={() => setOpen(true)}>
            {/* TODO(PD 문구) */}
            저장된 다른 사용자 불러오기
          </Button>
          {/* TODO(PD 문구): 제목 */}
          <BottomSheet open={open} onOpenChange={setOpen} title="저장된 사용자">
            <ul>
              {people.map((candidate) => (
                <li key={candidate.personId}>
                  <Button
                    aria-pressed={candidate.personId === person.personId}
                    onClick={() => {
                      onSelect(candidate);
                      setOpen(false);
                    }}
                  >
                    {candidate.name}
                    {candidate.isSelf ? (
                      // TODO(PD 문구)
                      <span> (본인)</span>
                    ) : null}
                  </Button>
                </li>
              ))}
            </ul>
          </BottomSheet>
        </>
      ) : null}
    </Card>
  );
}
