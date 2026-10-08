import { notFound } from "next/navigation";
import { RoutePlaceholder } from "@/components/RoutePlaceholder";
import { RequireSession } from "@/lib/auth/RequireSession";
import { isFortuneSlug } from "@/lib/navigation";
import { FortuneInfoScreen } from "./FortuneInfoScreen";

// FORT-01 유료 운세 정보 확인 (라우트 출처: FUNCTIONAL_SPEC 2장 /fortune/[type] — type = overall · love · wealth · compatibility · sinsal).
// A-02 (로그인 필요) · A-03 (본인 정보 없으면 온보딩) — 가드로 감싼다.
// params 는 Next 16 에서 Promise 다 (node_modules/next/dist/docs/01-app/03-api-reference/03-file-conventions/page.md).
export default async function Page(props: PageProps<"/fortune/[type]">) {
  const { type } = await props.params;
  if (!isFortuneSlug(type)) notFound();
  if (type === "compatibility") {
    // TODO(MATCH-01 · 02): 궁합 사람 선택은 다음 단계
    return <RoutePlaceholder path="/fortune/[type]" />;
  }
  return (
    <RequireSession>
      <FortuneInfoScreen slug={type} />
    </RequireSession>
  );
}
