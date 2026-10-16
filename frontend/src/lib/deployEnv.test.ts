import { describe, expect, it } from "vitest";
import { assertBuildEnv, resolveDeployEnv } from "./deployEnv";

// HOSTING-AMPLIFY · R-11 · MOCK-PORT. 이 파일의 키 · 값은 픽스처일 뿐, 실제 키와 무관하다.

describe("resolveDeployEnv", () => {
  it("a. 네 값은 그대로, 그 밖의 값(prod · Production)은 던진다", () => {
    for (const value of [
      "local",
      "preview",
      "staging",
      "production",
    ] as const) {
      expect(resolveDeployEnv(value, undefined)).toBe(value);
    }
    expect(() => resolveDeployEnv("prod", undefined)).toThrow(
      "NEXT_PUBLIC_DEPLOY_ENV",
    );
    expect(() => resolveDeployEnv("Production", undefined)).toThrow(
      "NEXT_PUBLIC_DEPLOY_ENV",
    );
  });

  it("b. raw 가 비면 vercelEnv 로 정한다", () => {
    expect(resolveDeployEnv(undefined, "production")).toBe("production");
    expect(resolveDeployEnv("", "preview")).toBe("preview");
    expect(resolveDeployEnv(undefined, "development")).toBe("local");
    expect(resolveDeployEnv(undefined, undefined)).toBe("local");
    expect(resolveDeployEnv("", "")).toBe("local");
    expect(() => resolveDeployEnv(undefined, "foo")).toThrow("VERCEL_ENV");
  });

  it("c. raw 가 있으면 vercelEnv 보다 우선한다", () => {
    expect(resolveDeployEnv("staging", "production")).toBe("staging");
  });
});

describe("assertBuildEnv", () => {
  const keys = {
    NEXT_PUBLIC_SENTRY_DSN: "fixture-dsn",
    NEXT_PUBLIC_POSTHOG_KEY: "fixture-key",
    NEXT_PUBLIC_POSTHOG_HOST: "fixture-host",
  };

  it("d. 아무것도 없으면(로컬 · GitHub CI) 통과한다", () => {
    expect(() => assertBuildEnv({})).not.toThrow();
  });

  it("e. Amplify 빌드(AWS_APP_ID)에 NEXT_PUBLIC_DEPLOY_ENV 가 없으면 던진다", () => {
    expect(() => assertBuildEnv({ AWS_APP_ID: "x", ...keys })).toThrow(
      "NEXT_PUBLIC_DEPLOY_ENV",
    );
  });

  it("f. Amplify + production + 키 + API_MODE real 은 통과한다", () => {
    expect(() =>
      assertBuildEnv({
        AWS_APP_ID: "x",
        NEXT_PUBLIC_DEPLOY_ENV: "production",
        NEXT_PUBLIC_API_MODE: "real",
        ...keys,
      }),
    ).not.toThrow();
  });

  it("g. staging · production 의 mock 은 던진다", () => {
    for (const deployEnv of ["production", "staging"]) {
      expect(() =>
        assertBuildEnv({
          NEXT_PUBLIC_DEPLOY_ENV: deployEnv,
          NEXT_PUBLIC_API_MODE: "mock",
          ...keys,
        }),
      ).toThrow("NEXT_PUBLIC_API_MODE");
    }
  });

  it.each(
    Object.keys(keys),
  )("h. production 에서 %s 가 비면 그 이름을 담아 던진다", (name) => {
    expect(() =>
      assertBuildEnv({
        NEXT_PUBLIC_DEPLOY_ENV: "production",
        ...keys,
        [name]: "",
      }),
    ).toThrow(name);
  });

  it("i. preview 에서도 키가 없으면 던진다", () => {
    expect(() => assertBuildEnv({ NEXT_PUBLIC_DEPLOY_ENV: "preview" })).toThrow(
      "NEXT_PUBLIC_SENTRY_DSN",
    );
  });

  it("j. preview 의 mock 과 local 의 키 없는 mock 은 통과한다", () => {
    expect(() =>
      assertBuildEnv({
        NEXT_PUBLIC_DEPLOY_ENV: "preview",
        NEXT_PUBLIC_API_MODE: "mock",
        ...keys,
      }),
    ).not.toThrow();
    expect(() =>
      assertBuildEnv({
        NEXT_PUBLIC_DEPLOY_ENV: "local",
        NEXT_PUBLIC_API_MODE: "mock",
      }),
    ).not.toThrow();
  });

  it("k. Vercel 호환 — VERCEL_ENV 로 단계를 정한다", () => {
    expect(() =>
      assertBuildEnv({
        VERCEL_ENV: "production",
        NEXT_PUBLIC_API_MODE: "mock",
        ...keys,
      }),
    ).toThrow("NEXT_PUBLIC_API_MODE");
    expect(() =>
      assertBuildEnv({
        VERCEL_ENV: "preview",
        NEXT_PUBLIC_API_MODE: "mock",
        ...keys,
      }),
    ).not.toThrow();
  });
});
