// 수능 준비물 체크 상태 (Phase 4, API_SPEC 8장 "체크 여부는 클라이언트 로컬 상태"). 결과 스냅샷은 바꾸지 않는다.
// localStorage 는 이 용도에만 쓴다 (frontend/CLAUDE.md 브라우저 저장소). 결과 ID 와 체크한 항목 ID 만 저장한다 —
// 항목 이름 · 결과 본문 · 인적정보는 넣지 않는다. 선물 토큰도 키로 쓰지 않는다 (결과 ID 로만).
// 저장소를 쓸 수 없거나 값이 깨졌으면 빈 상태로 시작한다 — 체크는 편의 기능이다.
import { z } from "zod";

const PREFIX = "suneungChecklist:";
const storedSchema = z.array(z.string()).max(200);

export function checklistStorageKey(
  readingId: string,
  sectionKey: string,
): string {
  return `${PREFIX}${readingId}:${sectionKey}`;
}

function storage(): Storage | null {
  try {
    return typeof window === "undefined" ? null : window.localStorage;
  } catch {
    return null;
  }
}

export function loadChecked(readingId: string, sectionKey: string): string[] {
  const store = storage();
  if (!store) return [];
  try {
    const raw = store.getItem(checklistStorageKey(readingId, sectionKey));
    if (raw === null) return [];
    const parsed = storedSchema.safeParse(JSON.parse(raw));
    return parsed.success ? parsed.data : [];
  } catch {
    return [];
  }
}

export function saveChecked(
  readingId: string,
  sectionKey: string,
  ids: readonly string[],
): void {
  try {
    storage()?.setItem(
      checklistStorageKey(readingId, sectionKey),
      JSON.stringify([...new Set(ids)]),
    );
  } catch {
    // 저장소가 가득 찼거나 막혔다 — 이번 화면 안에서만 체크가 유지된다
  }
}
