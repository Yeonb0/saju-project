import { z } from "zod";
import { ApiContractError } from "@/lib/api/errors";
import type { ApiClient } from "@/lib/api/http";
import type {
  CALENDARS,
  GENDERS,
  PersonFormOutput,
  RELATIONS,
} from "@/lib/person/schema";
import type { PersonPort, PersonSummary } from "@/lib/ports/person";
import type { components } from "@/types/api";

const personSchema = z.object({
  id: z.uuid(),
  profileType: z.enum(["SELF", "OTHER"]),
  displayName: z.string().min(1),
});

type CreatePerson = components["schemas"]["CreatePersonRequest"];

// 키가 빠지면 typecheck 가 깨진다. 관계는 API_SPEC 4장 화면 관계 매핑 (A-07 · Q-35)
const CALENDAR_TYPE: Record<
  (typeof CALENDARS)[number],
  CreatePerson["calendarType"]
> = { solar: "SOLAR", lunar: "LUNAR" };
const GENDER: Record<(typeof GENDERS)[number], CreatePerson["gender"]> = {
  male: "MALE",
  female: "FEMALE",
  unspecified: "UNSPECIFIED",
};
const RELATION_TYPE: Record<
  (typeof RELATIONS)[number],
  NonNullable<CreatePerson["relationType"]>
> = { mother: "FAMILY", father: "FAMILY", partner: "LOVER", custom: "OTHER" };

const peopleSchema = z.object({ items: z.array(personSchema) });

export function createPersonAdapter(client: ApiClient): PersonPort {
  async function create(input: PersonFormOutput, self: boolean) {
    if (self && input.relation !== null) {
      throw new Error("createSelf 는 relation 이 없는 입력만 받는다");
    }
    if (!self && input.relation === null) {
      throw new Error("createOther 는 relation 이 있는 입력만 받는다");
    }
    // 생성 타입에 null 이 없으므로 값이 없으면 키를 뺀다
    // TODO(A-07 · BE-B): 직접 입력 관계의 이름(relation.label)은 서버 필드가 없어 보내지 않는다
    const body = {
      profileType: self ? "SELF" : "OTHER",
      displayName: input.name,
      birthDate: input.birthDate,
      ...(input.birthTime === null ? {} : { birthTime: input.birthTime }),
      birthTimeUnknown: input.birthTime === null,
      calendarType: CALENDAR_TYPE[input.calendar],
      isLeapMonth: input.isLeapMonth,
      gender: GENDER[input.gender],
      ...(input.relation === null
        ? {}
        : { relationType: RELATION_TYPE[input.relation.kind] }),
      thirdPartyAuthorizationConfirmed: !self,
    } satisfies components["schemas"]["CreatePersonRequest"];
    const { status, data } = await client.request({
      method: "POST",
      path: "/api/v1/people",
      json: body,
    });
    const parsed = personSchema.safeParse(data);
    if (!parsed.success) {
      throw new ApiContractError(status, "인물 생성 응답 모양이 다르다");
    }
    return summary(parsed.data);
  }

  return {
    async list() {
      const { status, data } = await client.request({
        method: "GET",
        path: "/api/v1/people",
      });
      const parsed = peopleSchema.safeParse(data);
      if (!parsed.success) {
        throw new ApiContractError(status, "인물 목록 응답 모양이 다르다");
      }
      // 응답 순서와 상관없이 본인을 맨 앞으로, 나머지는 서버 순서 그대로
      const people = parsed.data.items;
      return [
        ...people.filter((p) => p.profileType === "SELF"),
        ...people.filter((p) => p.profileType !== "SELF"),
      ].map(summary);
    },
    createSelf: (input) => create(input, true),
    createOther: (input) => create(input, false),
  };
}

function summary(person: z.infer<typeof personSchema>): PersonSummary {
  return {
    personId: person.id,
    isSelf: person.profileType === "SELF",
    name: person.displayName,
  };
}
