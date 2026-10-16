import type { NextConfig } from "next";
import { assertBuildEnv } from "./src/lib/deployEnv";

// frontend/docs/FRONTEND.md · I-02: `/api/*` 를 rewrites 로 백엔드에 프록시해 같은 출처로 쿠키 · CORS 문제를 없앤다.
// 백엔드 주소는 배포 환경(local · staging · production)마다 달라 환경 변수로만 받는다 (I-01). 값이 없으면 프록시하지 않는다.
const apiProxyTarget = process.env.API_PROXY_TARGET;

// 배포 단계(NEXT_PUBLIC_DEPLOY_ENV, 비면 Vercel 의 VERCEL_ENV)로 빌드를 검사한다 (HOSTING-AMPLIFY · R-11 · MOCK-PORT).
// 키 없이 배포되면 모니터링이 조용히 꺼지고, 운영 · 스테이징에 가짜(mock)가 켜지면 안 된다. 로컬 · GitHub CI 는 local 이라 건너뛴다.
// 규칙은 src/lib/deployEnv.ts, 실행 단계 검사는 src/lib/ports/mode.ts.
assertBuildEnv(process.env);

const nextConfig: NextConfig = {
  reactCompiler: true,
  async rewrites() {
    if (!apiProxyTarget) {
      return [];
    }
    return [
      {
        source: "/api/:path*",
        destination: `${apiProxyTarget}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
