import dayjs from "dayjs";
import timezone from "dayjs/plugin/timezone";
import utc from "dayjs/plugin/utc";

dayjs.extend(utc);
dayjs.extend(timezone);

/** docs/PHASES.md 3-1: 날짜는 기기 시간대와 무관하게 항상 Asia/Seoul 기준으로 계산한다. */
export const KST = "Asia/Seoul";

export function kst(value?: dayjs.ConfigType) {
  return dayjs(value).tz(KST);
}

/** 오늘(KST)의 00:00 */
export function kstStartOfToday() {
  return kst().startOf("day");
}

export default dayjs;
