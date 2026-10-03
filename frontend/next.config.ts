import type { NextConfig } from "next";

// frontend/docs/FRONTEND.md · I-02: `/api/*` 를 rewrites 로 백엔드에 프록시해 같은 출처로 쿠키 · CORS 문제를 없앤다.
// 백엔드 주소는 배포 환경(local · staging · production)마다 달라 환경 변수로만 받는다 (I-01). 값이 없으면 프록시하지 않는다.
const apiProxyTarget = process.env.API_PROXY_TARGET;

// Vercel 에서만 검사 — 키 없이 배포되면 모니터링이 조용히 꺼진다. 로컬 · GitHub CI 는 VERCEL 이 없어 건너뛴다.
if (process.env.VERCEL === "1") {
  const required = [
    "NEXT_PUBLIC_SENTRY_DSN",
    "NEXT_PUBLIC_POSTHOG_KEY",
    "NEXT_PUBLIC_POSTHOG_HOST",
  ];
  const missing = required.filter((name) => !process.env[name]);
  if (missing.length > 0) {
    throw new Error(`Vercel 환경 변수가 비어 있다: ${missing.join(", ")}`);
  }
}

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
