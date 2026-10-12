"use client";

// "use client" 이유: 준비물 체크(localStorage)와 모르는 섹션 경고(Sentry)는 브라우저에서 한다.
// 결과 뷰어 하나로 수능(CSAT-03 ~ 06) · 일반 5종(FORT-06 · 07) · 선물 수신(RECV-03 ~ 06 · RECV-T-03)을 그린다 (VIEWER).
// 근거: RESULT-GUIDE-MODEL (결과 포트가 서버 suneungGuide 구조), Q-37 답 (API_SPEC 8장 "수능운 결과 구조"), Q-05 (모르는 섹션은 건너뜀),
// F-08 (결과 하단 고지 — 문구는 PD), S-06 (서버 결과만 표시). 서버 문장은 텍스트로만 렌더한다. 디자인 요소 없음 (PG-FIRST).
// 제목 · 교시 이름 · 분류 이름은 임시다 (src/lib/reading/labels.ts, TODO(PD 문구)).
import * as Sentry from "@sentry/nextjs";
import { type ReactNode, useEffect, useState } from "react";
import {
  PREPARATION_CATEGORIES,
  type Reading,
  type SuneungGuide,
} from "@/lib/ports/reading";
import { loadChecked, saveChecked } from "@/lib/reading/checklist";
import {
  EXAM_PERIOD_LABELS,
  labelOf,
  PREPARATION_CATEGORY_LABELS,
  SECTION_TITLES,
  SUNEUNG_GUIDE_TITLES,
} from "@/lib/reading/labels";

type Checklist = SuneungGuide["preparationChecklist"];

// 준비물 체크 저장 키. 예전 결과(sections 의 PREPARATION)와 같은 키를 써서 이미 한 체크가 남게 한다
const CHECKLIST_SECTION_KEY = "PREPARATION";

// 결과 카드 (LAYOUT-FIGMA, CSAT-04 195:397 · CSAT-05 195:432 · CSAT-06 195:463)
// TODO(PD 토큰 v0): 와이어 임시값
const CARD_CLASS =
  "mx-auto min-h-[640px] w-[376px] bg-[#d9d9d9] px-[32px] py-[36px]";
const TITLE_CLASS = "text-center text-[20px] font-semibold";
const BODY_CLASS = "text-[20px] font-semibold";

// 모르는 섹션 경고 — 섹션 key 만 보낸다 (결과 본문 · 인적정보 금지, CLAUDE.md 관측)
function reportUnknownToSentry(rawKey: string) {
  Sentry.captureMessage("unknown reading section", {
    level: "warning",
    tags: { sectionKey: rawKey },
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
  reportUnknown?: (rawKey: string) => void;
}) {
  const { body } = reading;
  useEffect(() => {
    if (body.kind !== "SECTIONS") return;
    for (const section of body.sections) {
      if (section.type === "UNKNOWN") reportUnknown(section.rawKey);
    }
  }, [body, reportUnknown]);

  return (
    <article className="mt-[53px]">
      {/* 카드 사이 16px. 편지 자리는 첫 카드 위에 같은 카드 모양으로 */}
      <div className="flex flex-col gap-y-[16px]">
        {letter ? (
          <section data-slot="letter" className={CARD_CLASS}>
            {letter}
          </section>
        ) : null}
        {body.kind === "SUNEUNG_GUIDE" ? (
          <GuideCards readingId={reading.id} guide={body.guide} />
        ) : (
          body.sections.map((section) =>
            section.type === "UNKNOWN" ? null : (
              <Card
                key={section.key}
                id={`s-${section.key}`}
                title={labelOf(SECTION_TITLES, section.key)}
              >
                <Paragraph>{section.content}</Paragraph>
              </Card>
            ),
          )
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

// 제목이 없으면 제목 줄도 aria-labelledby 도 만들지 않는다
function Card({
  id,
  title,
  children,
}: {
  id: string;
  title: string | null;
  children: ReactNode;
}) {
  return (
    <section
      data-slot="result-card"
      aria-labelledby={title === null ? undefined : id}
      className={CARD_CLASS}
    >
      {title === null ? null : (
        <h2 id={id} className={TITLE_CLASS}>
          {title}
        </h2>
      )}
      {children}
    </section>
  );
}

function Paragraph({ children }: { children: string }) {
  return (
    <p
      className={`mt-[24px] whitespace-pre-line leading-[normal] ${BODY_CLASS}`}
    >
      {children}
    </p>
  );
}

function TextList({ items }: { items: readonly string[] }) {
  return (
    <ul className="mt-[24px] flex flex-col gap-y-[12px]">
      {items.map((text, index) => (
        // 결과는 바뀌지 않는 스냅샷이고 같은 문장이 두 번 올 수 있어 순서를 key 로 쓴다 (F-05)
        // biome-ignore lint/suspicious/noArrayIndexKey: 순서가 바뀌지 않는 서버 결과 목록
        <li key={index} className={`whitespace-pre-line ${BODY_CLASS}`}>
          {text}
        </li>
      ))}
    </ul>
  );
}

function GuideCards({
  readingId,
  guide,
}: {
  readingId: string;
  guide: SuneungGuide;
}) {
  const t = SUNEUNG_GUIDE_TITLES;
  // TODO(PD 순서): 카드 순서는 API_SPEC 8장 필드 순서 그대로다. 목록이 비면 그 카드는 그리지 않는다
  return (
    <>
      <Card id="g-summary" title={t.summary}>
        <Paragraph>{guide.summary}</Paragraph>
      </Card>
      <Card id="g-examDayGuide" title={t.examDayGuide}>
        <Paragraph>{guide.examDayGuide}</Paragraph>
      </Card>
      {guide.examPeriods.length > 0 ? (
        <Card id="g-examPeriods" title={t.examPeriods}>
          <ol className="mt-[24px] flex flex-col gap-y-[45px]">
            {guide.examPeriods.map((item) => {
              // 모르는 코드는 이름 없이 안내만 — 코드를 화면에 보이지 않는다
              const label = labelOf(EXAM_PERIOD_LABELS, item.period);
              return (
                <li
                  key={`${item.period}:${item.guidance}`}
                  className="flex gap-x-[37px]"
                >
                  {label === null ? null : (
                    <h3 className={`shrink-0 ${BODY_CLASS}`}>{label}</h3>
                  )}
                  <p className={BODY_CLASS}>{item.guidance}</p>
                </li>
              );
            })}
          </ol>
        </Card>
      ) : null}
      {guide.focusTips.length > 0 ? (
        <Card id="g-focusTips" title={t.focusTips}>
          <TextList items={guide.focusTips} />
        </Card>
      ) : null}
      {guide.foodRecommendations.length > 0 ? (
        <Card id="g-foodRecommendations" title={t.foodRecommendations}>
          {/* TODO(이미지): 음식 이미지 — API 없음 */}
          <div className="mt-[24px] flex justify-center">
            <div
              aria-hidden
              data-slot="food-image"
              className="h-[194px] w-[311px] shrink-0 bg-[#b1b1b1]"
            />
          </div>
          <ul className="mt-[34px] flex flex-col gap-y-[36px]">
            {guide.foodRecommendations.map((food, index) => (
              // biome-ignore lint/suspicious/noArrayIndexKey: 순서가 바뀌지 않는 서버 결과 목록 — 같은 음식 이름이 두 번 올 수 있다
              <li key={index}>
                <h3 className={TITLE_CLASS}>{food.item}</h3>
                <p className={`mt-[12px] ${BODY_CLASS}`}>{food.reason}</p>
              </li>
            ))}
          </ul>
        </Card>
      ) : null}
      {guide.preparationChecklist.length > 0 ? (
        <Card id="g-preparationChecklist" title={t.preparationChecklist}>
          <ChecklistBody
            readingId={readingId}
            items={guide.preparationChecklist}
          />
        </Card>
      ) : null}
      {guide.anxietyManagement.length > 0 ? (
        <Card id="g-anxietyManagement" title={t.anxietyManagement}>
          <TextList items={guide.anxietyManagement} />
        </Card>
      ) : null}
      <Card id="g-missingElementAdvice" title={t.missingElementAdvice}>
        <Paragraph>{guide.missingElementAdvice}</Paragraph>
      </Card>
    </>
  );
}

function ChecklistBody({
  readingId,
  items,
}: {
  readingId: string;
  items: Checklist;
}) {
  const [checked, setChecked] = useState<ReadonlySet<string>>(new Set());
  // 저장소는 렌더가 아니라 effect 에서 읽는다 — 서버 렌더와 어긋나지 않게
  useEffect(() => {
    setChecked(new Set(loadChecked(readingId, CHECKLIST_SECTION_KEY)));
  }, [readingId]);

  function toggle(id: string) {
    const next = new Set(checked);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    setChecked(next);
    // 지금 결과에 있는 항목만 저장한다
    saveChecked(
      readingId,
      CHECKLIST_SECTION_KEY,
      items.map((item) => item.id).filter((itemId) => next.has(itemId)),
    );
  }

  // 분류 순서대로 묶고, 항목 없는 분류는 건너뛴다. 모르는 분류는 맨 끝에 제목 없는 묶음으로
  const groups = [
    ...PREPARATION_CATEGORIES.map((category) => ({
      category,
      title: PREPARATION_CATEGORY_LABELS[category],
      items: items.filter((item) => item.category === category),
    })),
    {
      category: "UNKNOWN",
      title: null,
      items: items.filter((item) => item.category === "UNKNOWN"),
    },
  ].filter((group) => group.items.length > 0);

  return (
    <div className="mt-[45px] flex flex-col gap-y-[24px]">
      {groups.map((group) => (
        <div key={group.category}>
          {group.title === null ? null : (
            <h3 className={BODY_CLASS}>{group.title}</h3>
          )}
          <ul>
            {group.items.map((item) => (
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
        </div>
      ))}
    </div>
  );
}
