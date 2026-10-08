"use client";

// "use client" 이유: 입력 상태 · 검증(React Hook Form) · 같은 틱 중복 제출을 막는 ref 는 브라우저에서 다룬다.
// 근거: FUNCTIONAL_SPEC 4장, PHASES 1장 HOME-02 (Figma 195:256 입력 순서), FRONTEND 3장 PersonForm.
// 디자인 요소 없음 (PG-FIRST). 날짜 계산은 하지 않는다 — 스키마(createPersonSchema)가 한다.
// TODO(MY-02): 관계 · 타인 권한 확인(kind "other")은 만들지 않는다 — 이번 단계는 본인 전용.
import { zodResolver } from "@hookform/resolvers/zod";
import type { Dayjs } from "dayjs";
import { useRef } from "react";
import { useForm } from "react-hook-form";
import { personErrorMessage } from "@/lib/person/messages";
import {
  createPersonSchema,
  EMPTY_PERSON_FORM,
  type PersonFormInput,
  type PersonFormOutput,
} from "@/lib/person/schema";
import { Button } from "./Button";
import { FormField } from "./FormField";

type PersonFormProps = {
  onSubmit: (value: PersonFormOutput) => void | Promise<void>;
  submitting?: boolean;
  // 테스트에서 주입한다. 기본값은 스키마의 KST 오늘.
  today?: Dayjs;
};

// TODO(PD 토큰 v0): 와이어 임시값 (LAYOUT-FIGMA) — 입력 칸 높이 40px · 회색 배경 · 글자 20px · 안쪽 좌우 12px
const INPUT_CLASS = "h-[40px] w-full bg-[#d9d9d9] px-[12px] text-[20px]";
// 선택지(라디오 · 체크박스) 한 줄: 상자 21×22px, 글자와 13px, 글자 20px font-semibold
const CHOICE_CLASS =
  "flex h-[22px] items-center gap-[13px] text-[20px] font-semibold";
const CHOICE_INPUT_CLASS = "h-[22px] w-[21px]";

export function PersonForm({ onSubmit, submitting, today }: PersonFormProps) {
  const resolver = zodResolver(createPersonSchema({ kind: "self", today }));

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors },
  } = useForm<PersonFormInput, unknown, PersonFormOutput>({
    resolver,
    defaultValues: EMPTY_PERSON_FORM,
  });

  const calendar = watch("calendar");
  const timeUnknown = watch("timeUnknown");

  // 같은 틱의 두 번째 제출은 렌더 전이라 submitting 이 아직 false 다 — ref 로 한 번 더 막는다
  const sending = useRef(false);

  const submit = handleSubmit(async (value) => {
    if (sending.current) return;
    sending.current = true;
    try {
      await onSubmit(value);
    } finally {
      sending.current = false;
    }
  });

  return (
    // 폼 왼쪽 34px · 오른쪽 33px — 입력 칸 폭 335px (LAYOUT-FIGMA, HOME-02 195:256)
    <form onSubmit={submit} noValidate className="ml-[34px] w-[335px]">
      {/* TODO(PD 문구) */}
      <FormField
        id="person-name"
        label="이름"
        className="mt-[20px]"
        error={personErrorMessage(errors.name?.message)}
      >
        {(field) => (
          <input
            type="text"
            className={INPUT_CLASS}
            {...field}
            {...register("name")}
          />
        )}
      </FormField>

      {/* TODO(PD 문구) */}
      {/* type="date" 를 쓰지 않는다: 음력 2월 30일처럼 양력 달력에 없는 날짜를 받아야 한다 */}
      <FormField
        id="person-birth-date"
        label="생년월일"
        className="mt-[31px]"
        error={personErrorMessage(errors.birthDate?.message)}
      >
        {(field) => (
          <input
            type="text"
            inputMode="numeric"
            placeholder="YYYY-MM-DD"
            className={INPUT_CLASS}
            {...field}
            {...register("birthDate")}
          />
        )}
      </FormField>

      {/* 달력 한 줄: 양력 · 음력 · (음력이면) 윤달 — 선택지 사이 33px */}
      <div className="mt-[13px] flex items-start gap-x-[33px]">
        {/* TODO(PD 문구) */}
        <fieldset className="flex flex-wrap items-start gap-x-[33px]">
          <legend className="sr-only">달력</legend>
          <label className={CHOICE_CLASS}>
            <input
              type="radio"
              value="solar"
              className={CHOICE_INPUT_CLASS}
              {...register("calendar")}
            />
            양력
          </label>
          <label className={CHOICE_CLASS}>
            <input
              type="radio"
              value="lunar"
              className={CHOICE_INPUT_CLASS}
              {...register("calendar")}
            />
            음력
          </label>
          {errors.calendar ? (
            <p role="alert" className="w-full">
              {personErrorMessage(errors.calendar.message)}
            </p>
          ) : null}
        </fieldset>

        {calendar === "lunar" ? (
          // TODO(PD 문구)
          <label className={CHOICE_CLASS}>
            <input
              type="checkbox"
              className={CHOICE_INPUT_CLASS}
              {...register("isLeapMonth")}
            />
            윤달
          </label>
        ) : null}
      </div>

      {/* 태어난 시간(172px) · 성별(120px) 두 칸 가로 배치. 화면 위 순서(시간 → 시간 모름 → 성별)는 그대로 두고 격자로 자리만 옮긴다 */}
      <div className="mt-[26px] grid grid-cols-[172px_120px] gap-x-[43px]">
        {/* TODO(PD 문구) */}
        <FormField
          id="person-birth-time"
          label="태어난 시간"
          className="col-start-1 row-start-1"
          error={personErrorMessage(errors.birthTime?.message)}
        >
          {(field) => (
            <input
              type="time"
              disabled={timeUnknown}
              className={INPUT_CLASS}
              {...field}
              {...register("birthTime")}
            />
          )}
        </FormField>

        {/* TODO(PD 문구) */}
        <label
          className={`${CHOICE_CLASS} col-span-2 col-start-1 row-start-2 mt-[12px]`}
        >
          <input
            type="checkbox"
            className={CHOICE_INPUT_CLASS}
            {...register("timeUnknown")}
          />
          시간 모름
        </label>

        {/* TODO(PD 문구) */}
        <FormField
          id="person-gender"
          label="성별"
          className="col-start-2 row-start-1"
          error={personErrorMessage(errors.gender?.message)}
        >
          {(field) => (
            <select className={INPUT_CLASS} {...field} {...register("gender")}>
              <option value="" />
              <option value="male">남성</option>
              <option value="female">여성</option>
              <option value="unspecified">선택하지 않음</option>
            </select>
          )}
        </FormField>
      </div>

      {/* 저장하기는 폼 안에 둔다 (submit). 시간 모름 줄 아래 246px · 아래 44px, 가운데 */}
      <div className="mt-[246px] mb-[44px] flex justify-center">
        {/* TODO(PD 문구) */}
        <Button type="submit" variant="cta" disabled={submitting}>
          저장하기
        </Button>
      </div>
    </form>
  );
}
