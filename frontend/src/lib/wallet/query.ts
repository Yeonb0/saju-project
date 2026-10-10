// 잔액(지갑) 조회의 TanStack Query 키 — 홈(HOME-03) · 충전(PAY-01) 이 같이 쓴다.
// 구매 성공 · 충전 CREDITED 뒤에는 이 키를 무효화해 서버에서 다시 받는다 (P-09 · Q-22: 잔액은 서버 값만, 클라이언트에서 차감 · 합산하지 않는다).
export const WALLET_QUERY_KEY = ["wallet"] as const;
