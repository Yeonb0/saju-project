import { notFound } from "next/navigation";
import { RoutePlaceholder } from "@/components/RoutePlaceholder";
import { RequireSession } from "@/lib/auth/RequireSession";
import { isFortuneSlug } from "@/lib/navigation";
import { FortuneQuestionsScreen } from "./FortuneQuestionsScreen";

// FORT-02 · 03 유료 운세 질문 + 옵션 선택 (라우트 출처: FUNCTIONAL_SPEC 2장). A-02 · A-03 — 로그인 가드.
// params · searchParams 는 Next 16 에서 Promise 다 (node_modules/next/dist/docs/01-app/03-api-reference/03-file-conventions/page.md).
// personId 쿼리는 FORT-01 이 넘긴 인물 ID 하나다 — 문자열 하나가 아니면 not-found.
export default async function Page(
  props: PageProps<"/fortune/[type]/questions">,
) {
  const { type } = await props.params;
  const { personId } = await props.searchParams;
  if (!isFortuneSlug(type)) notFound();
  if (type === "compatibility") {
    // TODO(MATCH-03): 궁합 질문은 사람 선택(MATCH-01 · 02) 다음 단계
    return <RoutePlaceholder path="/fortune/[type]/questions" />;
  }
  if (typeof personId !== "string" || personId === "") notFound();
  return (
    <RequireSession>
      <FortuneQuestionsScreen slug={type} personId={personId} />
    </RequireSession>
  );
}
