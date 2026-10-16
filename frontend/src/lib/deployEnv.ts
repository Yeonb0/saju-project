// 배포 단계(local · preview · staging · production) 판별과 빌드 검사. 근거: HOSTING-AMPLIFY · R-11 · MOCK-PORT.
// 배포 단계는 NEXT_PUBLIC_DEPLOY_ENV 하나로 정한다. 비어 있으면 Vercel 이 넣어 주는 VERCEL_ENV 를 쓴다 (Vercel 호환).
// next.config.ts 가 이 파일을 import 하므로 next · react 를 import 하지 않는다.

export type DeployEnv = "local" | "preview" | "staging" | "production";

const DEPLOY_ENVS: readonly string[] = [
  "local",
  "preview",
  "staging",
  "production",
];

function isDeployEnv(value: string): value is DeployEnv {
  return DEPLOY_ENVS.includes(value);
}

// 빌드 때 비어 있으면 모니터링이 조용히 꺼지는 키. 로컬 · GitHub CI(local)는 검사하지 않는다.
const REQUIRED_KEYS = [
  "NEXT_PUBLIC_SENTRY_DSN",
  "NEXT_PUBLIC_POSTHOG_KEY",
  "NEXT_PUBLIC_POSTHOG_HOST",
] as const;

// raw: NEXT_PUBLIC_DEPLOY_ENV. vercelEnv: VERCEL_ENV (브라우저 쪽에서는 NEXT_PUBLIC_VERCEL_ENV).
export function resolveDeployEnv(
  raw: string | undefined,
  vercelEnv: string | undefined,
): DeployEnv {
  if (raw !== undefined && raw !== "") {
    if (isDeployEnv(raw)) {
      return raw;
    }
    throw new Error(
      `NEXT_PUBLIC_DEPLOY_ENV 값이 잘못됐다: ${raw} (local · preview · staging · production 중 하나, 대소문자 구분)`,
    );
  }
  if (
    vercelEnv === undefined ||
    vercelEnv === "" ||
    vercelEnv === "development"
  ) {
    return "local";
  }
  if (vercelEnv === "production" || vercelEnv === "preview") {
    return vercelEnv;
  }
  throw new Error(
    `VERCEL_ENV · NEXT_PUBLIC_VERCEL_ENV 값을 알 수 없다: ${vercelEnv} (NEXT_PUBLIC_DEPLOY_ENV 로 배포 단계를 직접 정한다)`,
  );
}

// 빌드 때 한 번 검사한다 (next.config.ts). 로컬 · GitHub CI 는 local 이라 아무 검사도 하지 않는다.
export function assertBuildEnv(env: Record<string, string | undefined>): void {
  // AWS Amplify 는 VERCEL_ENV 가 없어 배포 단계를 알 수 없다 — 비어 있으면 조용히 local 로 빠지므로 막는다.
  if (env.AWS_APP_ID && !env.NEXT_PUBLIC_DEPLOY_ENV) {
    throw new Error(
      "AWS Amplify 빌드(AWS_APP_ID)에는 NEXT_PUBLIC_DEPLOY_ENV 가 필요하다: preview · staging · production 중 하나를 넣어라",
    );
  }

  const deployEnv = resolveDeployEnv(
    env.NEXT_PUBLIC_DEPLOY_ENV,
    env.VERCEL_ENV,
  );

  // MOCK-PORT: 가짜(mock)는 local · preview 에서만 쓴다. 실행 단계 검사는 src/lib/ports/mode.ts.
  if (
    (deployEnv === "staging" || deployEnv === "production") &&
    env.NEXT_PUBLIC_API_MODE === "mock"
  ) {
    throw new Error(
      `NEXT_PUBLIC_API_MODE=mock 은 ${deployEnv} 배포에서 쓸 수 없다 (MOCK-PORT)`,
    );
  }

  if (deployEnv !== "local") {
    const missing = REQUIRED_KEYS.filter((name) => !env[name]);
    if (missing.length > 0) {
      throw new Error(
        `${deployEnv} 배포 환경 변수가 비어 있다: ${missing.join(", ")}`,
      );
    }
  }
}
