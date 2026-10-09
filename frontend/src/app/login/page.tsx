import { safeReturnTo } from "@/lib/auth/returnTo";
import { LoginScreen } from "./LoginScreen";

// HOME-01 로그인 (PG-2). returnTo 는 문자열일 때만 쓰고 safeReturnTo 를 거친다 (A-02, 오픈 리다이렉트 방지).
// searchParams 는 Next 16 에서 Promise 다 (node_modules/next/dist/docs/01-app/03-api-reference/03-file-conventions/page.md).
export default async function Page(props: PageProps<"/login">) {
  const query = await props.searchParams;
  const raw = query.returnTo;
  return (
    <LoginScreen
      returnTo={safeReturnTo(typeof raw === "string" ? raw : null)}
    />
  );
}
