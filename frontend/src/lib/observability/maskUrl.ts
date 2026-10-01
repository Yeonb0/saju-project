import { ROUTES } from "@/lib/screens";

// 선물 토큰(/g/[token])은 유료 콘텐츠 접근 열쇠라 관측 도구(Sentry · PostHog)에 남기지 않는다.
// 쿼리(?…)와 해시(#…)는 통째로 지운다 — 결제 복귀 URL 의 paymentKey · orderId · amount 등이 들어 있다.

type DynamicPattern = { pattern: string; regex: RegExp; params: number };

const PARAM_SEGMENT = /^\[.+\]$/;
const escapeRegex = (s: string) => s.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");

const staticPaths = new Set(
  Object.keys(ROUTES).filter((path) => !path.includes("[")),
);

// 정적 경로를 먼저 보고, 그다음 동적 패턴을 동적 자리 수가 적은 순으로 맞춘다.
// Next 의 매칭 순서를 완전히 재현하지는 않는다 — 드물게 다른 동적 패턴 이름으로 표시될 수 있지만
// (예: readingId 가 "questions" 인 경우) 어느 패턴에 걸려도 동적 값은 가려진다.
const dynamicPatterns: DynamicPattern[] = Object.keys(ROUTES)
  .filter((path) => path.includes("["))
  .map((pattern) => {
    const segments = pattern.split("/");
    const params = segments.filter((s) => PARAM_SEGMENT.test(s)).length;
    const source = segments
      .map((s) => (PARAM_SEGMENT.test(s) ? "[^/]+" : escapeRegex(s)))
      .join("/");
    return { pattern, regex: new RegExp(`^${source}$`), params };
  })
  .sort((a, b) => a.params - b.params);

function maskPath(path: string): string {
  if (staticPaths.has(path)) return path; // 정적 경로가 동적 패턴보다 먼저
  const hit = dynamicPatterns.find(({ regex }) => regex.test(path));
  return hit ? hit.pattern : path;
}

const ABSOLUTE_URL = /^[a-z][a-z0-9+.-]*:\/\//i;

export function maskUrl(url: string): string {
  if (ABSOLUTE_URL.test(url)) {
    try {
      const parsed = new URL(url);
      return `${parsed.origin}${maskPath(parsed.pathname)}`;
    } catch {
      return url;
    }
  }
  if (!url.startsWith("/")) return url; // "$direct" 같은 URL 이 아닌 값은 그대로
  const path = url.split(/[?#]/, 1)[0];
  return maskPath(path);
}
