"use client";

// "use client" 이유: 준비물 체크(localStorage)와 모르는 섹션 경고(Sentry)는 브라우저에서 한다.
// 결과 뷰어 하나로 수능(CSAT-03 ~ 06) · 일반 5종(FORT-06 · 07) · 선물 수신(RECV-03 ~ 06 · RECV-T-03)을 그린다 (VIEWER).
// 수능 가이드는 FUNCTIONAL_SPEC 8장 v0.4 대로 8장 카드를 한 장씩 넘겨 본다 — 상단 점으로 진행 표시, 긴 내용은 카드 안 세로 스크롤,
// 1번 카드의 목록을 누르면 그 카드로 간다 (Figma CSAT-03 결과 카드_수정안 347:312 · CSAT-06 359:179, LAYOUT-FIGMA). 넘김 상태는 URL 에 두지 않는다 (8장 "URL 또는 결과 내부 상태").
// 근거: RESULT-GUIDE-MODEL (결과 포트가 서버 suneungGuide 구조), Q-37 답 (API_SPEC 8장 "수능운 결과 구조"), Q-05 (모르는 섹션은 건너뜀),
// F-08 (결과 하단 고지 — 문구는 PD), S-06 (서버 결과만 표시). 서버 문장은 텍스트로만 렌더한다. 디자인 요소 없음 (PG-FIRST).
// 제목 · 교시 이름 · 분류 이름은 PD 확정 (FUNCTIONAL_SPEC 8장 v0.4, Q-40 · Q-45) (src/lib/reading/labels.ts).
import * as Sentry from "@sentry/nextjs";
import { type ReactNode, useEffect, useRef, useState } from "react";
import type { Reading, SuneungGuide } from "@/lib/ports/reading";
import { loadChecked, saveChecked } from "@/lib/reading/checklist";
import {
  EXAM_PERIOD_LABELS,
  labelOf,
  PREPARATION_GROUPS,
  SECTION_TITLES,
  SUNEUNG_GUIDE_TITLES,
  suneungSummaryTitle,
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
    <article
      className={body.kind === "SUNEUNG_GUIDE" ? undefined : "mt-[53px]"}
    >
      {body.kind === "SUNEUNG_GUIDE" ? (
        <>
          {/* 편지 자리는 카드 묶음 위 지금 자리 — 선물 수신 배치는 RECV 와이어 확인 때 */}
          {letter ? (
            <section data-slot="letter" className={CARD_CLASS}>
              {letter}
            </section>
          ) : null}
          {/* 결과가 바뀌면 1번 카드로 돌아간다 */}
          <GuideCards
            key={reading.id}
            readingId={reading.id}
            personDisplayName={reading.personDisplayName}
            guide={body.guide}
          />
        </>
      ) : (
        /* 카드 사이 16px. 편지 자리는 첫 카드 위에 같은 카드 모양으로 */
        <div className="flex flex-col gap-y-[16px]">
          {letter ? (
            <section data-slot="letter" className={CARD_CLASS}>
              {letter}
            </section>
          ) : null}
          {body.sections.map((section) =>
            section.type === "UNKNOWN" ? null : (
              <Card
                key={section.key}
                id={`s-${section.key}`}
                title={labelOf(SECTION_TITLES, section.key)}
              >
                <Paragraph>{section.content}</Paragraph>
              </Card>
            ),
          )}
        </div>
      )}
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

// 수능 가이드 카드 안 글자 (와이어 347:312 · 359:179) — 글꼴 · 색 토큰은 넣지 않는다 (FONT-HOLD)
const GUIDE_BODY_CLASS = "text-[14px] leading-[normal]";
const GUIDE_TITLE_CLASS = "text-[20px] font-bold leading-[normal]";

function GuideParagraph({ children }: { children: string }) {
  return (
    <p className={`mt-[24px] whitespace-pre-line ${GUIDE_BODY_CLASS}`}>
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
        <li key={index} className={`whitespace-pre-line ${GUIDE_BODY_CLASS}`}>
          {text}
        </li>
      ))}
    </ul>
  );
}

type GuideCardId =
  | "summary"
  | "examDayGuide"
  | "examPeriods"
  | "focusTips"
  | "foodRecommendations"
  | "preparationChecklist"
  | "anxietyManagement"
  | "missingElementAdvice";

// 손가락 넘기기: 가로로 50px 이상, 세로보다 가로가 클 때만 (dx < 0 → 다음, dx > 0 → 이전)
const SWIPE_MIN_DX = 50;

function GuideCards({
  readingId,
  personDisplayName,
  guide,
}: {
  readingId: string;
  personDisplayName: string;
  guide: SuneungGuide;
}) {
  const t = SUNEUNG_GUIDE_TITLES;
  // 카드 순서 = FUNCTIONAL_SPEC 8장 수능 카드 표 (서버 필드 순서와 같다). 서버가 목록을 비워 주면 그 카드는 없다 —
  // 점 · 카드 목록 · 번호는 이 "있는 카드" 기준이다 (빈 카드를 빼면 번호가 당겨진다. 점 8개는 모든 카드가 있을 때)
  const cards: readonly { id: GuideCardId; title: string }[] = [
    { id: "summary", title: suneungSummaryTitle(personDisplayName) },
    { id: "examDayGuide", title: t.examDayGuide },
    ...(guide.examPeriods.length > 0
      ? [{ id: "examPeriods" as const, title: t.examPeriods }]
      : []),
    ...(guide.focusTips.length > 0
      ? [{ id: "focusTips" as const, title: t.focusTips }]
      : []),
    ...(guide.foodRecommendations.length > 0
      ? [{ id: "foodRecommendations" as const, title: t.foodRecommendations }]
      : []),
    ...(guide.preparationChecklist.length > 0
      ? [
          {
            id: "preparationChecklist" as const,
            title: t.preparationChecklist,
          },
        ]
      : []),
    ...(guide.anxietyManagement.length > 0
      ? [{ id: "anxietyManagement" as const, title: t.anxietyManagement }]
      : []),
    { id: "missingElementAdvice", title: t.missingElementAdvice },
  ];

  // 지금 보이는 카드 번호 (0 부터). 결과가 바뀌면 key 로 다시 만들어 0 으로 돌아간다
  const [index, setIndex] = useState(0);
  const last = cards.length - 1;
  const current = Math.min(index, last);
  const card = cards[current];
  const touchStart = useRef<{ x: number; y: number } | null>(null);

  const goTo = (next: number) => setIndex(Math.max(0, Math.min(last, next)));
  const prev = () => goTo(current - 1);
  const next = () => goTo(current + 1);

  function renderBody(id: GuideCardId): ReactNode {
    switch (id) {
      case "summary":
        return (
          <>
            {/* TODO(에셋): 캐릭터 · 요약 이미지 — 글자 없는 자리 (와이어 347:312) */}
            <div
              aria-hidden
              data-slot="summary-image"
              className="mt-[17px] h-[150px] w-[314px] rounded-[8px] bg-[#d9d9d9]"
            />
            <p className={`mt-[20px] whitespace-pre-line ${GUIDE_BODY_CLASS}`}>
              {guide.summary}
            </p>
            {/* TODO(PD 문구): 카드 목록 제목 — 와이어 자리표시 */}
            <p className="mt-[24px] text-[14px] font-bold leading-[normal]">
              카드 목록
            </p>
            <ul>
              {cards.map((item, position) => (
                <li key={item.id}>
                  <button
                    type="button"
                    onClick={() => goTo(position)}
                    className="block text-left text-[13px] leading-[normal]"
                  >
                    {`${position + 1} ${item.title}`}
                  </button>
                </li>
              ))}
            </ul>
          </>
        );
      case "examDayGuide":
        return <GuideParagraph>{guide.examDayGuide}</GuideParagraph>;
      case "examPeriods":
        return (
          <ol className="mt-[24px] flex flex-col gap-y-[24px]">
            {guide.examPeriods.map((item) => {
              // 모르는 코드는 이름 없이 안내만 — 코드를 화면에 보이지 않는다
              const label = labelOf(EXAM_PERIOD_LABELS, item.period);
              return (
                <li
                  key={`${item.period}:${item.guidance}`}
                  className="flex gap-x-[16px]"
                >
                  {label === null ? null : (
                    <h3 className={`shrink-0 font-bold ${GUIDE_BODY_CLASS}`}>
                      {label}
                    </h3>
                  )}
                  <p className={GUIDE_BODY_CLASS}>{item.guidance}</p>
                </li>
              );
            })}
          </ol>
        );
      case "focusTips":
        return <TextList items={guide.focusTips} />;
      case "foodRecommendations":
        return (
          <>
            {/* TODO(이미지): 음식 이미지 — API 없음 */}
            <div className="mt-[24px] flex justify-center">
              <div
                aria-hidden
                data-slot="food-image"
                className="h-[194px] w-[314px] shrink-0 bg-[#b1b1b1]"
              />
            </div>
            <ul className="mt-[24px] flex flex-col gap-y-[24px]">
              {guide.foodRecommendations.map((food, index) => (
                // biome-ignore lint/suspicious/noArrayIndexKey: 순서가 바뀌지 않는 서버 결과 목록 — 같은 음식 이름이 두 번 올 수 있다
                <li key={index}>
                  <h3 className={`font-bold ${GUIDE_BODY_CLASS}`}>
                    {food.item}
                  </h3>
                  <p className={`mt-[8px] ${GUIDE_BODY_CLASS}`}>
                    {food.reason}
                  </p>
                </li>
              ))}
            </ul>
          </>
        );
      case "preparationChecklist":
        return (
          <ChecklistBody
            readingId={readingId}
            items={guide.preparationChecklist}
          />
        );
      case "anxietyManagement":
        return <TextList items={guide.anxietyManagement} />;
      case "missingElementAdvice":
        // TODO(T-01 · 부적): 수능 부적은 TalismanViewer 연결 때 이 카드 끝에 (FUNCTIONAL_SPEC 8장 "마지막에 부적") — 상자를 새로 그리지 않는다
        return <GuideParagraph>{guide.missingElementAdvice}</GuideParagraph>;
    }
  }

  return (
    <>
      {/* 진행 점 — 있는 카드 수만큼, 지금 카드만 검정 (TODO(PD 토큰 v0): 와이어 임시값) */}
      <div
        aria-hidden
        data-slot="card-dots"
        className="mt-[22px] flex justify-center gap-x-[8px]"
      >
        {cards.map((item, position) => (
          <span
            key={item.id}
            data-current={position === current ? "true" : "false"}
            className={`h-[8px] w-[8px] rounded-full ${position === current ? "bg-black" : "bg-[#d9d9d9]"}`}
          />
        ))}
      </div>
      <section
        key={card.id}
        data-slot="result-card"
        aria-labelledby={`g-${card.id}`}
        onTouchStart={(event) => {
          const touch = event.touches[0];
          touchStart.current = touch
            ? { x: touch.clientX, y: touch.clientY }
            : null;
        }}
        onTouchEnd={(event) => {
          const start = touchStart.current;
          const touch = event.changedTouches[0];
          touchStart.current = null;
          if (!start || !touch) return;
          const dx = touch.clientX - start.x;
          const dy = touch.clientY - start.y;
          if (Math.abs(dx) < SWIPE_MIN_DX || Math.abs(dx) <= Math.abs(dy)) {
            return;
          }
          if (dx < 0) next();
          else prev();
        }}
        className="mt-[16px] mx-auto h-[600px] w-[354px] overflow-y-auto rounded-[12px] border border-black bg-[#f2f2f2] px-[20px] pt-[24px] pb-[24px]"
      >
        <h2 id={`g-${card.id}`} className={GUIDE_TITLE_CLASS}>
          {card.title}
        </h2>
        {renderBody(card.id)}
      </section>
      {/* TODO(PD 문구): 이전 · 다음 글자 — 와이어 자리표시. 첫 카드면 이전, 마지막 카드면 다음을 그리지 않는다 */}
      <div className="mt-[20px] flex justify-between text-[15px] font-bold leading-[normal]">
        {current > 0 ? (
          <button type="button" onClick={prev} className="ml-[24px]">
            ‹ 이전
          </button>
        ) : (
          <span />
        )}
        {current < last ? (
          <button type="button" onClick={next} className="mr-[24px]">
            다음 ›
          </button>
        ) : null}
      </div>
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

  // PD 묶음 순서대로 묶고(묶음 안은 서버 순서), 항목 없는 묶음은 건너뛴다. 모르는 분류는 맨 끝에 제목 없는 묶음으로
  const groups = [
    ...PREPARATION_GROUPS.map((group) => ({
      id: group.id,
      title: group.title,
      items: items.filter((item) =>
        (group.categories as readonly string[]).includes(item.category),
      ),
    })),
    {
      id: "UNKNOWN",
      title: null,
      items: items.filter((item) => item.category === "UNKNOWN"),
    },
  ].filter((group) => group.items.length > 0);

  return (
    <div className="mt-[22px] flex flex-col gap-y-[29px]">
      {groups.map((group) => (
        <div key={group.id}>
          {group.title === null ? null : (
            <h3 className="text-[14px] font-bold leading-[normal]">
              {group.title}
            </h3>
          )}
          <ul className="mt-[4px] flex flex-wrap gap-x-[12px] gap-y-[4px]">
            {group.items.map((item) => (
              <li key={item.id}>
                <label className="flex items-center gap-x-[4px] text-[13px] leading-[normal]">
                  <input
                    type="checkbox"
                    className="h-[14px] w-[14px]"
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
