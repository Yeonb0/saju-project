import * as Sentry from "@sentry/nextjs";
import posthog from "posthog-js";
import {
  getEnvironment,
  posthogOptions,
  sentryOptions,
} from "@/lib/observability/config";

// 값이 없으면 조용히 넘어간다: 로컬 · CI 에는 키가 없다. Vercel 빌드는 next.config 검사로 키 누락 시 실패한다.
const sentryDsn = process.env.NEXT_PUBLIC_SENTRY_DSN;
if (sentryDsn) {
  Sentry.init(sentryOptions(sentryDsn, getEnvironment()));
}

const posthogKey = process.env.NEXT_PUBLIC_POSTHOG_KEY;
const posthogHost = process.env.NEXT_PUBLIC_POSTHOG_HOST;
if (posthogKey && posthogHost) {
  posthog.init(posthogKey, posthogOptions(posthogHost));
}

export const onRouterTransitionStart = Sentry.captureRouterTransitionStart;
