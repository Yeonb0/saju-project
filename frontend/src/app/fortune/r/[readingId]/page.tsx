import { ReadingScreen } from "@/components/ReadingScreen";
import { RequireSession } from "@/lib/auth/RequireSession";

// FORT-05 ~ 07 유료 운세 결과 재열람 (VIEWER · F-05). 소유자만 — 로그인 가드 후 서버가 403/404 로 막는다.
// 저장 · 공유(G-11) · 부적 확인 · 부적 만들기(FORT-07, Q-19)는 아직 없다.
// params 는 Next 16 에서 Promise 다 (node_modules/next/dist/docs/01-app/03-api-reference/03-file-conventions/page.md).
export default async function Page(props: PageProps<"/fortune/r/[readingId]">) {
  const { readingId } = await props.params;
  return (
    <RequireSession>
      {/* TODO(PD 문구): 제목 */}
      <ReadingScreen readingId={readingId} title="운세 결과" />
    </RequireSession>
  );
}
