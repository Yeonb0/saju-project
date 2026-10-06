// 로그아웃 (PG-2). 화면은 SessionPort.logout 을 직접 부르지 않고 이 함수를 쓴다.
// 구매 선택 복원 값은 로그아웃 때 지운다 (PURCHASE-RESTORE) — 서버 요청이 실패해도 기기에는 남기지 않게 먼저 지운다.
import type { SessionPort } from "@/lib/ports/session";
import { clearPurchaseSelection } from "@/lib/purchase/restore";

export async function logout(port: Pick<SessionPort, "logout">): Promise<void> {
  clearPurchaseSelection();
  await port.logout();
}
