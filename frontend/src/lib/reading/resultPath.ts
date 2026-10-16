// 구매가 끝난 운세 결과 화면 경로 (DIRECT-PAY). 수능운은 /suneung/r, 그 밖의 5종은 /fortune/r.
// 3단계에서 앞 화면(CSAT-01 · FORT-02 · 03 · MATCH-03)이 갖던 규칙을 결제 복귀 화면이 쓰도록 한 곳으로 옮겼다.
import type { FortuneSelection } from "@/lib/ports/fortune";

// UNKNOWN 은 받지 않는다 — FortuneSelection 의 fortuneType 은 이미 UNKNOWN 을 뺀 타입이다
export function readingResultPath(
  fortuneType: FortuneSelection["fortuneType"],
  readingId: string,
): string {
  const id = encodeURIComponent(readingId);
  return fortuneType === "SUNEUNG" ? `/suneung/r/${id}` : `/fortune/r/${id}`;
}
