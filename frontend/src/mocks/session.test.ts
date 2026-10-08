import { describe, expect, it, vi } from "vitest";
import { kst } from "@/lib/date";
import { createPersonSchema } from "@/lib/person/schema";
import { createFakeAccount } from "./account";
import { createFakePersonPort } from "./person";
import { createFakeSessionPort } from "./session";

describe("가짜 세션 포트 (MOCK-PORT)", () => {
  it("기본은 signed_out", async () => {
    await expect(createFakeSessionPort().getSession()).resolves.toEqual({
      status: "signed_out",
    });
  });

  it("signed_out: 로그인하면 복귀 경로로 한 번 보내고 본인 정보 있음", async () => {
    const port = createFakeSessionPort({ scenario: "signed_out" });
    const navigate = vi.fn();
    await port.startLogin("/wallet", navigate);
    expect(navigate).toHaveBeenCalledTimes(1);
    expect(navigate).toHaveBeenCalledWith("/wallet");
    await expect(port.getSession()).resolves.toMatchObject({
      status: "signed_in",
      hasPrimaryPerson: true,
    });
  });

  it("new_user: 로그인 후 본인 정보 없음", async () => {
    const port = createFakeSessionPort({ scenario: "new_user" });
    await expect(port.getSession()).resolves.toEqual({ status: "signed_out" });
    await port.startLogin("/wallet", vi.fn());
    await expect(port.getSession()).resolves.toMatchObject({
      status: "signed_in",
      hasPrimaryPerson: false,
    });
  });

  it("signed_in · signed_in_without_person 은 처음부터 로그인", async () => {
    await expect(
      createFakeSessionPort({ scenario: "signed_in" }).getSession(),
    ).resolves.toMatchObject({ status: "signed_in", hasPrimaryPerson: true });
    await expect(
      createFakeSessionPort({
        scenario: "signed_in_without_person",
      }).getSession(),
    ).resolves.toMatchObject({ status: "signed_in", hasPrimaryPerson: false });
  });

  it("바깥 주소는 / 로 보낸다", async () => {
    const port = createFakeSessionPort();
    const navigate = vi.fn();
    await port.startLogin("//evil.example", navigate);
    expect(navigate).toHaveBeenCalledWith("/");
  });

  it("logout 후 signed_out", async () => {
    const port = createFakeSessionPort({ scenario: "signed_in" });
    await port.logout();
    await expect(port.getSession()).resolves.toEqual({ status: "signed_out" });
  });

  it("돌려준 객체를 바꿔도 다음 결과는 그대로", async () => {
    const port = createFakeSessionPort({ scenario: "signed_in" });
    const first = await port.getSession();
    (first as { status: string }).status = "signed_out";
    await expect(port.getSession()).resolves.toMatchObject({
      status: "signed_in",
    });
  });
});

describe("가짜 계정 공유 (MOCK-PORT)", () => {
  it("new_user: 세션 · 인물 포트가 같은 계정 — 로그인 후 본인 저장하면 hasPrimaryPerson true", async () => {
    const account = createFakeAccount("new_user");
    const session = createFakeSessionPort({ account });
    const people = createFakePersonPort(account);
    await session.startLogin("/onboarding", vi.fn());
    await expect(session.getSession()).resolves.toMatchObject({
      hasPrimaryPerson: false,
    });
    await people.createSelf(
      createPersonSchema({
        kind: "self",
        today: kst("2026-10-04T12:00:00+09:00").startOf("day"),
      }).parse({
        // 픽스처일 뿐이다
        name: "FIXTURE",
        calendar: "solar",
        isLeapMonth: false,
        birthDate: "2008-03-15",
        timeUnknown: true,
        birthTime: "",
        gender: "unspecified",
        relation: null,
        relationText: "",
        permissionConfirmed: false,
      }),
    );
    await expect(session.getSession()).resolves.toMatchObject({
      hasPrimaryPerson: true,
    });
  });
});
