"use client";

// "use client" 이유: 준비물 체크(localStorage)와 모르는 섹션 경고(Sentry)는 브라우저에서 한다.
// 결과 뷰어 하나로 수능(CSAT-03 ~ 06) · 일반 5종(FORT-06 · 07) · 선물 수신(RECV-03 ~ 06 · RECV-T-03)을 그린다 (VIEWER).
// 근거: API_SPEC 8장 초안 섹션 타입, Q-05 (모르는 타입은 그 섹션만 건너뜀), F-08 (결과 하단 고지 — 문구는 PD), S-06 (서버 결과만 표시).
// 섹션 순서는 서버 응답 그대로 (Q-23 g). 서버 문장은 텍스트로만 렌더한다. 디자인 요소 없음 (PG-FIRST).
import * as Sentry from "@sentry/nextjs";
import { type ReactNode, useEffect, useState } from "react";
import type { Reading, ReadingSection } from "@/lib/ports/reading";
import { loadChecked, saveChecked } from "@/lib/reading/checklist";

type Checklist = Extract<ReadingSection, { type: "CHECKLIST" }>;

// 결과 카드 (LAYOUT-FIGMA, CSAT-04 195:397 · CSAT-05 195:432 · CSAT-06 195:463)
// TODO(PD 토큰 v0): 와이어 임시값
const CARD_CLASS =
  "mx-auto min-h-[640px] w-[376px] bg-[#d9d9d9] px-[32px] py-[36px]";
const TITLE_CLASS = "text-center text-[20px] font-semibold";
const BODY_CLASS = "text-[20px] font-semibold";
const SUB_CLASS = "text-[16px] text-[#414141]";

// 모르는 섹션 타입 경고 — 타입 이름만 보낸다 (결과 본문 · 인적정보 금지, CLAUDE.md 관측)
function reportUnknownToSentry(rawType: string) {
  Sentry.captureMessage("unknown reading section type", {
    level: "warning",
    tags: { sectionType: rawType },
  });
}

export function ReadingViewer({
  reading,
  letter,
  reportUnknown = reportUnknownToSentry,
}: {
  reading: Reading;
  // 선물 수신의 보낸 사람 편지 자리 (RECV-03). 텍스트로만 렌더할 것 — dangerouslySetInnerHTML 금지 (G-08)
  letter?: ReactNode;
  // 테스트에서 주입한다
  reportUnknown?: (rawType: string) => void;
}) {
  useEffect(() => {
    for (const section of reading.sections) {
      if (section.type === "UNKNOWN") reportUnknown(section.rawType);
    }
  }, [reading.sections, reportUnknown]);

  return (
    <article className="mt-[53px]">
      {/* 카드 사이 16px. 편지 자리는 첫 카드 위에 같은 카드 모양으로 */}
      <div className="flex flex-col gap-y-[16px]">
        {letter ? (
          <section data-slot="letter" className={CARD_CLASS}>
            {letter}
          </section>
        ) : null}
        {reading.sections.map((section) =>
          section.type === "UNKNOWN" ? null : (
            <section
              key={section.key}
              data-slot="result-card"
              aria-labelledby={`s-${section.key}`}
              className={CARD_CLASS}
            >
              <h2 id={`s-${section.key}`} className={TITLE_CLASS}>
                {section.title}
              </h2>
              <SectionBody readingId={reading.id} section={section} />
            </section>
          ),
        )}
      </div>
      {/* 결과 하단 고지 (F-08). 서버 코드 그대로 — TODO(PD 문구): 코드별 고지 문구 */}
      <ul
        data-slot="disclaimers"
        className="mx-[34px] mt-[24px] text-[12px] text-neutral-600"
      >
        {reading.disclaimers.map((code) => (
          <li key={code}>{code}</li>
        ))}
      </ul>
    </article>
  );
}

function SectionBody({
  readingId,
  section,
}: {
  readingId: string;
  section: Exclude<ReadingSection, { type: "UNKNOWN" }>;
}) {
  switch (section.type) {
    case "TEXT":
      return (
        <p
          className={`mt-[24px] whitespace-pre-line leading-[normal] ${BODY_CLASS}`}
        >
          {section.content}
        </p>
      );
    case "PERIOD_GUIDANCE":
      return (
        <ol className="mt-[24px] flex flex-col gap-y-[45px]">
          {section.items.map((item) => (
            <li key={item.label} className="flex gap-x-[37px]">
              <h3 className={`shrink-0 ${BODY_CLASS}`}>{item.label}</h3>
              <div>
                <p className={BODY_CLASS}>{item.guidance}</p>
                {item.focusPoint !== null ? (
                  <p className={SUB_CLASS}>{item.focusPoint}</p>
                ) : null}
              </div>
            </li>
          ))}
        </ol>
      );
    case "FOOD_RECOMMENDATION":
      return (
        <>
          {/* TODO(이미지): 음식 이미지 — API 없음 */}
          <div className="mt-[24px] flex justify-center">
            <div
              aria-hidden
              data-slot="food-image"
              className="h-[194px] w-[311px] shrink-0 bg-[#b1b1b1]"
            />
          </div>
          <h3 className={`mt-[34px] ${TITLE_CLASS}`}>{section.primary.name}</h3>
          <p className={`mt-[36px] ${BODY_CLASS}`}>{section.primary.reason}</p>
          {section.alternatives.length > 0 ? (
            <ul className="mt-[36px] flex flex-col gap-y-[12px]">
              {section.alternatives.map((food) => (
                <li key={food.name}>
                  <h4 className={BODY_CLASS}>{food.name}</h4>
                  <p className={SUB_CLASS}>{food.reason}</p>
                </li>
              ))}
            </ul>
          ) : null}
        </>
      );
    case "CHECKLIST":
      return <ChecklistBody readingId={readingId} section={section} />;
  }
}

function ChecklistBody({
  readingId,
  section,
}: {
  readingId: string;
  section: Checklist;
}) {
  const [checked, setChecked] = useState<ReadonlySet<string>>(new Set());
  // 저장소는 렌더가 아니라 effect 에서 읽는다 — 서버 렌더와 어긋나지 않게
  useEffect(() => {
    setChecked(new Set(loadChecked(readingId, section.key)));
  }, [readingId, section.key]);

  function toggle(id: string) {
    const next = new Set(checked);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    setChecked(next);
    // 지금 결과에 있는 항목만 저장한다
    saveChecked(
      readingId,
      section.key,
      section.items.map((item) => item.id).filter((itemId) => next.has(itemId)),
    );
  }

  return (
    <ul className="mt-[45px]">
      {section.items.map((item) => (
        <li key={item.id}>
          <label
            className={`flex h-[35px] items-center gap-x-[13px] ${BODY_CLASS}`}
          >
            <input
              type="checkbox"
              className="h-[24px] w-[24px]"
              checked={checked.has(item.id)}
              onChange={() => toggle(item.id)}
            />
            {item.label}
          </label>
        </li>
      ))}
    </ul>
  );
}
