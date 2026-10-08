import { afterEach, describe, expect, it, vi } from "vitest";
import { kst } from "@/lib/date";
import {
  createPersonSchema,
  EMPTY_PERSON_FORM,
  PERSON_ERROR,
  type PersonFormInput,
} from "./schema";

// 픽스처일 뿐이며 실제 가격 · 규칙과 무관하다. 날짜 · 이름 · 시간은 검증 경계를 보기 위한 임의 값이다.
const TODAY = kst("2026-10-04T12:00:00+09:00").startOf("day");

const SELF: PersonFormInput = {
  name: "  픽스처  ",
  calendar: "solar",
  isLeapMonth: false,
  birthDate: "2008-03-15",
  timeUnknown: false,
  birthTime: "07:30",
  gender: "female",
  relation: null,
  relationText: "",
  permissionConfirmed: false,
};

const OTHER: PersonFormInput = {
  ...SELF,
  relation: "mother",
  permissionConfirmed: true,
};

function errorsOf(input: PersonFormInput, kind: "self" | "other" = "self") {
  const result = createPersonSchema({ kind, today: TODAY }).safeParse(input);
  if (result.success) return {};
  return Object.fromEntries(
    result.error.issues.map((issue) => [issue.path.join("."), issue.message]),
  );
}

function parse(input: PersonFormInput, kind: "self" | "other" = "self") {
  return createPersonSchema({ kind, today: TODAY }).parse(input);
}

describe("이름", () => {
  it("앞뒤 공백을 제거한다", () => {
    expect(parse(SELF).name).toBe("픽스처");
  });

  it("공백만 있으면 필수 오류", () => {
    expect(errorsOf({ ...SELF, name: "   " })).toEqual({
      name: PERSON_ERROR.required,
    });
  });
});

describe("생년월일", () => {
  it("1900-01-01 은 통과, 1899-12-31 은 오류", () => {
    expect(errorsOf({ ...SELF, birthDate: "1900-01-01" })).toEqual({});
    expect(errorsOf({ ...SELF, birthDate: "1899-12-31" })).toEqual({
      birthDate: PERSON_ERROR.dateTooEarly,
    });
  });

  it("오늘은 통과, 내일은 오류", () => {
    expect(errorsOf({ ...SELF, birthDate: "2026-10-04" })).toEqual({});
    expect(errorsOf({ ...SELF, birthDate: "2026-10-05" })).toEqual({
      birthDate: PERSON_ERROR.dateInFuture,
    });
  });

  it("양력은 없는 날짜를 막는다 (윤년 확인)", () => {
    expect(errorsOf({ ...SELF, birthDate: "2023-02-29" })).toEqual({
      birthDate: PERSON_ERROR.dateInvalid,
    });
    expect(errorsOf({ ...SELF, birthDate: "2024-02-29" })).toEqual({});
  });

  it("음력은 30일까지 허용한다 — 양력 달력으로 막지 않는다", () => {
    expect(
      errorsOf({ ...SELF, calendar: "lunar", birthDate: "1990-02-30" }),
    ).toEqual({});
    expect(
      errorsOf({ ...SELF, calendar: "lunar", birthDate: "1990-02-31" }),
    ).toEqual({ birthDate: PERSON_ERROR.dateInvalid });
  });

  it("형식이 다르면 형식 오류, 비어 있으면 필수 오류", () => {
    expect(errorsOf({ ...SELF, birthDate: "2008/03/15" })).toEqual({
      birthDate: PERSON_ERROR.dateFormat,
    });
    expect(errorsOf({ ...SELF, birthDate: "" })).toEqual({
      birthDate: PERSON_ERROR.required,
    });
  });
});

describe("오늘 기준은 KST", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it("UTC 로는 전날이어도 KST 날짜가 오늘이면 통과한다", () => {
    // 2026-10-03T15:30Z = 2026-10-04 00:30 KST
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-10-03T15:30:00Z"));
    const schema = createPersonSchema({ kind: "self" });
    expect(schema.safeParse({ ...SELF, birthDate: "2026-10-04" }).success).toBe(
      true,
    );
    expect(schema.safeParse({ ...SELF, birthDate: "2026-10-05" }).success).toBe(
      false,
    );
  });
});

describe("윤달", () => {
  it("양력이면 윤달 값을 버린다", () => {
    expect(parse({ ...SELF, isLeapMonth: true }).isLeapMonth).toBe(false);
  });

  it("음력이면 윤달 값을 유지한다", () => {
    expect(
      parse({ ...SELF, calendar: "lunar", isLeapMonth: true }).isLeapMonth,
    ).toBe(true);
  });
});

describe("태어난 시간", () => {
  it("시간 모름이면 시간 값을 null 로 바꾼다", () => {
    expect(
      parse({ ...SELF, timeUnknown: true, birthTime: "07:30" }).birthTime,
    ).toBeNull();
  });

  it("시간을 모르지 않으면 필수", () => {
    expect(errorsOf({ ...SELF, birthTime: "" })).toEqual({
      birthTime: PERSON_ERROR.required,
    });
  });

  it("24:00 · 7:30 은 형식 오류", () => {
    expect(errorsOf({ ...SELF, birthTime: "24:00" })).toEqual({
      birthTime: PERSON_ERROR.timeFormat,
    });
    expect(errorsOf({ ...SELF, birthTime: "7:30" })).toEqual({
      birthTime: PERSON_ERROR.timeFormat,
    });
  });
});

describe("고르는 값", () => {
  it("달력 · 성별을 고르지 않으면 필수 오류", () => {
    const input = {
      ...SELF,
      calendar: undefined,
      gender: undefined,
    } as unknown as PersonFormInput;
    expect(errorsOf(input)).toEqual({
      calendar: PERSON_ERROR.required,
      gender: PERSON_ERROR.required,
    });
  });
});

describe("타인 정보", () => {
  it("관계가 없으면 필수 오류", () => {
    expect(errorsOf({ ...OTHER, relation: null }, "other")).toEqual({
      relation: PERSON_ERROR.required,
    });
  });

  it("직접 입력이면 관계 글자가 필수", () => {
    expect(
      errorsOf({ ...OTHER, relation: "custom", relationText: "  " }, "other"),
    ).toEqual({ relationText: PERSON_ERROR.required });
    expect(
      parse({ ...OTHER, relation: "custom", relationText: " 이모 " }, "other")
        .relation,
    ).toEqual({ kind: "custom", label: "이모" });
  });

  it("권한 확인 체크가 없으면 오류", () => {
    expect(errorsOf({ ...OTHER, permissionConfirmed: false }, "other")).toEqual(
      { permissionConfirmed: PERSON_ERROR.permissionRequired },
    );
  });

  it("본인은 관계 · 권한 확인을 보지 않고 관계를 null 로 낸다", () => {
    expect(errorsOf(SELF, "self")).toEqual({});
    expect(parse({ ...SELF, relation: "mother" }, "self").relation).toBeNull();
  });
});

describe("빈 선택값 (한 번 제출에 모든 오류)", () => {
  it("모든 칸이 비면 이름 · 생년월일 · 시간 · 달력 · 성별 필수 오류가 함께 나온다", () => {
    expect(errorsOf(EMPTY_PERSON_FORM)).toEqual({
      name: PERSON_ERROR.required,
      birthDate: PERSON_ERROR.required,
      birthTime: PERSON_ERROR.required,
      calendar: PERSON_ERROR.required,
      gender: PERSON_ERROR.required,
    });
  });

  it("달력 · 성별이 비어도 생년월일 오류가 함께 나온다", () => {
    expect(
      errorsOf({
        ...SELF,
        calendar: undefined,
        gender: undefined,
        birthDate: "2026-10-05",
      }),
    ).toEqual({
      calendar: PERSON_ERROR.required,
      gender: PERSON_ERROR.required,
      birthDate: PERSON_ERROR.dateInFuture,
    });
  });

  it("성별 빈 문자열 · 달력 null 도 필수 오류", () => {
    expect(errorsOf({ ...SELF, gender: "", calendar: null })).toEqual({
      calendar: PERSON_ERROR.required,
      gender: PERSON_ERROR.required,
    });
  });

  it("시간 모름이면 birthTime undefined 도 통과하고 출력은 null", () => {
    expect(
      parse({ ...SELF, timeUnknown: true, birthTime: undefined }).birthTime,
    ).toBeNull();
  });

  it("시간 모름이 아닌데 birthTime undefined 면 필수 오류", () => {
    expect(
      errorsOf({ ...SELF, timeUnknown: false, birthTime: undefined }),
    ).toEqual({ birthTime: PERSON_ERROR.required });
  });
});
