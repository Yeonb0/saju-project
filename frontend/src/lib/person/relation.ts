// 화면 관계 → 서버 관계 표 한 곳. 근거: API_SPEC 4장 화면 관계 매핑 (A-07 · Q-35).
// 인물 생성 요청(adapters)과 가짜 인물(mocks)이 같은 표를 쓴다. 생성 타입과의 대조는 adapters 에서 한다.
import type { RelationType } from "@/lib/ports/fortune";
import type { RELATIONS } from "./schema";

export const RELATION_TYPE_OF_KIND: Record<
  (typeof RELATIONS)[number],
  RelationType
> = { mother: "FAMILY", father: "FAMILY", partner: "LOVER", custom: "OTHER" };
