// 오행분석 진짜 adapter — POST /api/v1/fortune/basic. 근거: Q-30 · Q-35 (personId 단독 입력), API_SPEC 8장,
// OpenAPI BasicSajuRequest · BasicSajuResult (docs/openapi/api-v1.json), ADAPTER-HTTP (createApiClient 로 요청하고
// 응답 data 를 zod 로 런타임 검사한 뒤 포트 모델로 바꾼다. openapi-fetch 는 쓰지 않는다), BASIC-SAJU-PORT.
// TODO(PG-2 세션 adapter): ports/index.ts 연결은 CSRF 출처인 세션 adapter 가 생긴 뒤에 한다.
import { z } from "zod";
import { ApiContractError } from "@/lib/api/errors";
import type { ApiClient } from "@/lib/api/http";
import {
  type BasicSaju,
  type BasicSajuPort,
  ELEMENTS,
} from "@/lib/ports/basicSaju";
import type { components, paths } from "@/types/api";

const PATH = "/api/v1/fortune/basic" satisfies keyof paths;

// 생성 타입은 필드가 모두 선택(?)이라 필수는 여기서 정한다. 화면이 쓰는 값만 검사 · 보관한다 (pillars 등은 BASIC-SAJU-PORT 에 따라 제외)
const resultSchema = z.object({
  fiveElements: z.object({
    // 키가 정확히 5개 — 빠진 원소 · 모르는 원소는 계약 위반
    counts: z.strictObject({
      WOOD: z.number().int().min(0),
      FIRE: z.number().int().min(0),
      EARTH: z.number().int().min(0),
      METAL: z.number().int().min(0),
      WATER: z.number().int().min(0),
    }),
  }),
  birthTimeKnown: z.boolean(),
  calculationVersion: z.string().min(1),
});

export function createBasicSajuAdapter(client: ApiClient): BasicSajuPort {
  return {
    async getBasicSaju(personId: string): Promise<BasicSaju> {
      // personId 만 보낸다 — 생년정보 원문 필드는 보내지 않는다 (Q-30 · X-01). 계산 요청이라 Idempotency-Key 없음 (API_SPEC 8장)
      const body = {
        personId,
      } satisfies paths[typeof PATH]["post"]["requestBody"]["content"]["application/json"];
      const { status, data } = await client.request({
        method: "POST",
        path: PATH,
        json: body,
      });

      const parsed = resultSchema.safeParse(data);
      if (!parsed.success) {
        throw new ApiContractError(status, "오행분석 응답 모양이 다르다");
      }
      // 필드 이름이 생성 타입과 달라지면 typecheck 가 깨진다 — 객체 리터럴이라 초과 속성 검사가 된다
      // (생성 타입은 필드가 모두 선택(?)이라, 리터럴이 아닌 값에 satisfies 를 붙이면 이름이 바뀌어도 통과한다)
      const wire = {
        fiveElements: { counts: parsed.data.fiveElements.counts },
        birthTimeKnown: parsed.data.birthTimeKnown,
        calculationVersion: parsed.data.calculationVersion,
      } satisfies components["schemas"]["BasicSajuResult"];

      const { counts } = wire.fiveElements;
      return {
        fiveElements: ELEMENTS.map((element) => ({
          element,
          count: counts[element],
        })),
        birthTimeKnown: wire.birthTimeKnown,
        calculationVersion: wire.calculationVersion,
      };
    },
  };
}
