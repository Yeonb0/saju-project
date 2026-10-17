import type { NextConfig } from "next";

// SITE-DOMAIN · HOSTING-AMPLIFY: 기준 주소는 하나다 (Origin 검사 FRONTEND_BASE_URL 과 같은 값).
// Amplify 앱 수준 리디렉션 규칙은 SSR 앱에서 적용되지 않음을 운영에서 확인했다 (www 가 그대로 200).
// 그래서 앱 안(next.config.ts redirects)에서 www 를 기준 주소로 영구 이동(301)시킨다.
// 이 두 문자열은 이 파일에만 둔다. 환경 변수로 읽지 않는다.
export const CANONICAL_ORIGIN = "https://ppugi-saju.kr";
export const WWW_HOST = "www.ppugi-saju.kr";

type Redirects = NonNullable<NextConfig["redirects"]>;

// www 는 정식 진입 주소가 아니므로 /api/* 를 포함한 모든 경로를 같은 규칙으로 이동한다 (예외 없음).
// permanent 대신 statusCode 로 301 을 명시한다. 경로 · 쿼리는 /:path* 로 보존된다.
export function wwwRedirects(): Awaited<ReturnType<Redirects>> {
  const destination = `${CANONICAL_ORIGIN}/:path*`;
  return [
    {
      source: "/:path*",
      has: [{ type: "host", value: WWW_HOST }],
      destination,
      statusCode: 301,
    },
    // Amplify(CloudFront) 뒤에서는 원래 호스트가 x-forwarded-host 로 온다.
    {
      source: "/:path*",
      has: [{ type: "header", key: "x-forwarded-host", value: WWW_HOST }],
      destination,
      statusCode: 301,
    },
  ];
}
