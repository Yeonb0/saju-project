import { HomeScreen } from "./HomeScreen";

// HOME-03 홈. 화면 내용은 HomeScreen (클라이언트)
// 로그인 가드 없음 — 홈은 공개이고, 들어간 화면이 가드한다 (A-02)
export default function Page() {
  return <HomeScreen />;
}
