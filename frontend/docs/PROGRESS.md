# PROGRESS

> **FE 개인 작업 문서다. 팀 합의 문서가 아니다** (DOCS-FE-OWN).
> 모든 세션은 시작할 때 같은 폴더의 `PHASES.md` · `TEAM-QUESTIONS.md` 와 이 파일을 읽는다. 갱신은 웹 대화에서 작성한 완성본으로만 한다 (`frontend/CLAUDE.md` "문서 변경 규칙").
> 갱신할 때는 "현재 상태"를 덮어쓰고, "세션 로그"에는 맨 위에 한 줄씩 추가한다.

## 현재 상태

- **현재 단계**: **PG 심사 트랙 (PG-FIRST)** — 백엔드 연결과 결제 플로우만, 디자인 요소 배제. FE 목표 10/12 · 카드사 심사 요청 10/14 전후. **위험: BE 10/4 스테이징 미충족 · 충전 API 제공일 재산정 중 (R-06)**. 10/4 `main` 에 BE-A 도메인 골격(`2c682e0`)만 — 업무 API · 스테이징 없음, 원화 결제는 충전 하나뿐 (R-01 토스 상담 10/10)
- **마지막으로 끝낸 작업**: (10/4) 최종 와이어프레임(Figma `와이어프레임 최종`, node `195:91`) 대조 — PHASES 1장 화면 목록을 새 화면 ID 로, 와이어와 팀 결정의 충돌 · 빈 곳을 Q-23 ~ Q-27 로 (FIGMA-FINAL), `main` 의 BE-A 골격(`2c682e0`)을 `boyeon` 에 병합. 그 전(10/3): 팀 문서 2차 반영 (`cc60ab3` — `docs/PENDING_DECISIONS.md` · `FUNCTIONAL_SPEC.md` · `PRD.md`). PD 요청(정리본 8번)으로 FE 가 고쳐 PR, **`boyeon` → `main` PR 검토 · 병합 대기** (DOCS-TEAM-PR). 같은 날: `boyeon` 을 `main` 병합 커밋 `90703b7` 에 fast-forward, `next.config.ts` 주석 보정(`e4ae66b`, lint · typecheck · test 73개 · build 통과). 그 전: PG-1 Idempotency-Key 헬퍼(`353d9b8`), PG-4 푸터 틀(`4453ba9`)
- **다음 작업**
  1. (사용자) PR `boyeon` → `main` — 검토 PD(전체 · P-02 50,000원 보너스 82 · 신살 관심 항목), BE-A(P-03 가격 변경 · 정가 필드 · P-03A 선물 견적 필드 · P-07 에 따른 API · G-07 24시간 유예 문구 · 정보 입력 마감 · G-09 환급), BE-B(T-01 부족한 기운 · `talismanType` 삭제 · T-06 묶음 · F-03 17종 계산)
  2. (사용자 → PD · BE) Q-23 ~ Q-27 전달 — 10/8 까지 필요한 것: Q-23 a(로그인 수단) · j(서비스명), Q-25(충전 결제 수단 4칸 · 동의 체크 — 심사 중 변경 금지)
  3. (FE) PR 병합 후 `src/lib/screens.ts` ROUTES 에 `/wallet` · `/share/[shareId]` 추가 → PG-3 `/wallet`(PAY-01 · PAY-02) 시작 (데이터 · 결제는 BE 충전 API · OpenAPI · R-09 후, 결제 수단은 Q-25 후)
  4. (FE) PG-1 나머지 — openapi-fetch 클라이언트 · 공통 응답 / 오류 code · CSRF 재시도 · API 경로 가리기. 스테이징 주소 또는 OpenAPI 스펙 파일을 받으면 (R-06 · Q-18)
  5. (FE) PG-4 값 — 사업자 명의(R-08) → 값 8개 · 사업자정보 공개페이지 링크(R-07). `/refund` 는 PD 확정 원고(10/10, 초안만 수령 — Q-21), `/about` 은 충전 상품 기준(R-01)
  6. (FE) PG-2 카카오 로그인 · 세션(HOME-01, 카카오 버튼만 — Q-23 a) — BE-B OAuth · 세션 API · 리다이렉트 URI(R-04) 후
  7. (팀) 10/8: Q-23 a · j, Q-25. 10/10: Q-27(결제 확인 팝업 · Checkout 경로), 부족한 기운 산출 · 기준일(Q-19, BE-B), 충전 환불 원고 확정(Q-21) · 토스 상담(R-01), 가격 표기 값(Q-22), 충전 `processing` · 충전 승인 키 확인(Q-17). 10/15: 선물 견적 필드 · 주문 API · 환급(Q-20, BE-A), 신살 17종 계산 · 문구(F-03), Q-23 나머지 · Q-24 · Q-26
- **보류 (재개 조건)**
  - 디자인 항목 — PG 심사 요청 후 (PG-FIRST): 토큰 CSS 변수(값 D-01), 프레임 `border-image`(에셋 D-03 10/10 · 10/15, **PD 가 손그림 테두리 사용 여부 미확정 Q-15**), 폰트(FONT-HOLD · D-02), 캐릭터 규격 + 뿌기 포즈(범위 R-02 해결, 합성 입력 Q-14 제안), 사이드 메뉴 운세 목록 정리(일반 5종 + 수능운, `TODO(D-10)` 취업운 제거)
  - 최종 와이어에만 있고 팀 결정과 부딪혀 만들지 않는 것 (Q-23 해결 전): 네이버 · 구글 로그인 버튼, 여러 명 선물(GIFT-06 ~ 08), 발송 실패 링크 복사(GIFT-05), 오늘의 운세 로그인 필수, 관계 칩(엄마 · 아빠 · 애인). 충전 화면 결제 수단 4칸 · 동의 체크는 Q-25 전까지 정하지 않는다
  - 팀 문서 PR(`cc60ab3`) 병합 전이라 만들지 않는 것: `/wallet` · 공유 랜딩 라우트(Q-01 · Q-10), 선물 위저드 · 선물 등껍질 결제(P-07 · P-03A, 세부 Q-20), 부적 자동생성 화면 흐름(T-01, 세부 Q-19), 단위 문구 "등껍질 N개"(P-03). 병합 후에도 API 는 OpenAPI 를 기다린다
  - iOS Safari 실기기 확인(AppShell · 404) — iPhone 확보 후 (R-03, PD 보유 · 대여 미정). Android Chrome · 카카오톡 인앱은 통과
  - openapi-fetch 클라이언트 · API 타입 · 관측 API 경로 가리기 — BE OpenAPI 수령 후 (PG-1)
  - Sentry 소스맵 업로드(`SENTRY_AUTH_TOKEN`) — Phase 7
- **막힌 점 · 전달할 것**
  - 팀에 받을 것(필요한 날은 `TEAM-QUESTIONS.md`): BE 재산정 일정(R-06) · OpenAPI 받는 방법 · `details`(Q-18) · API_SPEC · OpenAPI 반영(Q-17), 사업자 명의(R-08, 전원) → 사업자 정보 값(R-07), 토스 상담 결과(R-01) · 테스트 클라이언트 키(R-09), 카카오 리다이렉트 URI · JavaScript 키(R-04), 심사 테스트 계정(R-05), Q-19 · Q-20 · Q-21 · Q-22, 최종 와이어 대조 Q-23 ~ Q-27
  - 팀 문서 중 BE 가 고칠 것 (FE 는 손대지 않음, Q-17): `API_SPEC.md` · `COMMON_RESPONSE_AND_ERROR_CODES.md` · `ERD.md` — `talismanType` 삭제 · 생성된 부적 정보 응답, 선물 등껍질 주문(원화 `/gift-orders` → 토스 흐름 대체) · 선물 상태 이름, 수능운 단일 상품(`READING_ONLY` 없음), 정가 필드(Q-22), 충전 `processing`, 견적 필드 · `GET /quotes/{quoteId}`, CSRF, 선물 `delivery` · 재발송 새 링크. `BACKEND_ROLE_SPLIT.md` 의 선물 원화 결제 일정 문구
  - 팀 문서에 아직 없는 FE 쪽 답: O-06 서버 이벤트 `gift_alimtalk_sent/failed`(Q-12 해결, FUNCTIONAL_SPEC 16장에는 있음)
  - PD 에게 받은 것 (저장소 밖): 선물 재발송 안내 문구(확인 모달 · 남은 횟수 · 초과 · 간격 — Phase 5), 충전 환불정책 초안 `refund-policy-draft.md`(확정 전 코드 반영 금지). 재발송 "1분 뒤" 문구와 서버 `nextResendAt` 의 관계는 Phase 5 에서 PD 와 맞춘다
  - (PD 전달) 개인정보처리방침 국외 이전 고지 — PostHog(US) · Sentry(지역 확인 필요), 오류 정보 · 접속 기록
  - (PD 전달) 카카오톡 인앱브라우저의 떠 있는 버튼이 화면 오른쪽 가운데를 가린다 — 누르는 요소 배치 참고
  - (PD 전달) 오류 화면 · 404 화면 문구 — 지금은 자리표시 "오류가 발생했습니다" · "다시 시도" · "페이지를 찾을 수 없습니다" · "홈으로" (TODO(PD 문구)). 공통 응답 문서에 따라 5xx 화면에 `traceId` 를 보여 줄 수 있다
  - `app/error.tsx` 는 실기기에서 띄워 보지 못했다 — 첫 실제 API 연결 화면에서 오류 경로를 실기기로 확인한다
  - Claude Code 는 `frontend/` 에서 시작한다 — 루트에 `CLAUDE.md` 가 없다
  - Playwright 브라우저 미설치 — E2E 처음 돌리기 전에 `pnpm -C frontend e2e:install`
  - 알려진 작은 문제: PC 에서 스크롤바가 있는 긴 페이지는 사이드 메뉴 패널이 앱 기둥보다 몇 px 오른쪽으로 나갈 수 있다 (휴대폰 영향 없음)
  - `.github/pull_request_template.md` 에 "스크린샷 필수" 문구가 남아 있다 (팀 공용 파일이라 그대로, NO-SCREENSHOT 와 다름)

## 마감 체크

| 날짜 | 마감 | 상태 |
|---|---|---|
| 9/29 (화) | 구 D-01 · D-02 · D-12 · D-14 확정 | 닫힘 — 팀 결정 문서로 대체 (DECISION-IDS) |
| 10/4 (일) | BE 스테이징 · 공통 응답 · 로그인 골격 (BE 일정) · Phase 1 FE 골격 | ⬜ FE 골격 완료(디자인 보류). BE 는 골격만(`a605afa`), 스테이징 · 로그인 미충족 — 재산정 중 (R-06) |
| 10/10 (토) | BE 충전 · 토스 테스트 결제 · 디자인 에셋 1차 (D-03) | ⬜ BE 는 "10/10 이전 약속 어려움" (R-06) |
| 10/12 (월) | FE 심사용 사이트 (PG-1 ~ PG-5) | ⬜ |
| 10/14 (수) 전후 | 카드사 심사 요청 | ⬜ |
| 10/15 (목) | 디자인 에셋 확정 · 콘텐츠 1차 (D-03 · D-04) | ⬜ |
| 10/17 (토) | 본인 수능운 `사주/사주+부적` E2E | ⬜ |
| 10/20 (화) | 콘텐츠 검수 · PG 미승인 시 대안 판단 | ⬜ |
| 10/23 (금) | 선물 결제 → 알림톡 → 비로그인 결과 E2E | ⬜ |
| 10/27 (화) | 일반 5종 · 공유 · 창고 · 운영 시나리오 | ⬜ |
| 10/28 (수) 18:00 | 코드 프리즈 | ⬜ |
| 10/29 (목) | 운영 환경 실결제 + 환불 확인 | ⬜ |
| 10/31 (토) | 출시 | ⬜ |

## 결정 로그

| ID | 결정 | 날짜 | 결정자 |
|---|---|---|---|
| FIGMA-FINAL | 최종 와이어프레임 = Figma 페이지 `와이어프레임 최종`(node `195:91`). 화면 ID 는 프레임 이름(HOME-01 · FORT-06 · CSAT-04 · GIFT-03 · RECV-T-04 등)을 쓴다. 옛 페이지 `17:2` 와 구 번호 #1 ~ #40 은 기록용(PHASES 1장 "구 #" 열). 와이어와 팀 문서가 다르면 팀 문서를 따르고 Q 로 올린다(Q-23 ~ Q-27). 와이어 문구 · 수량은 자리표시 | 2026-10-04 | 사용자 (PD 최종본) |
| DOCS-TEAM-PR | 팀 문서(`docs/`) 반영은 FE 가 고쳐 `boyeon` → `main` PR 로 올리고 PD 가 검토한다 (PD 답변 8번). FE 범위: 기획 결정과 BE-A 제안 중 PD 가 동의한 결정. `API_SPEC` · `COMMON` · `ERD` · `BACKEND_ROLE_SPLIT` 의 계약 변경은 BE. 첫 반영 `cc60ab3` (PENDING_DECISIONS · FUNCTIONAL_SPEC · PRD). 병합 전에는 그 결정에 기대는 코드를 만들지 않는다 (TEAM-QUESTIONS 상태 `반영 PR 대기`) | 2026-10-03 | 팀 (PD 요청, 사용자) |
| P-03 · P-03A 변경 | PD 가격: 일반 `사주` 10 · `사주+부적` 15 · 부적 추가 10, 수능운 15(부적 포함 단일, 정가 20 할인 표기), 선물(수능운) `부적` 10 · `부적+사주` 15 — 단위 등껍질 N개. 보너스 유효기간 1년(P-04). 화면에는 서버 값만 (Q-22). 팀 문서 `cc60ab3` | 2026-10-03 | PD |
| TEAM-1003 | 팀 결정(사용자 전달): 선물도 **등껍질** 결제(원화는 충전만, P-07 변경), 1차 선물은 **수능운만**(핵심 전제 변경), 부적은 **고르지 않고 결과 기반 자동생성**(T-01 변경), 단위 표기 **"등껍질 N개"**(P-03), 신살은 일반 5종에 포함(잠정, F-03 미정). 팀 문서 반영 전에는 이 결정에 기대는 코드를 만들지 않는다 (TQ-ANSWERS) | 2026-10-03 | 팀 |
| CONFIRM-KEY | 충전 승인(`POST /top-up-orders/{orderId}/confirm`)의 `Idempotency-Key` 는 서버 주문 ID(UUID)를 그대로 쓴다 — PG 복귀 페이지 새로고침에도 저장소 없이 같은 키. 그 밖의 멱등 명령은 구매 의도당 무작위 UUID 하나 + 본문 직렬화 고정 (`src/lib/api/idempotency.ts`, `353d9b8`). BE-A 확인 요청 (Q-17) | 2026-10-03 | FE |
| FILE-HANDOFF | 지시문으로 넘기는 파일(문서 zip · 코드 파일)은 사용자 PC 의 `~/Downloads`(`$HOME/Downloads`)에 받아 두고 SHA-256 으로 대조한다. 코드 본문을 지시문에 직접 붙이지 않는다 (붙여 넣기 중 잘린 적이 있다) | 2026-10-03 | 사용자 |
| TQ-ANSWERS | 팀 답 1차 반영 (PD 답변서 · BE 답변서 · BE-A 결정 기록 1~42). `TEAM-QUESTIONS.md` 상태를 `해결` · `답변 · 반영 대기` · `답 엇갈림` · `부분` · `대기` 로 나눈다. `반영 대기` · `엇갈림` 항목은 팀 문서(PENDING_DECISIONS · FUNCTIONAL_SPEC · API_SPEC)에 들어가기 전까지 그 답에 기대는 코드를 만들지 않는다. 답변 원문은 저장소에 두지 않고 요지만 적는다 | 2026-10-03 | FE |
| TOPUP-DONE | 충전 완료는 주문 `CREDITED` 일 때만 표시. `PAID` · `processing: true` 는 처리 중. 승인 결과가 불명확하면 주문 조회 2초 간격 최대 30초 → 확인 중 안내 + 주문 확인 버튼, 새 결제 · 새 멱등 키로 유도하지 않음 (BE-A 결정 17 · 39 · 42 채택, 계약 반영은 Q-17) | 2026-10-03 | FE |
| PURCHASE-RESTORE | 잔액 부족 → 충전 → 복귀 때 구매 선택은 `sessionStorage` 에 둔다 — 인물 ID · 최소 선택값만(생년정보 원문 금지), 마지막 변경 후 24시간, 읽을 때 만료 검증, 구매 성공 · 로그아웃 시 삭제, 저장한 가격 · 잔액은 표시 근거가 아님(`GET /quotes/{quoteId}` 재확인). 전역 상태가 아니며 Zustand 를 쓰지 않는다. FE 브라우저 저장소 규칙 확장 (Q-07, BE-A 결정 1 · 15 채택) | 2026-10-03 | FE |
| NO-SCREENSHOT | 실기기 확인 결과는 사용자가 말로 알려 준 것으로 받는다. FE 문서의 "스크린샷 첨부" 요구를 뺀다 (PR 템플릿 문구는 팀 공용이라 그대로) | 2026-10-03 | 사용자 |
| DOCS-GITHUB | 팀 문서의 원본은 GitHub 저장소 — 루트 `docs/`(PENDING_DECISIONS · PRD · FUNCTIONAL_SPEC · API_SPEC · COMMON_RESPONSE_AND_ERROR_CODES · ERD)와 `backend/docs/`. 결정 원본은 `docs/PENDING_DECISIONS.md`. Notion 스냅샷 방식(TEAM-DOCS)은 폐기하고, 10/1 스냅샷 묶음(`docs-20261001.zip`)은 배치하지 않는다 | 2026-10-03 | FE (사용자) |
| P-02 변경 | 충전 상품 6종으로 변경 — 1,000원 10개(보너스 없음) · 3,000원 33개(30+3) · 5,000원 56개(50+6) · 10,000원 114개(100+14) · 30,000원 346개(300+46) · 50,000원 582개(500+82). 팀 문서 PENDING_DECISIONS · PRD · FUNCTIONAL_SPEC · API_SPEC 반영 | 2026-10-03 | 사용자 |
| STYLE-HAND | 비주얼은 손그림체. 손그림 SVG 프레임 `border-image` 와 레이어 분리 벡터 캐릭터, 도트(픽셀) 처리 안 함. 10/3 재확인 — 팀 문서 D-01 · D-05 · PRD 1장 · FUNCTIONAL_SPEC 1장 · BACKEND_ROLE_SPLIT 의 도트 문장을 손그림으로 고침 | 2026-10-01 | FE (사용자) |
| PG-FIRST | PG 심사 통과용 흐름이 최우선. 10/3 보완: **PG 심사 요청 전까지 디자인 요소(토큰 · 프레임 · 글꼴 · 캐릭터 · 꾸밈)를 배제하고 백엔드 연결과 결제 플로우만** 만든다. 디자인 항목은 PG 심사 요청 후 Phase 6 에서 재개 | 2026-10-01 | 사용자 |
| PG-TRACK | PG 심사 트랙 체크박스 v2 — PG-1 백엔드 연결 기반 · PG-2 카카오 로그인 · 세션 · PG-3 충전 결제 플로우 · PG-4 심사 노출 요건(푸터 · /about · 약관, 스타일 없이) · PG-5 운영 도메인 실기기. 옛 P-1 ~ P-6 · PG-1 ~ PG-6 번호 대체. 팀 결정 ID P-01 ~ P-09 와 헷갈리지 않게 `PG-` 를 붙인다. 옛 Phase 2 는 이 트랙에 흡수 | 2026-10-03 | FE |
| TOSS-SDK | `@tosspayments/tosspayments-sdk`(토스페이먼츠 SDK v2, 결제창) 추가 승인 — PG-3 용. 설치는 사용자가 직접 | 2026-10-01 | 사용자 |
| VIEWER | 결과 화면은 API 섹션 타입(`TEXT` · `PERIOD_GUIDANCE` · `FOOD_RECOMMENDATION` · `CHECKLIST`)별로 그리는 ReadingViewer 하나로 수능 · 일반 5종 · 선물 수신에 재사용한다. SuneungResultViewer 대체 | 2026-10-01 | FE |
| DECISION-IDS | FE 의 구 D-01 ~ D-16 을 닫고 팀 결정 ID(P- · F- · G- · A- · S- · T- · X- · O- · D- · I-)로 부른다. 대응표는 `PHASES.md` 7장 | 2026-10-01 | FE |
| TQ-RESET | `TEAM-QUESTIONS.md` 를 GitHub 팀 문서 기준으로 새로 쓰고 번호를 새로 매긴다 (10/1 판은 저장소에 들어간 적 없고 팀 문서가 바뀌어 폐기) | 2026-10-03 | FE |
| ~~TEAM-DOCS~~ | ~~팀 문서 4개를 Notion 원본의 날짜 스냅샷으로 루트 `docs/` 에 둔다~~ → DOCS-GITHUB 로 대체 (배치된 적 없음) | 2026-10-01 | FE |
| DOCS-PLACE | DOCS-WEB-ONLY 보완: 지시문에 파일 목록과 SHA-256 이 있으면 Claude Code 가 웹 대화 완성본을 압축 해제 · 지정 경로로 복사 · 지정 문서 삭제/이동까지 한다. 내용 변경 금지, 배치 후 해시 확인, 불일치 시 커밋 금지 | 2026-09-30 | FE |
| DOCS-FE-OWN | `PHASES.md` · `PROGRESS.md` 를 루트 `docs/` 에서 `frontend/docs/` 로, 루트 `CLAUDE.md` 를 `frontend/CLAUDE.md` 로 옮긴다. 셋 다 FE 개인 작업 문서이며 팀 합의 문서가 아니다. 루트 `docs/` 는 API 명세처럼 팀이 함께 쓰는 문서 자리로 비워 둔다. DOCS-SPLIT 대체 | 2026-09-30 | FE |
| OBS-PRIVACY | 관측 수집 범위: Sentry 오류 · PostHog 페이지뷰만. 화면 녹화 · 자동 수집 끔, `sendDefaultPii` 끔. URL 은 `maskUrl` 로 동적 경로를 패턴으로 · 쿼리 · 해시 제거 후 전송(선물 토큰 · 결제 파라미터 보호). PostHog 지역 US, Sentry 지역은 조직 설정에서 확인 후 기입. Vercel 빌드는 관측 키 누락 시 실패 | 2026-09-30 | FE |
| FONT-HOLD | 본문 · 제목 글꼴 모두 보류(Pretendard 포함). 결정 전까지 코드에서 글꼴을 지정하지 않는다. 결정되면 FE 가 통보받아 적용 | 2026-09-30 | FE |
| FIGMA-WEB-ONLY | Figma 조회는 웹 대화에서만 한다. Claude Code 는 Figma MCP 를 호출하지 않고, 지시문에 적힌 Figma 값만 쓴다 | 2026-09-30 | FE |
| DOCS-WEB-ONLY | 저장소 문서(`*.md`)는 Claude Code가 수정하지 않는다. 문서 변경(PROGRESS·PHASES 갱신 포함)은 웹 대화에서 작성한 완성본으로만 하고, Claude Code는 diff 확인·커밋만 한다. 팀 문서(`docs/` · `backend/docs/`)를 FE 쪽에서 고칠 때도 같다 (10/3 보완) | 2026-09-30 | FE |
| BRANCH | FE 코드 작업은 전부 `boyeon` 브랜치에서 한다. `main` 직접 푸시 금지, `boyeon` → `main` PR. 기존 `feat/`·`fix/`·`chore/` 규칙 폐기 | 2026-09-30 | FE |
| DOCS-RESTORE | 저장소 문서의 용어·문구를 원본 초안 기준으로 복원 (오행 5색, 수능운, 인앱브라우저, 행정, 요일 등). D-11 확정·문서 분리·REPO 결정은 유지 | 2026-09-30 | FE |
| NAME | 서비스명 **뿌기사주** 확정 (D-11). 도메인·OG·심사용 페이지에 이 이름을 쓴다. 캐릭터 이름도 `뿌기`로 통일 | 2026-09-26 | PD |
| DOCS-SPLIT | ~~`docs/`는 팀 공용 문서만(PHASES·PROGRESS).~~ → DOCS-FE-OWN 으로 대체. FE 전용 내용(스택 상세·손그림 톤·컴포넌트 맵)은 `frontend/docs/FRONTEND.md`로 분리. README도 루트=공용 / `frontend/README.md`=FE 전용 | 2026-09-26 | FE |
| FE-STACK | 프론트엔드 스택 확정: Next.js(App Router)+TS, Tailwind, `border-image` 손그림 프레임, vaul/Radix, TanStack Query, openapi-fetch, RHF+Zod, Zustand(위저드 한정), dayjs, next/og, Biome/Vitest/Playwright, Sentry/PostHog | 2026-09-26 | FE |
| REPO | 저장소 1개(`saju-project`) 안에 `frontend/`와 `backend/`를 폴더로 분리. `docs/`는 루트. ~~백엔드 스택(D-14) 확정 전까지 `backend/`는 비워 둔다~~ → 백엔드 확정(I-01)으로 해제 | 2026-09-26 | FE |

## 실험 로그 (Phase 8)

| 실험 | 가설 | 기간 | 판정 기준 | 결과 |
|---|---|---|---|---|
| (아직 없음) | | | | |

## 세션 로그

- 2026-10-04 · 최종 와이어프레임(`195:91`) 대조로 FE 문서 v2.4 — PHASES 1장 화면 ID 재작성 · FRONTEND 3장 · `frontend/CLAUDE.md` Figma 규칙 · Q-23 ~ Q-27 (FIGMA-FINAL). `main` BE-A 골격 `2c682e0` 확인(업무 API 없음, G-07 유예 규칙 확인 → Q-17) 후 `boyeon` 에 병합.
- 2026-10-03 · PD 추가 답변 반영(가격 · 부적 자동생성 · 신살 · BE-A 제안 결정 동의 · 재발송 문구 · 충전 환불 초안). `boyeon` 을 `main`(`90703b7`)에 fast-forward, `next.config.ts` 주석(`e4ae66b`), 팀 문서 2차 반영(`cc60ab3`, DOCS-TEAM-PR). FE 문서 v2.3: TEAM-QUESTIONS 상태 `반영 PR 대기` 추가 · Q-22 신규. PR `boyeon` → `main` 검토 대기.
- 2026-10-03 · PG-1 일부: Idempotency-Key 헬퍼(`353d9b8`). `main` 병합(`def95cd`). 팀 결정 반영 문서 v2.2(TEAM-1003 · CONFIRM-KEY · FILE-HANDOFF), TEAM-QUESTIONS Q-19 ~ Q-21 신규. 팀 정리본 `TEAM-DECISIONS-NEEDED` v1 · v2 작성(저장소 밖).
- 2026-10-03 · 문서 v2.1: 팀 답 1차 반영(TQ-ANSWERS) — TEAM-QUESTIONS 상태 갱신 · Q-16 ~ Q-18 신규, PHASES · FRONTEND · CLAUDE 갱신(CSRF · TOPUP-DONE · PURCHASE-RESTORE · NO-SCREENSHOT), `boyeon` 에 `main`(BE 골격 `a605afa`) 병합 — 실제 병합은 `def95cd`. 코드 변경 없음.
- 2026-10-03 · PG-4 사업자 정보 푸터 틀(`d42ba99` · `22f9712` · `4453ba9`): `business.ts` 8개 항목(토스 6 + 전자우편주소 · 호스팅 제공자), 값 없음은 "(미정)", 약관 링크 간격. TEAM-QUESTIONS R-07 확장 · R-08 · R-09 판은 팀에만 전달됐고 저장소에는 v2.1 로 반영.
- 2026-10-03 · 문서 v2: GitHub 팀 문서(PR #5) 반영 — 원본 GitHub 전환(DOCS-GITHUB), PHASES · PROGRESS · FRONTEND · CLAUDE · README 갱신, TEAM-QUESTIONS 신규(TQ-RESET). PG 트랙 v2(PG-TRACK) · 디자인 배제(PG-FIRST 보완). 팀 문서에 P-02 충전 상품 6종 · D-01/D-05 손그림 반영. 코드 변경 없음.
- 2026-10-01 · 문서 v1(PG-FIRST · TOSS-SDK · STYLE-HAND · VIEWER · DECISION-IDS · TEAM-DOCS) 작성 — 저장소에 배치하지 않고 10/3 v2 로 대체. 코드 변경 없음.
- 2026-09-30 · 문서 구조 변경(DOCS-FE-OWN): PHASES · PROGRESS · CLAUDE 를 `frontend/` 로 옮겨 FE 개인 문서로 전환, 루트 README · PR 템플릿 · backend README 정리. 완성본 배치를 Claude Code 에 허용(DOCS-PLACE). 코드 변경 없음.
- 2026-09-30 · Phase 1 FE: 라우트 오류 화면 `error.tsx`(AppShell 유지 · Sentry · `retry`) · 404 `not-found.tsx` · `global-error.tsx` `retry` 통일. 실기기 Android Chrome · 카카오톡 인앱 통과, iOS 보류. 문서: Phase 1 체크박스 추가.
- 2026-09-30 · Phase 1 FE: 라우트 26개 · AppShell · 공통 컴포넌트 7종 · TanStack Query 기본 설정 · Sentry/PostHog(URL 가리기). Phase 0: Vercel 연결 · main 보호. CI typecheck 수정. 결정 OBS-PRIVACY · FONT-HOLD · FIGMA-WEB-ONLY.
- 2026-09-30 · 문서 원본 복원·확정(DOCS-RESTORE), 브랜치 규칙 `boyeon`(BRANCH), 문서 변경 웹 대화 경유 규칙(DOCS-WEB-ONLY). 화면 코드 없음.
- 2026-09-26 · 서비스명 `뿌기사주` 확정 반영(D-11), FE 문서 분리(`frontend/docs/FRONTEND.md`), README 공용/FE 분리. 화면 코드 없음.
- 2026-09-26 · 저장소 초기 세팅. `saju-project/` 생성, git init, Next.js 16(App Router·TS·Tailwind 4·Biome) 스캐폴드, 계획 라이브러리 전부 설치·버전 고정, Vitest/Playwright 설정, `/api` rewrites 골격, PR 템플릿·CI·문서 배치, GitHub 원격 연결·첫 푸시. 화면 코드는 아직 없음.
- 2026-09-26 · 프론트엔드 스택 확정 및 문서 반영. 코드 없음.
- 2026-09-26 · 계획 문서 초안 작성 (PHASES/PROGRESS/CLAUDE). 코드 없음.
