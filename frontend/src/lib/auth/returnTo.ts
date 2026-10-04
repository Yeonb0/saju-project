// 로그인 후 복귀 경로 검사 (PG-2 · HOME-01 · A-02 · A-03).
// 근거: API_SPEC 3장 "returnTo 내부 상대 경로만", PG-2 체크박스. 오픈 리다이렉트 방지.
// 통과하지 못하면 던지지 않고 "/" 로 돌린다 — 화면이 오류로 멈추지 않게.
// returnTo 는 쿼리라 maskUrl 이 관측 도구에서 지운다 (경로 안의 개인 정보가 새지 않게).

const FALLBACK = "/";
const MAX_LENGTH = 2048;
const BASE = "https://returnto.invalid";
// 로그인 · 온보딩으로 되돌아가는 고리 방지
const LOOP_PREFIXES = ["/login", "/onboarding"];

function hasControl(value: string): boolean {
  for (let i = 0; i < value.length; i += 1) {
    const code = value.charCodeAt(i);
    if (code <= 0x1f || code === 0x7f) return true;
  }
  return false;
}

export function safeReturnTo(raw: string | null | undefined): string {
  if (raw === null || raw === undefined) return FALLBACK;
  if (raw === "" || raw.length > MAX_LENGTH) return FALLBACK;
  if (raw[0] !== "/" || raw[1] === "/") return FALLBACK;
  if (raw.includes("\\")) return FALLBACK;
  if (hasControl(raw)) return FALLBACK;

  let url: URL;
  try {
    url = new URL(raw, BASE);
  } catch {
    return FALLBACK;
  }
  if (url.origin !== BASE) return FALLBACK;

  const { pathname } = url;
  for (const prefix of LOOP_PREFIXES) {
    if (pathname === prefix || pathname.startsWith(`${prefix}/`)) {
      return FALLBACK;
    }
  }
  if (pathname.startsWith("/api/")) return FALLBACK;

  return raw;
}

export function loginHref(returnTo: string): string {
  return `/login?returnTo=${encodeURIComponent(safeReturnTo(returnTo))}`;
}

export function onboardingHref(returnTo: string): string {
  return `/onboarding?returnTo=${encodeURIComponent(safeReturnTo(returnTo))}`;
}
