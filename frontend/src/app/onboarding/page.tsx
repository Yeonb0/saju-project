import { RequireSession } from "@/lib/auth/RequireSession";
import { safeReturnTo } from "@/lib/auth/returnTo";
import { OnboardingScreen } from "./OnboardingScreen";

// HOME-02 온보딩 (A-03). returnTo 는 문자열일 때만 쓰고 safeReturnTo 를 거친다 (오픈 리다이렉트 방지).
// searchParams 는 Next 16 에서 Promise 다 (node_modules/next/dist/docs/01-app/03-api-reference/03-file-conventions/page.md).
export default async function Page(props: PageProps<"/onboarding">) {
  const query = await props.searchParams;
  const raw = query.returnTo;
  return (
    // requirePerson={false}: 온보딩 화면 자신이 본인 정보 없음을 받는 곳이라 — 켜면 /onboarding 으로 다시 보내는 고리가 생긴다
    <RequireSession requirePerson={false}>
      <OnboardingScreen
        returnTo={safeReturnTo(typeof raw === "string" ? raw : null)}
      />
    </RequireSession>
  );
}
