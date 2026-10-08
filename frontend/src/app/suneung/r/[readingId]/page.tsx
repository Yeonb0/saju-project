import { ReadingScreen } from "@/components/ReadingScreen";
import { RequireSession } from "@/lib/auth/RequireSession";

// CSAT-02 ~ 06 수능운 결과 재열람 (VIEWER · F-05). 소유자만 — 로그인 가드 후 서버가 403/404 로 막는다.
// params 는 Next 16 에서 Promise 다 (node_modules/next/dist/docs/01-app/03-api-reference/03-file-conventions/page.md).
export default async function Page(props: PageProps<"/suneung/r/[readingId]">) {
  const { readingId } = await props.params;
  return (
    <RequireSession>
      {/* TODO(PD 문구): 제목 */}
      <ReadingScreen readingId={readingId} title="수능운" />
    </RequireSession>
  );
}
