// 인물 정보 입력 폼(PersonForm)의 검증 규칙. 화면 없이 규칙만 둔다.
// 근거: docs/FUNCTIONAL_SPEC.md 4장 (main), A-07 관계 선택지, O-03 타인 정보 권한 확인, PHASES 3장 PersonForm.
// 이 파일의 값 이름(calendar · gender · relation 등)은 FE 폼 모델이다. 백엔드 API 타입이 아니며,
// 서버 요청 모양으로의 변환은 OpenAPI 수령 후 따로 만든다 (CLAUDE.md — 타입의 출처는 OpenAPI 생성본뿐).
// 오류 메시지는 화면 문구가 아니라 코드(PERSON_ERROR)다. 화면 문구는 PD 가 정한다 — TODO(PD 문구).
import { z } from "zod";
import type dayjs from "@/lib/date";
import { kstStartOfToday } from "@/lib/date";

export const CALENDARS = ["solar", "lunar"] as const;
// FUNCTIONAL_SPEC 4장: 남성 / 여성 / 선택하지 않음
export const GENDERS = ["male", "female", "unspecified"] as const;
// A-07: 엄마 · 아빠 · 애인 · 직접 입력
export const RELATIONS = ["mother", "father", "partner", "custom"] as const;

export const PERSON_ERROR = {
  required: "required",
  dateFormat: "date_format",
  dateInvalid: "date_invalid",
  dateTooEarly: "date_too_early",
  dateInFuture: "date_in_future",
  timeFormat: "time_format",
  permissionRequired: "permission_required",
} as const;

// FUNCTIONAL_SPEC 4장: 생년월일 1900-01-01 ~ 오늘
export const MIN_BIRTH_DATE = "1900-01-01";

const DATE_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/;
// 24시간 HH:mm
const TIME_PATTERN = /^([01]\d|2[0-3]):[0-5]\d$/;

export type PersonKind = "self" | "other";

type SchemaOptions = {
  kind: PersonKind;
  // 테스트에서 주입한다. 기본값은 KST 기준 오늘 — 기기 시간대를 쓰지 않는다 (PHASES 3-1).
  today?: dayjs.Dayjs;
};

function isSolarDate(year: number, month: number, day: number) {
  const date = new Date(Date.UTC(year, month - 1, day));
  return (
    date.getUTCFullYear() === year &&
    date.getUTCMonth() === month - 1 &&
    date.getUTCDate() === day
  );
}

// 음력은 한 달이 29일 또는 30일이라 양력 달력으로 검사하면 2월 30일 같은 실제 음력 날짜가 막힌다.
// FE 는 월 1~12 · 일 1~30 범위만 보고, 그 날짜가 실제로 있는지는 서버(만세력)가 판단한다 (S-06).
function isLunarDateShape(month: number, day: number) {
  return month >= 1 && month <= 12 && day >= 1 && day <= 30;
}

export function createPersonSchema({ kind, today }: SchemaOptions) {
  const todayText = (today ?? kstStartOfToday()).format("YYYY-MM-DD");

  return z
    .object({
      name: z.string(),
      calendar: z.enum(CALENDARS, { error: PERSON_ERROR.required }),
      isLeapMonth: z.boolean(),
      birthDate: z.string(),
      timeUnknown: z.boolean(),
      birthTime: z.string(),
      gender: z.enum(GENDERS, { error: PERSON_ERROR.required }),
      relation: z.enum(RELATIONS).nullable(),
      relationText: z.string(),
      permissionConfirmed: z.boolean(),
    })
    .superRefine((value, ctx) => {
      // 이름: 필수, 앞뒤 공백 제거. 최대 길이는 명세에 없어 정하지 않는다 (서버 검증).
      if (value.name.trim() === "") {
        ctx.addIssue({
          code: "custom",
          path: ["name"],
          message: PERSON_ERROR.required,
        });
      }

      const match = DATE_PATTERN.exec(value.birthDate);
      if (value.birthDate === "") {
        ctx.addIssue({
          code: "custom",
          path: ["birthDate"],
          message: PERSON_ERROR.required,
        });
      } else if (!match) {
        ctx.addIssue({
          code: "custom",
          path: ["birthDate"],
          message: PERSON_ERROR.dateFormat,
        });
      } else {
        const [year, month, day] = match.slice(1).map(Number);
        const shapeOk =
          value.calendar === "lunar"
            ? isLunarDateShape(month, day)
            : isSolarDate(year, month, day);
        if (!shapeOk) {
          ctx.addIssue({
            code: "custom",
            path: ["birthDate"],
            message: PERSON_ERROR.dateInvalid,
          });
        } else if (value.birthDate < MIN_BIRTH_DATE) {
          ctx.addIssue({
            code: "custom",
            path: ["birthDate"],
            message: PERSON_ERROR.dateTooEarly,
          });
        } else if (value.birthDate > todayText) {
          ctx.addIssue({
            code: "custom",
            path: ["birthDate"],
            message: PERSON_ERROR.dateInFuture,
          });
        }
      }

      // 태어난 시간: 시 · 분 또는 시간 모름
      if (!value.timeUnknown) {
        if (value.birthTime === "") {
          ctx.addIssue({
            code: "custom",
            path: ["birthTime"],
            message: PERSON_ERROR.required,
          });
        } else if (!TIME_PATTERN.test(value.birthTime)) {
          ctx.addIssue({
            code: "custom",
            path: ["birthTime"],
            message: PERSON_ERROR.timeFormat,
          });
        }
      }

      if (kind === "other") {
        // 관계: 타인 저장 시 필수 (FUNCTIONAL_SPEC 4장 · A-07)
        if (value.relation === null) {
          ctx.addIssue({
            code: "custom",
            path: ["relation"],
            message: PERSON_ERROR.required,
          });
        } else if (
          value.relation === "custom" &&
          value.relationText.trim() === ""
        ) {
          // 직접 입력 글자 수 제한은 명세에 없어 정하지 않는다
          ctx.addIssue({
            code: "custom",
            path: ["relationText"],
            message: PERSON_ERROR.required,
          });
        }
        // 타인 정보 입력 권한 확인 체크 필수 (FUNCTIONAL_SPEC 4장 · O-03)
        if (!value.permissionConfirmed) {
          ctx.addIssue({
            code: "custom",
            path: ["permissionConfirmed"],
            message: PERSON_ERROR.permissionRequired,
          });
        }
      }
    })
    .transform((value) => ({
      name: value.name.trim(),
      calendar: value.calendar,
      // 윤달은 음력일 때만 의미가 있다 (FUNCTIONAL_SPEC 4장)
      isLeapMonth: value.calendar === "lunar" ? value.isLeapMonth : false,
      birthDate: value.birthDate,
      birthTime: value.timeUnknown ? null : value.birthTime,
      gender: value.gender,
      relation:
        kind === "other" && value.relation !== null
          ? {
              kind: value.relation,
              label:
                value.relation === "custom" ? value.relationText.trim() : null,
            }
          : null,
    }));
}

export type PersonFormInput = z.input<ReturnType<typeof createPersonSchema>>;
export type PersonFormOutput = z.output<ReturnType<typeof createPersonSchema>>;

// 폼 초기값. 고를 값(달력 · 성별 · 관계)은 비워 두어 사용자가 직접 고르게 한다.
export const EMPTY_PERSON_FORM = {
  name: "",
  calendar: undefined,
  isLeapMonth: false,
  birthDate: "",
  timeUnknown: false,
  birthTime: "",
  gender: undefined,
  relation: null,
  relationText: "",
  permissionConfirmed: false,
} as const;
