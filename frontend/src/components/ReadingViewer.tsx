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
    <article>
      {letter ? <section data-slot="letter">{letter}</section> : null}
      {reading.sections.map((section) =>
        section.type === "UNKNOWN" ? null : (
          <section key={section.key} aria-labelledby={`s-${section.key}`}>
            <h2 id={`s-${section.key}`}>{section.title}</h2>
            <SectionBody readingId={reading.id} section={section} />
          </section>
        ),
      )}
      {/* 결과 하단 고지 (F-08). 서버 코드 그대로 — TODO(PD 문구): 코드별 고지 문구 */}
      <ul data-slot="disclaimers">
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
      return <p className="whitespace-pre-line">{section.content}</p>;
    case "PERIOD_GUIDANCE":
      return (
        <ol>
          {section.items.map((item) => (
            <li key={item.label}>
              <h3>{item.label}</h3>
              <p>{item.guidance}</p>
              {item.focusPoint !== null ? <p>{item.focusPoint}</p> : null}
            </li>
          ))}
        </ol>
      );
    case "FOOD_RECOMMENDATION":
      return (
        <>
          <h3>{section.primary.name}</h3>
          <p>{section.primary.reason}</p>
          {section.alternatives.length > 0 ? (
            <ul>
              {section.alternatives.map((food) => (
                <li key={food.name}>
                  <h4>{food.name}</h4>
                  <p>{food.reason}</p>
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
    <ul>
      {section.items.map((item) => (
        <li key={item.id}>
          <label>
            <input
              type="checkbox"
              checked={checked.has(item.id)}
              onChange={() => toggle(item.id)}
            />{" "}
            {item.label}
          </label>
        </li>
      ))}
    </ul>
  );
}
