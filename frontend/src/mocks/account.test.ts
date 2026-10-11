import { describe, expect, it } from "vitest";
import { createFakeAccount, type FakeSessionScenario } from "./account";

describe("가짜 계정 (MOCK-PORT)", () => {
  const cases: [FakeSessionScenario, boolean, boolean][] = [
    ["signed_out", false, true],
    ["new_user", false, false],
    ["signed_in", true, true],
    ["signed_in_without_person", true, false],
  ];

  it.each(
    cases,
  )("%s: 처음 로그인 %s · 본인 있음 %s", (scenario, signedIn, hasSelf) => {
    const account = createFakeAccount(scenario);
    expect(account.isSignedIn()).toBe(signedIn);
    expect(account.getSelf() !== null).toBe(hasSelf);
    expect(account.getOthers()).toHaveLength(0);
  });

  it("본인 있음 상태의 본인은 픽스처", () => {
    expect(createFakeAccount("signed_in").getSelf()).toMatchObject({
      isSelf: true,
      name: "FIXTURE",
    });
  });

  it("signed_in_with_other: 로그인 · 본인은 픽스처 · 저장된 타인 1명", () => {
    const account = createFakeAccount("signed_in_with_other");
    expect(account.isSignedIn()).toBe(true);
    expect(account.getSelf()).toMatchObject({ isSelf: true, name: "FIXTURE" });
    expect(account.getOthers()).toEqual([
      {
        personId: "77777777-7777-4777-8777-777777777777",
        isSelf: false,
        name: "FIXTURE OTHER",
      },
    ]);
  });

  it("signed_in 은 저장된 타인이 없다 (기존 동작 유지)", () => {
    expect(createFakeAccount("signed_in").getOthers()).toHaveLength(0);
  });
});
