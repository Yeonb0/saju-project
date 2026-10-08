import { AboutScreen } from "./AboutScreen";

// PG-4 서비스 · 상품 소개. 화면 내용은 AboutScreen (클라이언트)
// 로그인 불필요 (FUNCTIONAL_SPEC 2장 /about) — RequireSession 으로 감싸지 않는다
export default function Page() {
  return <AboutScreen />;
}
