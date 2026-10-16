// 포트 구현 선택: 가짜(src/mocks) 또는 진짜(src/lib/api/adapters). 근거: docs/FRONTEND.md 1-2 (MOCK-PORT) · HOSTING-AMPLIFY · R-11.
// 환경 변수 NEXT_PUBLIC_API_MODE — 비우거나 "real" 이면 진짜, "mock" 이면 가짜. 그 밖의 값은 오류.
// 가짜는 배포 단계가 local · preview 일 때만 쓴다(배포 단계는 src/lib/deployEnv.ts). staging · production 에서 켜져 있으면
// 조용히 넘어가지 않고 던진다 (빌드 단계 검사는 next.config.ts, 여기는 실행 단계 검사).

import { type DeployEnv, resolveDeployEnv } from "../deployEnv";

export type ApiMode = "mock" | "real";

export function resolveApiMode(
  raw: string | undefined,
  deployEnv: DeployEnv,
): ApiMode {
  let mode: ApiMode;
  if (raw === undefined || raw === "" || raw === "real") {
    mode = "real";
  } else if (raw === "mock") {
    mode = "mock";
  } else {
    throw new Error(`NEXT_PUBLIC_API_MODE 값이 잘못됐다: ${raw}`);
  }
  if (
    mode === "mock" &&
    (deployEnv === "staging" || deployEnv === "production")
  ) {
    throw new Error(
      `${deployEnv} 배포에서는 가짜 구현(mock)을 쓸 수 없다: NEXT_PUBLIC_API_MODE (MOCK-PORT)`,
    );
  }
  return mode;
}

// process.env.NEXT_PUBLIC_* 는 빌드 때 글자 그대로 치환되므로 이 형태로 직접 읽는다 (구조 분해 · 변수 경유 금지).
export const API_MODE: ApiMode = resolveApiMode(
  process.env.NEXT_PUBLIC_API_MODE,
  resolveDeployEnv(
    process.env.NEXT_PUBLIC_DEPLOY_ENV,
    process.env.NEXT_PUBLIC_VERCEL_ENV,
  ),
);
