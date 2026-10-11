"use client";

// "use client" 이유: 오행분석 조회(TanStack Query)는 브라우저에서 한다.
// 결제 전 오행분석 (F-09) — FORT-01 · CSAT-01 공통 (LAYOUT-FIGMA 195:180 · 195:202). 값은 서버(가짜) 그대로, 화면에서 계산하지 않는다 (S-06).
// 포트는 요청할 때 고른다 (MOCK-PORT). 진짜 adapter 는 Q-35 · OpenAPI 후. TODO(PD 토큰 v0): 와이어 임시값.
import { useQuery } from "@tanstack/react-query";
import { classifyApiError } from "@/lib/api/errors";
import { getBasicSajuPort } from "@/lib/ports";
import type { BasicSajuPort, FiveElement } from "@/lib/ports/basicSaju";

// TODO(PD 문구): 원소 이름 표기
const ELEMENT_LABEL: Record<FiveElement, string> = {
  WOOD: "목",
  FIRE: "화",
  EARTH: "토",
  METAL: "금",
  WATER: "수",
};

export function FiveElementsSection({
  personId,
  port,
}: {
  personId: string | null;
  // 테스트에서 주입한다. 기본값은 포트 선택(src/lib/ports) — 요청할 때 고른다 (MOCK-PORT).
  port?: BasicSajuPort;
}) {
  const result = useQuery({
    queryKey: ["basicSaju", personId],
    enabled: personId !== null,
    // 422 는 재시도로 낫지 않고, 그 밖의 실패는 오류 화면으로 간다
    retry: false,
    queryFn: () => {
      if (personId === null) throw new Error("오행분석 대상이 없다");
      return (port ?? getBasicSajuPort()).getBasicSaju(personId);
    },
  });

  // 시끄럽게 실패: 출생 시간 필요 안내가 아닌 오류는 오류 화면(error.tsx · Sentry)으로
  const needsBirthTime =
    result.error !== null &&
    classifyApiError(result.error) === "birth_time_required";
  if (result.error && !needsBirthTime) throw result.error;

  // TODO(F-08 · PD 문구): 시간 미상 해석 제한 고지 — 문구 확정 후 (result.data.birthTimeKnown === false 일 때)

  return (
    <section data-slot="five-elements" className="mt-[38px] ml-[37px]">
      {/* TODO(PD 문구) */}
      <h2 className="text-[20px] font-semibold">오행분석</h2>
      {result.data ? (
        <ul className="mt-[19px] flex flex-col gap-[28px]">
          {result.data.fiveElements.map(({ element, count }) => (
            <li
              key={element}
              data-element={element}
              className="flex h-[31px] w-[327px] items-center justify-between bg-[#d9d9d9] px-[12px] text-[16px]"
            >
              <span>{ELEMENT_LABEL[element]}</span>
              <span>{count}</span>
            </li>
          ))}
        </ul>
      ) : needsBirthTime ? (
        <>
          {/* TODO(PD 문구): 절기 경계일 출생 시간 안내 — 본인 정보 수정 경로는 Q-29 */}
          <output
            data-slot="birth-time-required"
            className="mt-[19px] block text-[16px]"
          >
            출생 시간이 필요합니다
          </output>
        </>
      ) : (
        <div aria-hidden className="mt-[19px] flex flex-col gap-[28px]">
          {[0, 1, 2, 3, 4].map((slot) => (
            <div key={slot} className="h-[31px] w-[327px] bg-[#d9d9d9]" />
          ))}
        </div>
      )}
    </section>
  );
}
