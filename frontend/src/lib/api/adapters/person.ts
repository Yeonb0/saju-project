import { z } from "zod";
import { ApiContractError } from "@/lib/api/errors";
import type { ApiClient } from "@/lib/api/http";
import type { PersonFormOutput } from "@/lib/person/schema";
import type { PersonPort, PersonSummary } from "@/lib/ports/person";

const personSchema = z.object({
  id: z.uuid(),
  profileType: z.enum(["SELF", "OTHER"]),
  displayName: z.string().min(1),
});

const peopleSchema = z.object({ items: z.array(personSchema) });

export function createPersonAdapter(client: ApiClient): PersonPort {
  async function create(input: PersonFormOutput, self: boolean) {
    if (self && input.relation !== null) {
      throw new Error("createSelf 는 relation 이 없는 입력만 받는다");
    }
    if (!self && input.relation === null) {
      throw new Error("createOther 는 relation 이 있는 입력만 받는다");
    }
    const { status, data } = await client.request({
      method: "POST",
      path: "/api/v1/people",
      json: {
        profileType: self ? "SELF" : "OTHER",
        displayName: input.name,
        birthDate: input.birthDate,
        birthTime: input.birthTime,
        birthTimeUnknown: input.birthTime === null,
        calendarType: input.calendar.toUpperCase(),
        isLeapMonth: input.isLeapMonth,
        gender: input.gender.toUpperCase(),
        relationType:
          input.relation === null ? null : relationType(input.relation.kind),
        thirdPartyAuthorizationConfirmed: !self,
      },
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
      return parsed.data.items.map(summary);
    },
    createSelf: (input) => create(input, true),
    createOther: (input) => create(input, false),
  };
}

function relationType(
  relation: NonNullable<PersonFormOutput["relation"]>["kind"],
) {
  return relation === "partner"
    ? "LOVER"
    : relation === "custom"
      ? "OTHER"
      : "FAMILY";
}

function summary(person: z.infer<typeof personSchema>): PersonSummary {
  return {
    personId: person.id,
    isSelf: person.profileType === "SELF",
    name: person.displayName,
  };
}
