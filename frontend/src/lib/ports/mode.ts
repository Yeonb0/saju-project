// 포트 구현 선택: 가짜(src/mocks) 또는 진짜(src/lib/api/adapters). 근거: docs/FRONTEND.md 1-2 (MOCK-PORT).
// 환경 변수 NEXT_PUBLIC_API_MODE — 비우거나 "real" 이면 진짜, "mock" 이면 가짜. 그 밖의 값은 오류.
// 가짜는 개발 서버 · Vercel 미리보기에서만 쓴다. 운영 배포에서 켜져 있으면 조용히 넘어가지 않고 던진다
// (빌드 단계 검사는 next.config.ts, 여기는 실행 단계 검사).

export type ApiMode = "mock" | "real";

export function resolveApiMode(
  raw: string | undefined,
  vercelEnv: string | undefined,
): ApiMode {
  let mode: ApiMode;
  if (raw === undefined || raw === "" || raw === "real") {
    mode = "real";
  } else if (raw === "mock") {
    mode = "mock";
  } else {
    throw new Error(`NEXT_PUBLIC_API_MODE 값이 잘못됐다: ${raw}`);
  }
  if (mode === "mock" && vercelEnv === "production") {
    throw new Error("운영 배포에서는 가짜 구현(mock)을 쓸 수 없다 (MOCK-PORT)");
  }
  return mode;
}

// process.env.NEXT_PUBLIC_* 는 빌드 때 글자 그대로 치환되므로 이 형태로 직접 읽는다.
export const API_MODE: ApiMode = resolveApiMode(
  process.env.NEXT_PUBLIC_API_MODE,
  process.env.NEXT_PUBLIC_VERCEL_ENV,
);
