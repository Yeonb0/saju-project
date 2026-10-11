import { notFound } from "next/navigation";
import { RequireSession } from "@/lib/auth/RequireSession";
import { isFortuneSlug } from "@/lib/navigation";
import { FortuneQuestionsScreen } from "./FortuneQuestionsScreen";

// FORT-02 · 03 · MATCH-03 유료 운세 질문 + 옵션 선택 (라우트 출처: FUNCTIONAL_SPEC 2장). A-02 · A-03 — 로그인 가드.
// params · searchParams 는 Next 16 에서 Promise 다 (node_modules/next/dist/docs/01-app/03-api-reference/03-file-conventions/page.md).
// personId 쿼리는 FORT-01 이 넘긴 인물 ID 하나다 — 문자열 하나가 아니면 not-found.
export default async function Page(
  props: PageProps<"/fortune/[type]/questions">,
) {
  const { type } = await props.params;
  const { personId, counterpartId } = await props.searchParams;
  if (!isFortuneSlug(type)) notFound();
  if (typeof personId !== "string" || personId === "") notFound();
  if (type === "compatibility") {
    // MATCH-03 궁합은 본인 + 상대 (Q-26) — 상대 ID 쿼리도 문자열 하나여야 한다
    if (typeof counterpartId !== "string" || counterpartId === "") notFound();
    return (
      <RequireSession>
        <FortuneQuestionsScreen
          slug="compatibility"
          personId={personId}
          counterpartId={counterpartId}
        />
      </RequireSession>
    );
  }
  // 다른 운세는 counterpartId 쿼리를 읽지 않는다
  return (
    <RequireSession>
      <FortuneQuestionsScreen
        slug={type}
        personId={personId}
        counterpartId={null}
      />
    </RequireSession>
  );
}
