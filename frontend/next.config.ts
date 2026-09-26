import type { NextConfig } from "next";

// docs/PHASES.md 3-1: 백엔드와 무관하게 `/api/*`를 rewrites로 프록시해 쿠키·CORS 문제를 없앤다.
// 백엔드 주소는 D-14(백엔드 언어) 확정 전이므로 환경 변수로만 받는다. 값이 없으면 프록시하지 않는다.
const apiProxyTarget = process.env.API_PROXY_TARGET;

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
