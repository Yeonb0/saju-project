import { RequireSession } from "@/lib/auth/RequireSession";
import { MyPageScreen } from "./MyPageScreen";

// MY-01 마이페이지. 화면 내용은 MyPageScreen (클라이언트)
// A-02 (로그인 필요) · A-03 (본인 정보 없으면 온보딩) — 가드로 감싼다
export default function Page() {
  return (
    <RequireSession>
      <MyPageScreen />
    </RequireSession>
  );
}
