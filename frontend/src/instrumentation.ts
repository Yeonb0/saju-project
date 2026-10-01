import * as Sentry from "@sentry/nextjs";
import { getEnvironment, sentryOptions } from "@/lib/observability/config";

export async function register() {
  if (
    process.env.NEXT_RUNTIME === "nodejs" ||
    process.env.NEXT_RUNTIME === "edge"
  ) {
    const dsn = process.env.NEXT_PUBLIC_SENTRY_DSN;
    if (dsn) {
      Sentry.init(sentryOptions(dsn, getEnvironment()));
    }
  }
}

export const onRequestError = Sentry.captureRequestError;
