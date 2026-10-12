// 결과 진짜 adapter — GET /api/v1/readings/{readingId}.
// 근거: RESULT-GUIDE-MODEL, Q-37 답 (API_SPEC 8장 "수능운 결과 구조" · GET /readings/{readingId} — 수능운 · 예전 결과 호환 경로),
// ADAPTER-HTTP (createApiClient 로 요청하고 응답 data 를 zod 로 런타임 검사한 뒤 포트 모델로 바꾼다. 생성 타입은 이 파일 안에서만 쓴다),
// F-05 (재열람 스냅샷), S-06 (서버 결과만 표시), Q-05 (모르는 섹션은 그 섹션만 건너뜀), VIEWER.
// 일반 운세 5종의 카테고리별 상세 경로 (GET /readings/{category}/{readingId}) 는 쓰지 않는다 — TODO(Q-26 일반 결과 adapter).
// counterpart · questionKey · talisman · *Version · generationMode · sourceFactKeys 는 검사 · 보관하지 않는다.
import { z } from "zod";
import { ApiContractError } from "@/lib/api/errors";
import type { ApiClient } from "@/lib/api/http";
import { toFortuneType, toProductOption } from "@/lib/ports/fortune";
import {
  type ReadingBody,
  type ReadingPort,
  type ReadingSection,
  toPreparationCategory,
} from "@/lib/ports/reading";
import type { components, paths } from "@/types/api";

const READING_PATH = "/api/v1/readings/{readingId}" satisfies keyof paths;

const text = z.string().min(1);
const texts = z.array(text);

// 목록은 빈 배열도 허용한다 — 화면이 빈 목록 카드를 건너뛴다. 서버가 빈 목록을 주는 것은 계약 위반으로 막지 않는다
const suneungGuideSchema = z.object({
  summary: text,
  examDayGuide: text,
  examPeriods: z.array(z.object({ period: text, guidance: text })),
  focusTips: texts,
  foodRecommendations: z.array(z.object({ item: text, reason: text })),
  // 체크 상태를 id 로 저장하므로 id 가 겹치면 계약 위반이다
  preparationChecklist: z
    .array(z.object({ id: text, label: text, category: text }))
    .refine(
      (items) => new Set(items.map((item) => item.id)).size === items.length,
    ),
  anxietyManagement: texts,
  missingElementAdvice: text,
});

// 키는 null 이어도 있어야 한다 (suneungGuide · event)
const readingSchema = z.object({
  id: z.uuid(),
  fortuneType: z.string(),
  productOption: z.string(),
  subjectDisplayName: text,
  event: z.object({ type: text, date: text }).nullable(),
  disclaimers: texts,
  createdAt: z.string().refine((value) => !Number.isNaN(Date.parse(value))),
  sections: z.array(z.object({ key: z.string(), content: text })),
  suneungGuide: suneungGuideSchema.nullable(),
});

type ReadingData = z.infer<typeof readingSchema>;
type ReadingResponse = components["schemas"]["ReadingResponse"];

// 생성 타입은 필드가 모두 선택(?)이고 null 을 모른다 — 이름을 객체 리터럴과 대조한다.
// 하위 객체(event · sections · suneungGuide)는 모르는 enum 값도 받으므로 이름만 대조하고 모양은 zod 가 검사한다
type ReadingWire = {
  [K in
    | "id"
    | "fortuneType"
    | "productOption"
    | "subjectDisplayName"
    | "disclaimers"
    | "createdAt"]-?: NonNullable<ReadingResponse[K]>;
} & { [K in "event" | "sections" | "suneungGuide"]-?: unknown };

// 생성 타입의 섹션 key. 서버가 key 를 더하면 typecheck 가 깨져 여기에 올린다 (모르는 key 는 런타임에 UNKNOWN)
const KNOWN_SECTION_KEYS: Record<
  NonNullable<components["schemas"]["GeneratedSection"]["key"]>,
  true
> = {
  SUMMARY: true,
  CURRENT_FLOW: true,
  GOOD_PERIOD: true,
  CAUTION: true,
  ACTION_TIP: true,
  RELATIONSHIPS: true,
  STUDY_AND_WORK: true,
  WEALTH_FLOW: true,
  CONDITION: true,
  LUCKY_POINT: true,
  INCOME: true,
  SPENDING_CAUTION: true,
  MATCH_STRENGTH: true,
  MATCH_CONFLICT: true,
  COMMUNICATION: true,
  RELATIONSHIP_TIP: true,
  SPECIAL_STARS: true,
  BALANCING_SPECIAL_STARS: true,
  MISSING_ELEMENT: true,
  EXAM_DAY: true,
  EXAM_PERIODS: true,
  FOCUS: true,
  MEAL: true,
  PREPARATION: true,
  ANXIETY_MANAGEMENT: true,
};

function toSection(item: ReadingData["sections"][number]): ReadingSection {
  return Object.hasOwn(KNOWN_SECTION_KEYS, item.key)
    ? { type: "TEXT", key: item.key, content: item.content }
    : { type: "UNKNOWN", rawKey: item.key };
}

function toBody(data: ReadingData): ReadingBody {
  // 수능운은 suneungGuide 가 있으면 가이드 구조, 없는 예전 결과 · 일반 운세는 sections (API_SPEC 8장 전환 기간 규칙)
  if (data.suneungGuide !== null) {
    return {
      kind: "SUNEUNG_GUIDE",
      guide: {
        summary: data.suneungGuide.summary,
        examDayGuide: data.suneungGuide.examDayGuide,
        examPeriods: data.suneungGuide.examPeriods.map((p) => ({
          period: p.period,
          guidance: p.guidance,
        })),
        focusTips: data.suneungGuide.focusTips,
        foodRecommendations: data.suneungGuide.foodRecommendations.map((f) => ({
          item: f.item,
          reason: f.reason,
        })),
        preparationChecklist: data.suneungGuide.preparationChecklist.map(
          (c) => ({
            id: c.id,
            label: c.label,
            category: toPreparationCategory(c.category),
          }),
        ),
        anxietyManagement: data.suneungGuide.anxietyManagement,
        missingElementAdvice: data.suneungGuide.missingElementAdvice,
      },
    };
  }
  return { kind: "SECTIONS", sections: data.sections.map(toSection) };
}

export function createReadingAdapter(client: ApiClient): ReadingPort {
  return {
    async getReading(readingId) {
      const { status, data } = await client.request({
        method: "GET",
        path: READING_PATH.replace(
          "{readingId}",
          encodeURIComponent(readingId),
        ),
      });
      const parsed = readingSchema.safeParse(data);
      if (!parsed.success) {
        throw new ApiContractError(status, "결과 응답 모양이 다르다");
      }
      const wire = {
        id: parsed.data.id,
        fortuneType: parsed.data.fortuneType,
        productOption: parsed.data.productOption,
        subjectDisplayName: parsed.data.subjectDisplayName,
        event: parsed.data.event,
        disclaimers: parsed.data.disclaimers,
        createdAt: parsed.data.createdAt,
        sections: parsed.data.sections,
        suneungGuide: parsed.data.suneungGuide,
      } satisfies ReadingWire;
      return {
        id: wire.id,
        fortuneType: toFortuneType(wire.fortuneType),
        productOption: toProductOption(wire.productOption),
        personDisplayName: wire.subjectDisplayName,
        event: parsed.data.event,
        body: toBody(parsed.data),
        disclaimers: wire.disclaimers,
        createdAt: wire.createdAt,
      };
    },
  };
}
