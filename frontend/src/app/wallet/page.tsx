import { RequireSession } from "@/lib/auth/RequireSession";
import { WalletScreen } from "./WalletScreen";

// PAY-01 등껍질 충전 (PG-3). 화면 내용은 WalletScreen (클라이언트)
// PG-2 · PG-3 · A-02 (로그인 필요) · A-03 (본인 정보 없으면 온보딩) — 가드로 감싼다
export default function Page() {
  return (
    <RequireSession>
      <WalletScreen />
    </RequireSession>
  );
}
