import type { Breadcrumb, ErrorEvent } from "@sentry/nextjs";
import type { CaptureResult, PostHogConfig } from "posthog-js";
import { maskUrl } from "./maskUrl";

// 라이브러리 초기화 호출은 두지 않는다 — 옵션 객체만 만들어 테스트할 수 있게 한다.

export function getEnvironment(): string {
  return process.env.NEXT_PUBLIC_VERCEL_ENV ?? "development";
}

export function sentryOptions(dsn: string, environment: string) {
  return {
    dsn,
    environment,
    sendDefaultPii: false,
    tracesSampleRate: 0,
    // Replay 통합은 넣지 않는다.
    beforeSend(event: ErrorEvent): ErrorEvent {
      const request = event.request;
      if (request) {
        if (request.url) {
          request.url = maskUrl(request.url);
        }
        // 브라우저 SDK 는 이전 페이지 주소를 Referer 헤더로 보낸다 — 선물 링크에서 넘어오면 토큰이 샌다.
        if (request.headers) {
          for (const key of ["Referer", "referer"]) {
            const value = request.headers[key];
            if (typeof value === "string") {
              request.headers[key] = maskUrl(value);
            }
          }
        }
        delete request.query_string;
      }
      return event;
    },
    beforeBreadcrumb(breadcrumb: Breadcrumb): Breadcrumb {
      const data = breadcrumb.data;
      if (data) {
        for (const key of ["url", "to", "from"]) {
          if (typeof data[key] === "string") {
            data[key] = maskUrl(data[key]);
          }
        }
      }
      return breadcrumb;
    },
  };
}

// 속성 이름 목록은 버전마다 달라 새기 쉽다 — 이름 끝 규칙으로 가린다.
// 첫 방문 URL($initial_current_url · $initial_pathname 등)은 $set_once 에 따로 실린다.
// URL 이 아닌 값("$direct" 등)은 maskUrl 이 그대로 둔다.
const URL_KEY = /(url|pathname|referrer)$/i;

function maskUrlProperties(bag: Record<string, unknown> | undefined) {
  if (!bag) return;
  for (const [key, value] of Object.entries(bag)) {
    if (URL_KEY.test(key) && typeof value === "string") {
      bag[key] = maskUrl(value);
    }
  }
}

export function posthogOptions(apiHost: string): Partial<PostHogConfig> {
  return {
    api_host: apiHost,
    capture_pageview: "history_change", // 첫 진입 + SPA 라우트(pathname) 변경까지
    autocapture: false, // 이벤트 설계는 P7 — 그 전에는 자동 수집을 켜지 않는다.
    disable_session_recording: true, // 생년월일 · 이름 입력 화면이 있어 화면 녹화는 끈다.
    person_profiles: "identified_only",
    before_send(result: CaptureResult | null): CaptureResult | null {
      if (!result) return result;
      maskUrlProperties(result.properties);
      maskUrlProperties(result.$set);
      maskUrlProperties(result.$set_once);
      return result;
    },
  };
}
