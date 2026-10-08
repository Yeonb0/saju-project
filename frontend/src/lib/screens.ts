// 화면 ID · 라우트 표. 화면 ID 는 Figma 최종 와이어(페이지 `와이어프레임 최종`, node 195:91)의 프레임 이름 (FIGMA-FINAL).
// 출처: docs/PHASES.md 1장 표 (화면명 · node 를 그대로 옮김), 라우트는 docs/FUNCTIONAL_SPEC.md 2장.
// 옛 와이어(node 17:2)의 구 번호 #1 ~ #40 은 쓰지 않는다 — 구 #24(D-day 가격) · #26(부적 고르기)은 최종 와이어에 없다.
// 팝업(차감 확인 · 잔액 부족 · 충전 완료 · 부적 저장)은 프레임 이름이 없어 이 표에 없다 — 앞 화면 안 Modal (CHECKOUT-POPUP).

export type Screen = {
  name: string;
  nodeId: string;
};

export const SCREENS = {
  "HOME-01": { name: "로그인", nodeId: "195:774" },
  "HOME-02": { name: "정보 입력 (정보 저장하기)", nodeId: "195:256" },
  "HOME-03": { name: "홈", nodeId: "195:92" },
  "HOME-04": { name: "사이드 메뉴 (오버레이)", nodeId: "195:647" },
  "TODAY-01": { name: "오늘의 운세 — 로딩", nodeId: "195:113" },
  "TODAY-02": { name: "오늘의 운세 — 결과", nodeId: "195:758" },
  "MY-01": { name: "마이페이지", nodeId: "195:145" },
  "MY-02": { name: "사람 정보 저장", nodeId: "195:274" },
  "TALBOX-01": { name: "내 부적 창고 — 목록", nodeId: "195:666" },
  "TALBOX-02": { name: "내 부적 창고 — 상세 (바텀시트)", nodeId: "195:701" },
  "FORT-01": { name: "유료 운세 — 정보 확인", nodeId: "195:180" },
  "FORT-02": { name: "종합 · 재물 · 신살 — 질문", nodeId: "195:611" },
  "FORT-03": { name: "애정운 — 질문", nodeId: "248:956" },
  "MATCH-01": { name: "궁합 — 사람 선택", nodeId: "248:798" },
  "MATCH-02": { name: "궁합 — 사람 선택 완료", nodeId: "248:827" },
  "MATCH-03": { name: "궁합 — 질문", nodeId: "248:849" },
  "FORT-04": { name: "등껍질 충전 — 잔액 부족", nodeId: "248:244" },
  "FORT-05": { name: "유료 운세 — 분석 로딩", nodeId: "195:121" },
  "FORT-06": { name: "유료 운세 — 결과 (부적 포함)", nodeId: "195:622" },
  "FORT-07": { name: "유료 운세 — 결과 (부적 미포함)", nodeId: "248:278" },
  "FORT-08": { name: "부적 생성 로딩", nodeId: "195:137" },
  "FORT-09": { name: "내 부적", nodeId: "195:744" },
  "PAY-01": { name: "등껍질 충전", nodeId: "237:69" },
  "PAY-02": { name: "등껍질 충전 — 잔액 부족", nodeId: "240:98" },
  "CSAT-01": { name: "수능운 — 정보 확인", nodeId: "195:202" },
  "CSAT-02": { name: "수능운 — 분석 로딩", nodeId: "195:129" },
  "CSAT-03": {
    name: "수능운 — 결과 목차 (교시별 사주 · 준비물 · 도시락 추천)",
    nodeId: "195:368",
  },
  "CSAT-04": { name: "수능운 — 교시별 사주", nodeId: "195:397" },
  "CSAT-05": { name: "수능운 — 수능 도시락", nodeId: "195:432" },
  "CSAT-06": { name: "수능운 — 준비물 체크리스트", nodeId: "195:463" },
  "CSAT-07": { name: "수능운 — 수능 부적", nodeId: "195:495" },
  "GIFT-01": { name: "선물하기 — 옵션 선택", nodeId: "195:224" },
  "GIFT-02": { name: "선물하기 — 수신인 입력", nodeId: "195:238" },
  "GIFT-03": { name: "선물하기 — 메시지 카드", nodeId: "195:509" },
  "GIFT-04": { name: "선물하기 — 완료", nodeId: "195:557" },
  "GIFT-05": { name: "선물하기 — 발송 실패", nodeId: "248:1081" },
  "GIFT-06": { name: "여러 명 — 수신인 입력", nodeId: "248:326" },
  "GIFT-07": { name: "여러 명 — 메시지 공통", nodeId: "248:355" },
  "GIFT-08": { name: "여러 명 — 메시지 개별", nodeId: "248:374" },
  "RECV-01": { name: "선물 받기 (사주 + 부적) — 도착", nodeId: "195:340" },
  "RECV-02": { name: "선물 받기 — 정보 입력", nodeId: "248:771" },
  "RECV-03": { name: "선물 받기 — 메시지 확인", nodeId: "195:358" },
  "RECV-04": { name: "선물 받기 — 교시별 사주", nodeId: "195:377" },
  "RECV-05": { name: "선물 받기 — 수능 도시락", nodeId: "195:420" },
  "RECV-06": { name: "선물 받기 — 준비물 체크리스트", nodeId: "195:445" },
  "RECV-07": { name: "선물 받기 — 수능 부적", nodeId: "195:484" },
  "RECV-T-01": { name: "선물 받기 (`부적`) — 도착", nodeId: "248:415" },
  "RECV-T-02": { name: "선물 받기 (`부적`) — 정보 입력", nodeId: "248:422" },
  "RECV-T-03": { name: "선물 받기 (`부적`) — 메시지 확인", nodeId: "248:435" },
  "RECV-T-04": { name: "선물 받기 (`부적`) — 수능 부적", nodeId: "248:494" },
} as const satisfies Record<string, Screen>;

export type ScreenId = keyof typeof SCREENS;

export type Route = {
  screens: readonly ScreenId[];
  note?: string;
};

export const ROUTES = {
  "/login": { screens: ["HOME-01"] },
  "/onboarding": { screens: ["HOME-02"] },
  "/": { screens: ["HOME-03"] },
  "/today": { screens: ["TODAY-01", "TODAY-02"] },
  // CHECKOUT-POPUP — 수능운 차감 확인은 정보 확인 화면 안 팝업 (FUNCTIONAL_SPEC 2장 v0.3, 최종 와이어 CSAT-01 다음)
  "/suneung": { screens: ["CSAT-01"] },
  "/suneung/r/[readingId]": {
    screens: ["CSAT-02", "CSAT-03", "CSAT-04", "CSAT-05", "CSAT-06", "CSAT-07"],
  },
  // 궁합은 같은 경로에서 사람 선택 (MATCH-01 · 02)
  "/fortune/[type]": { screens: ["FORT-01", "MATCH-01", "MATCH-02"] },
  // CHECKOUT-POPUP — 옵션 선택 · 차감 확인은 질문 화면의 옵션 버튼 + 팝업 (FUNCTIONAL_SPEC 2장 v0.3)
  "/fortune/[type]/questions": { screens: ["FORT-02", "FORT-03", "MATCH-03"] },
  "/fortune/r/[readingId]": { screens: ["FORT-05", "FORT-06", "FORT-07"] },
  "/talisman/[id]": { screens: ["FORT-08", "FORT-09"] },
  // FUNCTIONAL_SPEC 2장 v0.3 — /gift/checkout 삭제, 선물 차감 확인은 위저드 안 팝업 (GIFT-03 다음)
  "/gift/new": { screens: ["GIFT-01", "GIFT-02", "GIFT-03"] },
  "/gift/done/[orderId]": { screens: ["GIFT-04", "GIFT-05"] },
  "/g/[token]": {
    screens: [
      "RECV-01",
      "RECV-02",
      "RECV-03",
      "RECV-04",
      "RECV-05",
      "RECV-06",
      "RECV-07",
      "RECV-T-01",
      "RECV-T-02",
      "RECV-T-03",
      "RECV-T-04",
    ],
  },
  // FUNCTIONAL_SPEC 2장 (Q-10) — 공유 랜딩, 와이어 없음. 공유 범위 · 만료는 G-11 미정
  "/share/[shareId]": { screens: [], note: "공유 랜딩" },
  "/me": { screens: ["MY-01"] },
  "/me/people/new": { screens: ["MY-02"] },
  "/me/people/[id]": { screens: ["MY-02"] },
  "/vault": { screens: ["TALBOX-01", "TALBOX-02"] },
  // FUNCTIONAL_SPEC 2장 (Q-01) — 충전. FORT-04 는 PAY-02 와 같은 화면
  "/wallet": { screens: ["PAY-01", "PAY-02", "FORT-04"], note: "충전" },
  "/pay/success": { screens: [], note: "결제 공통" },
  "/pay/fail": { screens: [], note: "결제 공통" },
  "/about": { screens: [], note: "심사용·법적 고지" },
  "/terms": { screens: [], note: "심사용·법적 고지" },
  "/privacy": { screens: [], note: "심사용·법적 고지" },
  "/refund": { screens: [], note: "심사용·법적 고지" },
} as const satisfies Record<string, Route>;

export type RoutePath = keyof typeof ROUTES;

// 라우트가 없는 화면: 사이드 메뉴 → AppShell
export const ROUTELESS_SCREENS = {
  "HOME-04": "AppShell",
} as const satisfies Partial<Record<ScreenId, string>>;

// 와이어에는 있지만 만들지 않는 화면 — 선물 단건 (P-03A, Q-23)
export const NOT_BUILT_SCREENS = {
  "GIFT-06": "Q-23",
  "GIFT-07": "Q-23",
  "GIFT-08": "Q-23",
} as const satisfies Partial<Record<ScreenId, string>>;
