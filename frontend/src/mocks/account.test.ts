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
});
