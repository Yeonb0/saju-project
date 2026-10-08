// 공통 응답 · 오류 껍데기의 런타임 검사.
// 근거: docs/COMMON_RESPONSE_AND_ERROR_CODES.md 2 · 3장, BE 골격 a605afa 의 ApiResponse(data, traceId) ·
// ApiError(code, message, traceId, fieldErrors). details 는 아직 BE 골격에 없어 선택 항목이다 (Q-18).
// 업무 payload(data 안쪽)는 검사하지 않는다 — 그것은 adapters 가 OpenAPI 생성 타입으로 한다 (MOCK-PORT).
// OpenAPI 를 받으면 생성 타입과 이 모양을 대조한다.
import { z } from "zod";

export const successEnvelopeSchema = z.object({
  data: z.unknown(),
  traceId: z.string(),
});

export const errorEnvelopeSchema = z.object({
  code: z.string().min(1),
  message: z.string(),
  traceId: z.string(),
  fieldErrors: z.array(z.object({ field: z.string(), reason: z.string() })),
  details: z
    .record(
      z.string(),
      z.union([z.string(), z.number(), z.boolean(), z.null()]),
    )
    .optional(),
});
