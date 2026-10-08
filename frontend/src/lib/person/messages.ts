// TODO(PD 문구): 모든 문구는 자리표시
// 검증 오류 code(PERSON_ERROR 의 값) → 화면에 보이는 문구. schema.ts 는 코드만 두고 문구는 여기서만 정한다.
import { PERSON_ERROR } from "./schema";

const MESSAGES: Readonly<Record<string, string>> = {
  [PERSON_ERROR.required]: "필수 항목입니다",
  [PERSON_ERROR.dateFormat]: "YYYY-MM-DD 형식으로 입력해 주세요",
  [PERSON_ERROR.dateInvalid]: "없는 날짜입니다",
  [PERSON_ERROR.dateTooEarly]: "1900년 1월 1일부터 입력할 수 있습니다",
  [PERSON_ERROR.dateInFuture]: "오늘 이후 날짜는 입력할 수 없습니다",
  [PERSON_ERROR.timeFormat]: "시간 형식을 확인해 주세요",
  [PERSON_ERROR.permissionRequired]: "확인이 필요합니다",
};

export function personErrorMessage(code: string | undefined) {
  if (code === undefined) return undefined;
  return MESSAGES[code] ?? "입력을 확인해 주세요";
}
