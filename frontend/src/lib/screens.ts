// docs/PHASES.md 1장 · Phase 1 라우트 초안 기준. 화면명은 문서 표를 그대로 옮긴 것

export type Frame = {
  frame: string;
  nodeId: string;
};

export type Screen = {
  name: string;
  frames: readonly Frame[];
  note?: string;
};

const f = (frame: string, nodeId: string): Frame => ({ frame, nodeId });

export const SCREENS = {
  1: { name: "로그인", frames: [f("iPhone 17 - 7", "37:103")] },
  2: { name: "내 정보 저장", frames: [f("iPhone 17 - 36", "89:966")] },
  3: {
    name: "다른 사람 정보 저장 (나와의 관계)",
    frames: [f("iPhone 17 - 37", "89:1009")],
  },
  4: { name: "홈", frames: [f("iPhone 17 - 1", "37:73")] },
  5: {
    name: "사이드 메뉴 (오버레이)",
    frames: [f("iPhone 17 - 12", "52:166")],
    note: "내 오버레이",
  },
  6: {
    name: "오늘의 운세 — 등껍질 (분석 전/중)",
    frames: [f("iPhone 17 - 8", "37:127")],
  },
  7: { name: "오늘의 운세 — 결과", frames: [f("iPhone 17 - 9", "50:11")] },
  8: { name: "유료 운세 — 정보 확인", frames: [f("iPhone 17 - 13", "52:187")] },
  9: { name: "유료 운세 — 상세 질문", frames: [f("iPhone 17 - 34", "89:827")] },
  10: {
    name: "유료 운세 — 주문/결제 (사주 only / 사주+부적)",
    frames: [f("iPhone 17 - 15", "53:231"), f("17 - 35", "89:856")],
  },
  11: { name: "유료 운세 — 분석 중", frames: [f("iPhone 17 - 33", "74:723")] },
  12: { name: "유료 운세 — 결과", frames: [f("iPhone 17 - 16", "53:251")] },
  13: { name: "부적 생성 중", frames: [f("iPhone 17 - 38", "89:1039")] },
  14: { name: "내 부적 (부적 결과)", frames: [f("iPhone 17 - 39", "89:1048")] },
  15: { name: "수능운 — 정보 확인", frames: [f("iPhone 17 - 40", "90:1220")] },
  16: { name: "수능운 — 주문/결제", frames: [f("iPhone 17 - 47", "90:1329")] },
  17: { name: "수능운 — 분석 중", frames: [f("iPhone 17 - 46", "90:1321")] },
  18: { name: "수능운 — 결과 요약", frames: [f("iPhone 17 - 41", "90:1243")] },
  19: {
    name: "수능운 — 교시별 사주",
    frames: [f("iPhone 17 - 42", "90:1252")],
  },
  20: {
    name: "수능운 — 도시락 추천",
    frames: [f("iPhone 17 - 43", "90:1272")],
  },
  21: {
    name: "수능운 — 준비물 체크리스트",
    frames: [f("iPhone 17 - 44", "90:1282")],
  },
  22: {
    name: "수능운 — 수능 부적 (저장/공유)",
    frames: [f("iPhone 17 - 45", "90:1300")],
  },
  23: {
    name: "선물하기 — 응원할 날 · 상품 (1장 / n일)",
    frames: [f("iPhone 17 - 17", "53:296")],
  },
  24: {
    name: "선물하기 — D-day별 가격 옵션 · 첫 부적 도착일",
    frames: [f("iPhone 17 - 20", "54:371")],
  },
  25: {
    name: "선물하기 — 수신인 정보",
    frames: [f("iPhone 17 - 18", "53:315")],
  },
  26: {
    name: "선물하기 — 부적 고르기",
    frames: [f("iPhone 17 - 19", "54:337")],
  },
  27: {
    name: "선물하기 — 메시지 카드",
    frames: [f("iPhone 17 - 21", "54:394")],
  },
  28: { name: "선물하기 — 주문/결제", frames: [f("iPhone 17 - 22", "54:415")] },
  29: { name: "선물하기 — 완료", frames: [f("iPhone 17 - 23", "56:439")] },
  30: {
    name: "선물 받기 — 선물 열기",
    frames: [f("iPhone 17 - 24", "57:458")],
  },
  31: {
    name: "선물 받기 — 생년월일 입력",
    frames: [f("iPhone 17 - 25", "57:479")],
  },
  32: {
    name: "선물 받기 — 결과 요약 + 보낸 사람 메시지",
    frames: [f("iPhone 17 - 26", "57:493")],
  },
  33: {
    name: "선물 받기 — 교시별 사주",
    frames: [f("iPhone 17 - 27", "57:505")],
  },
  34: {
    name: "선물 받기 — 도시락 추천",
    frames: [f("iPhone 17 - 28", "61:527")],
  },
  35: { name: "선물 받기 — 준비물", frames: [f("iPhone 17 - 29", "61:552")] },
  36: {
    name: "선물 받기 — 수능 부적 (저장/공유)",
    frames: [f("iPhone 17 - 30", "73:563")],
  },
  37: {
    name: "마이페이지 (저장된 사람들)",
    frames: [f("iPhone 17 - 31", "74:593")],
  },
  38: { name: "내 부적 창고 (그리드)", frames: [f("iPhone 17 - 10", "50:22")] },
  39: { name: "부적 상세 (바텀시트)", frames: [f("iPhone 17 - 11", "52:64")] },
  40: {
    name: '"부적이 저장되었어요!" 모달',
    frames: [f("프레임 밖 요소", "52:119 부근")],
  },
} as const satisfies Record<number, Screen>;

export type ScreenNo = keyof typeof SCREENS;

export type Route = {
  screens: readonly ScreenNo[];
  note?: string;
};

export const ROUTES = {
  "/login": { screens: [1] },
  "/onboarding": { screens: [2] },
  "/": { screens: [4] },
  "/today": { screens: [6, 7] },
  "/suneung": { screens: [15] },
  "/suneung/checkout": { screens: [16] },
  "/suneung/r/[readingId]": { screens: [17, 18, 19, 20, 21, 22] },
  "/fortune/[type]": { screens: [8] },
  "/fortune/[type]/questions": { screens: [9] },
  "/fortune/[type]/checkout": { screens: [10] },
  "/fortune/r/[readingId]": { screens: [11, 12] },
  "/talisman/[id]": { screens: [13, 14] },
  "/gift/new": { screens: [23, 24, 25, 26, 27] },
  "/gift/checkout": { screens: [28] },
  "/gift/done/[orderId]": { screens: [29] },
  "/g/[token]": { screens: [30, 31, 32, 33, 34, 35, 36] },
  // FUNCTIONAL_SPEC 2장 (Q-10) — 공유 랜딩, 와이어 없음. 공유 범위 · 만료는 G-11 미정
  "/share/[shareId]": { screens: [], note: "공유 랜딩" },
  "/me": { screens: [37] },
  "/me/people/new": { screens: [3] },
  "/me/people/[id]": { screens: [3] },
  "/vault": { screens: [38, 39] },
  // FUNCTIONAL_SPEC 2장 (Q-01) — 충전. 최종 와이어 PAY-01 · PAY-02 · FORT-04 (구 번호 없음, FIGMA-FINAL)
  "/wallet": { screens: [], note: "결제 공통 · 충전" },
  "/pay/success": { screens: [], note: "결제 공통" },
  "/pay/fail": { screens: [], note: "결제 공통" },
  "/about": { screens: [], note: "심사용·법적 고지" },
  "/terms": { screens: [], note: "심사용·법적 고지" },
  "/privacy": { screens: [], note: "심사용·법적 고지" },
  "/refund": { screens: [], note: "심사용·법적 고지" },
} as const satisfies Record<string, Route>;

export type RoutePath = keyof typeof ROUTES;

// 라우트가 없는 화면: 5 = 사이드 메뉴 → AppShell, 40 = 저장 모달 → 공통 Modal
export const ROUTELESS_SCREENS = {
  5: "AppShell",
  40: "공통 Modal",
} as const;
