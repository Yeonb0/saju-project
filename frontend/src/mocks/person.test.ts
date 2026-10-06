import { describe, expect, it } from "vitest";
import { ApiError } from "@/lib/api/errors";
import { kst } from "@/lib/date";
import { createPersonSchema, type PersonFormInput } from "@/lib/person/schema";
import { createFakeAccount } from "./account";
import { createFakePersonPort } from "./person";
import { createFakeSessionPort } from "./session";

// 픽스처일 뿐이며 실제 사용자와 무관하다. 폼 검증을 거친 값만 포트에 넣는다.
const TODAY = kst("2026-10-04T12:00:00+09:00").startOf("day");

const BASE: PersonFormInput = {
  name: "FIXTURE",
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

const selfInput = () =>
  createPersonSchema({ kind: "self", today: TODAY }).parse(BASE);
const otherInput = () =>
  createPersonSchema({ kind: "other", today: TODAY }).parse({
    ...BASE,
    relation: "mother",
    permissionConfirmed: true,
  });

describe("가짜 인물 포트 (MOCK-PORT)", () => {
  it("로그아웃 계정에서 createSelf → 401 AUTHENTICATION_REQUIRED", async () => {
    const port = createFakePersonPort(createFakeAccount("signed_out"));
    await expect(port.createSelf(selfInput())).rejects.toMatchObject({
      status: 401,
      code: "AUTHENTICATION_REQUIRED",
    });
    await expect(port.createSelf(selfInput())).rejects.toBeInstanceOf(ApiError);
  });

  it("createSelf: isSelf true, 같은 계정의 세션은 hasPrimaryPerson true", async () => {
    const account = createFakeAccount("signed_in_without_person");
    const session = createFakeSessionPort({ account });
    await expect(session.getSession()).resolves.toMatchObject({
      hasPrimaryPerson: false,
    });
    const saved = await createFakePersonPort(account).createSelf(selfInput());
    expect(saved).toMatchObject({ isSelf: true, name: "FIXTURE" });
    expect(saved.personId).toMatch(/^[0-9a-f-]{36}$/);
    await expect(session.getSession()).resolves.toMatchObject({
      hasPrimaryPerson: true,
    });
  });

  it("입력 종류가 맞지 않으면 던진다", async () => {
    const port = createFakePersonPort(createFakeAccount("signed_in"));
    await expect(port.createSelf(otherInput())).rejects.toThrow();
    await expect(port.createOther(selfInput())).rejects.toThrow();
  });

  it("createOther: 10명까지 성공, 11번째는 409 PERSON_LIMIT_EXCEEDED", async () => {
    const port = createFakePersonPort(createFakeAccount("signed_in"));
    for (let i = 0; i < 10; i++) {
      await expect(port.createOther(otherInput())).resolves.toMatchObject({
        isSelf: false,
      });
    }
    await expect(port.createOther(otherInput())).rejects.toMatchObject({
      status: 409,
      code: "PERSON_LIMIT_EXCEEDED",
    });
  });

  it("list: 로그아웃이면 401, 로그인이면 본인 먼저 · 저장한 타인 순서", async () => {
    await expect(
      createFakePersonPort(createFakeAccount("signed_out")).list(),
    ).rejects.toMatchObject({ status: 401 });

    const port = createFakePersonPort(createFakeAccount("signed_in"));
    const other = await port.createOther(otherInput());
    const people = await port.list();
    expect(people.map((p) => p.isSelf)).toEqual([true, false]);
    expect(people[1]).toEqual(other);
  });
});
