import { describe, expect, it, vi } from "vitest";
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
