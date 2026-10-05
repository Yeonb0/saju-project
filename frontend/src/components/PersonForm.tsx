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
    <form onSubmit={submit} noValidate>
      {/* TODO(PD 문구) */}
      <FormField
        id="person-name"
        label="이름"
        error={personErrorMessage(errors.name?.message)}
      >
        {(field) => <input type="text" {...field} {...register("name")} />}
      </FormField>

      {/* TODO(PD 문구) */}
      {/* type="date" 를 쓰지 않는다: 음력 2월 30일처럼 양력 달력에 없는 날짜를 받아야 한다 */}
      <FormField
        id="person-birth-date"
        label="생년월일"
        error={personErrorMessage(errors.birthDate?.message)}
      >
        {(field) => (
          <input
            type="text"
            inputMode="numeric"
            placeholder="YYYY-MM-DD"
            {...field}
            {...register("birthDate")}
          />
        )}
      </FormField>

      {/* TODO(PD 문구) */}
      <fieldset>
        <legend>달력</legend>
        <label>
          <input type="radio" value="solar" {...register("calendar")} />
          양력
        </label>
        <label>
          <input type="radio" value="lunar" {...register("calendar")} />
          음력
        </label>
        {errors.calendar ? (
          <p role="alert">{personErrorMessage(errors.calendar.message)}</p>
        ) : null}
      </fieldset>

      {calendar === "lunar" ? (
        // TODO(PD 문구)
        <label>
          <input type="checkbox" {...register("isLeapMonth")} />
          윤달
        </label>
      ) : null}

      {/* TODO(PD 문구) */}
      <FormField
        id="person-birth-time"
        label="태어난 시간"
        error={personErrorMessage(errors.birthTime?.message)}
      >
        {(field) => (
          <input
            type="time"
            disabled={timeUnknown}
            {...field}
            {...register("birthTime")}
          />
        )}
      </FormField>

      {/* TODO(PD 문구) */}
      <label>
        <input type="checkbox" {...register("timeUnknown")} />
        시간 모름
      </label>

      {/* TODO(PD 문구) */}
      <FormField
        id="person-gender"
        label="성별"
        error={personErrorMessage(errors.gender?.message)}
      >
        {(field) => (
          <select {...field} {...register("gender")}>
            <option value="" />
            <option value="male">남성</option>
            <option value="female">여성</option>
            <option value="unspecified">선택하지 않음</option>
          </select>
        )}
      </FormField>

      {/* TODO(PD 문구) */}
      <Button type="submit" disabled={submitting}>
        저장하기
      </Button>
    </form>
  );
}
