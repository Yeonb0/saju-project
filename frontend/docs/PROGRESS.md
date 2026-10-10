# PROGRESS

> **FE 개인 작업 문서다. 팀 합의 문서가 아니다** (DOCS-FE-OWN).
> 모든 세션은 시작할 때 같은 폴더의 `PHASES.md` · `TEAM-QUESTIONS.md` 와 이 파일을 읽는다. 갱신은 웹 대화에서 작성한 완성본으로만 한다 (`frontend/CLAUDE.md` "문서 변경 규칙").
> 갱신할 때는 "현재 상태"를 덮어쓰고, "세션 로그"에는 맨 위에 한 줄씩 추가한다.

## 현재 상태

- **현재 단계**: **PG 심사 트랙 (PG-FIRST)** + **가짜 구현(MOCK-PORT)** + **진짜 adapter 시작(ADAPTER-HTTP)**. 최종 와이어의 글자 · 상자 배치는 넣는다 (LAYOUT-FIGMA) — 글꼴 · 손그림 외곽선 · 색 토큰은 팀 문서 반영(Q-32) · 적용 시작(사용자 결정) 전까지 넣지 않는다. 캐릭터 · 아이콘 · 이미지 에셋은 자리만, 와이어 문구는 자리표시. 코드는 Claude Code, 웹 대화는 명세 · 검토 · 작업 공간 재현 · 변이 확인 (CODE-BY-CC · SCREEN-CHECK). **위험: BE 스테이징 · 세션(카카오 로그인 · `GET /session`) API 없음 (R-06) — 진짜 모드 연결은 세션 adapter(CSRF 출처)에 막혀 있다. FE 목표 10/12 · 심사 요청 10/14 의 진짜 연결은 BE 일정에 달렸다**
- **10/10 오전 새로 들어온 것**: (1) BE-A PR #17 `main` 병합(`f47b719`, 10/9 밤) — **OpenAPI 파일 `docs/openapi/api-v1.json`**, 견적 자금 필드(`walletBalance` · `balanceAfter` · `shortage` · `recommendedTopUp`) · `charged` 통일 · `GET /quotes/{quoteId}` · `GET /wallet`(유료 · 보너스) · `POST /top-up-orders`(예정 지급량) · 오행 `{ personId }` 입력, `backend/docs/FE_REQUESTS_20261009.md`. `boyeon` 에 병합(`f207fd9`). (2) BE-B 답변서 2종(10/9) · BE-A FE 전달(10/10) · 사업자등록증 → TEAM-QUESTIONS 반영(`1a9d877`): Q-30 · R-08 해결, Q-34 · Q-35 대부분 해결, 새 질문 Q-37(수능 결과 구조) · Q-38(구매 결과 · 환급 확인). (3) 팀 회신 문서 `뿌기사주-FE-회신-20261010.md`(저장소 밖, 사용자가 팀에 공유)
- **일정 위치 (10/10 오전)**: PG 트랙 가짜 범위 배치는 끝났고, 진짜 연결 준비(생성 타입 · adapter 틀 · 오행 · 지갑 · 주문 생성)를 했다. 남은 PG 일: 세션 adapter(BE-B 세션 API 대기) → `ports/index.ts` 진짜 연결, 충전 승인 · 상품 목록(Q-17 · Q-28), 토스 SDK(R-09), 푸터 값 4개(R-07) · 원고(Q-21 · Q-36), 실기기
- **마지막으로 끝낸 작업** (10/10, `boyeon` `c46bfa7`): 전부 웹 대화가 작업 공간에서 diff 재현 · 변이 확인 후 커밋
  - `main` PR #17 병합 (`f207fd9`), TEAM-QUESTIONS 팀 답 반영 (`1a9d877`, 웹 대화 직접 push)
  - COMMON-01 사업자 값 4개 — 상호 · 대표자 · 사업자등록번호 · 주소, 사업자등록증 그대로 (`5bee76c`)
  - 구매 결과 처리 — 환급 문장은 서버 `REFUNDED` 일 때만, `FULFILLED` + `readingId` 일 때만 결과 이동, 처리 중은 같은 키로 결과 확인 (`8feb588`, Q-38)
  - OpenAPI 생성 타입 + 동기화 테스트 · adapters 밖 사용 금지 테스트 (`2c9a17b`)
  - 첫 adapter — 오행분석 (`e6a603a`), 충전 지갑 · 주문 생성 (`c46bfa7`). 둘 다 `ports/index.ts` 에는 아직 연결하지 않았다 (세션 adapter 후)
  - 테스트 67 파일 · 558 개
- **화면 현황 (10/10, 최종 와이어 기준)** — 10/9 와 같음
  - 동작 + 배치: HOME-01 ~ 04, FORT-01 ~ 05, MATCH-01 ~ 03, CSAT-01 ~ 06, PAY-01 ~ 05, MY-01, INFO-01 · 02, COMMON-01, (팝업) 차감 확인 · 잔액 부족 — 전부 가짜 모드. 진짜 모드는 adapter 연결 전이라 "진짜 구현 없음" 오류
  - 부분: FORT-06 · 07 결과 — 결과 고정 필드 순서는 Q-35 1 해결, 요약 Q-05 · 공유 G-11 · 부적. 수능 결과 구조 Q-37
  - 보류: TODAY-01 · 02 (Q-31 답 왔으나 API_SPEC 7장 · OpenAPI 미반영), GIFT-06 ~ 08 · MY-02 (팀 결정)
  - 규칙으로 막음: GIFT-01 ~ 05 · RECV-* · FORT-08 · 09 · CSAT-07 · TALBOX — 선물 · 부적은 OpenAPI 전 가짜 포트로도 만들지 않는다 (부적 · 선물 HTTP API 는 아직 OpenAPI 에 없음)
- **다음 작업** (막힌 것은 건너뛴다)
  1. (사용자 → 팀) 회신 문서 공유 — Q-37 수능 결과 구조(BE-B · PD) · Q-38 환급 확인(BE-A · BE-B) · R-06 스테이징 · R-09 토스 키 · PD 항목(신살 · 정책 초안 수정 · 푸터 값 4개 · Q-33)
  2. (FE, 지금 가능) 일반 운세 견적 · 구매 포트를 카테고리 계약에 맞추기 — 선택값에 `productOption` · `questionKey`, 수능운 `charged` · 자금 필드(가짜 · 포트 모델) → 그다음 견적 · 구매 adapter. 상품명 · 정가(Q-28) · `relationType` 대응(Q-35 2) · 질문 선택지 문구(Q-26)는 자리만
  3. (FE, 답 오면) 세션 adapter(BE-B 세션 API · OpenAPI) → `ports/index.ts` 진짜 연결, 충전 승인 · 주문 조회(Q-17), 상품 목록(Q-28), 결과 adapter(Q-37), 오늘의 운세(Q-31 반영 후)
  4. (사용자) `boyeon` → `main` PR, 미리보기 실기기 (Android Chrome · 카카오톡 인앱)
  5. (FE) 푸터 남은 값 4개(R-07) · 정책 원고(Q-21 · Q-36) 오면 넣기
- **`main` 배포 주의**: `main` 배포(가짜 모드 금지)에서 `/login` · `/onboarding` · `/wallet` · `/about` · `/suneung` · `/me` · `/fortune/*` 가 "진짜 구현 없음" 오류 화면 (의도된 시끄러운 실패). 팀 확인은 `boyeon` 미리보기 주소로만 한다. 진짜 로그인은 고정 스테이징 · 운영 도메인에서만 된다 (R-04 — 미리보기 브랜치 주소는 카카오 Redirect URI 로 못 쓴다)
- **보류 (재개 조건)**
  - 디자인 — 글꼴(FONT-HOLD), 손그림 외곽선(Q-32), 캐릭터(D-06 · T-01 · R-02 — 자리만), 아이콘 에셋, 색 토큰(Q-32): 팀 문서 반영 · 적용 시작 결정 후
  - 네이버 · 구글 로그인 버튼 — PG 심사 후 (LOGIN-3 개정, BE-B 도 같은 답 — Q-36 3)
  - 오늘의 운세 TODAY-01 · 02 — Q-31 이 API_SPEC 7장 · OpenAPI 에 들어간 뒤
  - 결과 FORT-06 · 07 · CSAT-04 — Q-37 · Q-05 · G-11 · 부적 규칙
  - 질문 화면 입력 — 선택지 문구(Q-26), MATCH-03 관계 질문 여부(Q-26 · Q-35 2), 고민 입력은 1차 미반영 · 입력 칸 없음(BE-B 답)
  - PAY-01 동의 · 안내 문구, PAY-05 취소 · 실패 문구, 다시 결제 시 상품 유지 — Q-33 · Q-25 · Q-21
  - PAY-03 지급 수량 — 승인 · 지급 API(Q-17). 주문 예정량으로 표시하지 않는다 (TOPUP-DONE)
  - 정책 원고 · 시행일 · 가입 약관 동의 · 만 14세 — Q-36 · Q-21 · R-07 (BE-B 제안: 동의 버전 · 시각 기록, 만 14세 확인 기록)
  - 신살 — 17종 계산 미구현(BE-B), 1차 판매 여부 PD (Q-05 · F-03, 10/15)
  - 타인 정보 입력(MY-02 · 궁합 상대 새로 입력) — 권한 확인 문구 PD 후. 본인 수정 경로 Q-29
  - 결과 고지 문구 — PD (F-08). 시간 미상 오행 고지 — F-08 문구 후
  - OAuth 취소 · 오류 복귀 — BE-B 콜백 확정 후. `/pay/success` 로그인 필요 링크의 `returnTo` — 진짜 세션 연결 때 (`TODO(PG-2)`)
  - 선물 위저드 · 선물 결제 · 부적 화면 — OpenAPI 대기 (Q-17 · Q-20), 가짜 포트로도 만들지 않는다 (선택지 B)
  - iOS Safari 실기기 — iPhone 확보 후 (R-03). Sentry 소스맵 — Phase 7
- **막힌 점 · 전달할 것**
  - 팀에 받을 것: 10/10 회신 문서(Q-37 · Q-38 · R-06 · R-09 · PD 항목). 세부는 `TEAM-QUESTIONS.md` (Q-17 ~ Q-38, R-01 · R-03 ~ R-09)
  - 팀 문서 중 BE 가 고칠 것 (FE 는 손대지 않음): `API_SPEC.md` 2장 `ReadingSectionType` 과 수능 결과 실제 모양(Q-37), 7장 오늘의 운세(Q-31 답 반영), 2장 관계 대응표(Q-35 2 — 또는 FE 가 A-07 PR), 상품 메타데이터 · `productName` · 정가 · `GET /products`(Q-28), 충전 승인 · `processing`(Q-17), 구매 상태 조회(Q-38)
  - FE 가 팀 문서 PR 로 고칠 것 (DOCS-TEAM-PR, 사용자 동의 후): A-01 개발 범위(심사 전 카카오만 · 심사 후 구글 · 네이버, Q-36 3), Q-19 답(T-01 · T-02, PD 확인 후), Q-32 디자인 시스템
  - 가짜 구현 환경 변수 (사용자가 `frontend/.env.local` 에 직접, 바꾸면 `pnpm dev` 재시작): `NEXT_PUBLIC_API_MODE=mock`, `NEXT_PUBLIC_MOCK_SESSION_SCENARIO`(signed_out · new_user · signed_in · signed_in_without_person · signed_in_with_other, 비우면 signed_out), `NEXT_PUBLIC_MOCK_TOP_UP_SCENARIO`(credited · paid_then_credited · stuck_paid · confirm_lost · processing_409 · rejected, 비우면 credited), `NEXT_PUBLIC_MOCK_FORTUNE_SCENARIO`(fulfilled · generation_failed · processing_409 · quote_expired, 비우면 fulfilled). 미리보기에서는 왼쪽 아래 "가짜" 조작판이 우선 (MOCK-PANEL). 가짜 상태는 탭 메모리 — 새로고침하면 처음으로
  - Vercel "Automatically expose System Environment Variables" 켜짐(10/4) — 운영 mock 차단 · Sentry 키 검사가 기댄다
  - `pnpm test` — 보통 35 ~ 60초. 10/10 에 한 번 `HomeScreen.test.tsx` "c. 로그인이면 지갑 픽스처 잔액(7)" 이 실패하고 재실행에서 통과(작업 공간 부하 재현 안 됨 — `findByRole` 기본 대기 1초 초과로 추정). 또 나면 실패 로그를 받아 원인 확인. 같은 테스트의 이름 정규식 `/^7s*충전하기$/` 는 `\s*` 의 오타(지금 통과에는 영향 없음) — 다음 HomeScreen 작업 때 고친다
  - 명세 정확도 (웹 대화 자체 점검): "이 조건을 빼면 이 테스트가 실패해야 한다" 요구가 10/10 에 두 번 더 성립하지 않았다(59단계 status 조건 — 테스트 픽스처의 readingId 가 null 이라 다른 조건이 막음, 61단계 정렬 — zod 가 스키마 순서로 키를 만듦). 웹 대화가 작업 공간 변이로 확인해 59단계는 테스트를 더했다. 61단계에서 `satisfies` 를 리터럴이 아닌 값에 쓰면 이름 대조가 안 된다는 것도 변이로 찾았다 (생성 타입 필드가 모두 선택이라)
  - PD 에게 받은 것: 선물 재발송 안내 문구(저장소 밖). 정책 초안 3종은 `docs/policies/`(`8fffac0`) — 확정본 전 코드 반영 금지
  - 사업자등록증 원본은 저장소에 두지 않는다 (대표자 개인정보 포함). 푸터에는 4개 값만
  - 유저 플로우: FigJam `dd8IamO1coU9vpa4P7AgMi`(v1, 10/4). 와이어 배치 값은 웹 대화가 Figma 에서 읽어 지시문에 숫자로 넘긴다
  - (PD 전달) 개인정보처리방침 국외 이전 고지 — PostHog(US) · Sentry(지역 확인 필요)
  - (PD 전달) 카카오톡 인앱브라우저의 떠 있는 버튼이 화면 오른쪽 가운데를 가린다
  - `app/error.tsx` 는 실기기에서 띄워 보지 못했다 — 첫 실제 API 연결 화면에서 확인
  - 테스트 출력의 jsdom 경고 "Not implemented: navigation to another Document" 는 `AppShell.test.tsx` 사이드 메뉴 링크 테스트가 낸다 (통과에 영향 없음)
  - Claude Code 는 `frontend/` 에서 시작한다 — 루트에 `CLAUDE.md` 가 없다 (루트 `AGENTS.md` 는 BE 운영 규칙 — FE 작업 규칙은 `frontend/CLAUDE.md`)
  - Playwright 브라우저 미설치 — E2E 처음 돌리기 전에 `pnpm -C frontend e2e:install`
  - `.github/pull_request_template.md` 에 "스크린샷 필수" 문구가 남아 있다 (팀 공용 파일이라 그대로)

## 마감 체크

| 날짜 | 마감 | 상태 |
|---|---|---|
| 9/29 (화) | 구 D-01 · D-02 · D-12 · D-14 확정 | 닫힘 — 팀 결정 문서로 대체 (DECISION-IDS) |
| 10/4 (일) | BE 스테이징 · 공통 응답 · 로그인 골격 (BE 일정) · Phase 1 FE 골격 | ⬜ FE 골격 완료(디자인 보류). BE 는 골격만(`a605afa`), 스테이징 · 로그인 미충족 — 재산정 중 (R-06) |
| 10/10 (토) | BE 충전 · 토스 테스트 결제 · 디자인 에셋 1차 (D-03) | ⬜ BE-A 충전 주문 생성 · 지갑 API 는 `main`(PR #17), 승인 · 지급 · 스테이징 없음 (R-06). FE 는 생성 타입 · adapter 2개 |
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
| ADAPTER-HTTP | 진짜 adapter 는 openapi-fetch 대신 `src/lib/api/http.ts`(`createApiClient` — 공통 껍데기 검사 · CSRF 1회 재시도 · 멱등 키)로 요청하고, 응답 `data` 를 zod 로 필수 · 정확한 모양까지 런타임 검사한 뒤(실패면 `ApiContractError`) 검사 값을 **객체 리터럴 + `satisfies` 생성 타입**으로 대조해(필드 이름이 바뀌면 typecheck 실패 — 생성 타입 필드가 모두 선택이라 리터럴이 아닌 값에는 대조가 안 된다) 포트 모델로 바꾼다. 경로는 `satisfies keyof paths`, 요청 본문은 `satisfies` 요청 스키마. 생성 타입은 `adapters/` 에서만(테스트로 막음). 모르는 enum 값은 UNKNOWN. 기준 파일 `adapters/basicSaju.ts`(`e6a603a`) | 2026-10-10 | 사용자 (선택지 A http.ts + zod · B openapi-fetch 중 A) |
| LOGIN-3 개정 | PG 심사까지 로그인 화면(HOME-01)에는 카카오 버튼만 둔다. 네이버 · 구글 버튼은 나중에 추가한다 — 세션 포트 `startLogin(provider, …)` 와 가짜 3종은 그대로 둔다(`4717e77`). 버튼 배치 값은 와이어 224:1087 · 195:781 | 2026-10-09 | 사용자 |
| POLICY-DRAFTS | PD 정책 초안 3종을 팀 문서 `docs/policies/`(TERMS · PRIVACY · REFUND + README)에 보관한다(`8fffac0`). 확정본(빈칸 채움 · Q-36 답) 전에는 FE 코드(INFO-02)에 넣지 않는다. 옮기면서 Notion 내보내기의 깨진 링크 두 곳만 고쳤다(README 에 기록) | 2026-10-09 | 사용자 |
| BASIC-SAJU-PORT | 오행분석 포트 입력은 `personId`(FE 모델)다. `POST /fortune/basic` 의 요청 모양(생년정보 원문 또는 personId, Q-35 3)은 진짜 adapter 가 맞춘다. 포트에는 화면이 쓰는 값만(오행 5개 · 시간 미상 여부 · 계산 버전) | 2026-10-09 | FE |
| DESIGN-SYSTEM | 디자인 시스템 확정본은 Figma `iFtLxChBQf3nQWaltdrJ37` 페이지 `디자인 시스템`(`67:66`) — 색은 의미 변수만(Primitives 직접 사용 금지), 글꼴 4종, 여백 · 모서리 · 외곽선 값, 손그림 외곽선 프리셋, 기본 · 전용 컴포넌트. 팀 문서 D-01 · D-02 · D-03 · D-05 반영(Q-32, DOCS-TEAM-PR) 전에는 코드에 넣지 않는다 | 2026-10-09 | 사용자 (팀 확정 전달) |
| LOGIN-3 | HOME-01 에 네이버 · 구글 버튼을 넣고 세션 포트 `startLogin` 이 제공자(KAKAO · NAVER · GOOGLE)를 받게 한다(A-01 3종 확정, FE 개발 범위는 사용자 결정). 가짜 구현은 세 제공자 모두 로그인. 진짜 연결의 네이버 · 구글 경로는 API_SPEC 에 없어 지어내지 않고 BE-B 대기(진짜 모드에서는 던짐). 계정 연결(I-07)은 미정 | 2026-10-09 | 사용자 |
| TODAY-HOLD | 오늘의 운세(TODAY-01 · 02)는 넘긴다 — API_SPEC 7장 `POST /daily-fortunes` 초안(인증 불필요 · 요청에 생년정보 · 운세 지수 없음)이 X-01 · X-02(로그인 + 저장된 본인 · 운세 지수)와 반대라 가짜 포트 모양을 정할 수 없다. BE-B 계약(Q-31) 후 재개 | 2026-10-08 | 사용자 |
| MATCH-SELF | 궁합 사람 선택(MATCH-01 · 02)의 첫 칸은 본인 고정, 둘째 칸만 고른다 — 와이어는 두 칸 모두 빈 칸이지만 Q-26 팀 답(v0.3 "궁합은 본인 + 상대")을 따른다 (FIGMA-FINAL). 와이어와의 차이는 Q-23 l | 2026-10-08 | FE |
| CLIENT-REDIRECT | 클라이언트 화면에서 대상이 잘못됐을 때(옛 링크 · 지운 인물 · 잘못된 두 사람)는 `notFound()` 를 부르지 않고 앞 화면으로 `router.replace` 1회 (ref 가드). 근거: Next 16 문서 — `notFound()` 는 서버 컴포넌트 · 서버 함수 · 라우트 핸들러에서만. 정상 사용에서도 생기는 경우라 `error.tsx` 로 던지지 않는다. 쿼리 자체가 없거나 모양이 틀리면 서버 page.tsx 의 `notFound()` | 2026-10-08 | FE |
| SCREEN-CHECK | 웹 대화가 원격 `boyeon` 을 작업 공간에 받아 가짜 모드로 띄우고(`pnpm dev -p 3100`) Playwright 402×874 로 찍어 와이어 좌표와 대조한다. 커밋 전 검토 때는 Claude Code 보고의 diff 를 작업 공간에 적용해 같은 확인 + "조건을 빼면 실패해야 하는 테스트" 를 직접 돌린다. 사용자에게 스크린샷을 요구하지 않는다 (NO-SCREENSHOT) | 2026-10-08 | 사용자 |
| MOCK-PANEL | 가짜 모드(`NEXT_PUBLIC_API_MODE=mock`)에서만 화면 왼쪽 아래 "가짜" 버튼으로 세션 · 충전 결과 · 운세 구매 결과 · 시작 잔액(0 · 7 · 100)을 고르고 바로 가기로 이동한다. 우선순위 저장값 → 환경 변수 → 기본값. 저장은 localStorage `mockOverrides` — 가짜 모드 개발 도구라 브라우저 저장소 규칙의 예외, 진짜 모드 · 운영에서는 읽지도 그리지도 않는다. 목적: Vercel 미리보기 하나로 팀원이 모든 흐름을 확인 (`aaa196e`) | 2026-10-08 | 사용자 |
| LAYOUT-FIGMA | PG-FIRST 를 일부 푼다 — 최종 와이어(`195:91`)의 글자 크기 · 굵기 · 위치 · 간격과 상자(크기 · 테두리 · 모서리 · 와이어 회색)를 화면에 넣는다. 값은 웹 대화가 Figma 에서 읽어 지시문에 숫자로 넘긴다(FIGMA-WEB-ONLY). 계속 넣지 않는 것: 글꼴(FONT-HOLD), 손그림 프레임, 캐릭터 · 아이콘 · 이미지 에셋(자리만 둔다), 색 토큰 확정(와이어 회색은 `TODO(PD 토큰 v0)`), 와이어 문구(지금 자리표시 유지). 공통: 헤더 64px · 제목 30px, Card 와이어 카드, Button `cta` 327×69, AppShell `header` 옵션 | 2026-10-08 | 사용자 |
| CODE-BY-CC 예외 기록 | 10/6 새벽 사용자의 Claude Code 세션이 웹 대화 검토 없이 8커밋을 `boyeon` 에 push 하고 draft PR #13 을 만들었다(커밋 메시지에 지정하지 않은 줄 포함). 10/7 사후 검토로 유지. 10/8 은 속도를 위해 CSAT-01 · 배치 1차를 커밋 후 원격 검토로 처리했다 — 둘 다 문제 없음. 기본은 계속 커밋 전 검토 (형식 B → C) | 2026-10-08 | FE |
| CODE-BY-CC | 코드는 Claude Code 가 작성한다. 웹 대화는 구현 명세(만들 · 바꿀 파일, 규칙 · 근거 ID, 동작, 반드시 넣을 테스트 경우, 하지 말 것)를 쓰고 diff · 새 파일 전문을 검토한다. 코드 단계는 형식 B(구현 · 검사 · diff 보고, 커밋 없음) → 검토 → 형식 C(커밋) 또는 B 재지시. 웹 대화는 코드 파일을 만들거나 zip 으로 넘기지 않는다 (정확한 값 — 픽스처 표 · 상수 · 오류 code — 은 명세에 적는다). FILE-HANDOFF 는 문서 · 외부 파일로 좁힌다 | 2026-10-04 | 사용자 |
| MOCK-PORT | OpenAPI 전에는 화면이 FE 포트(`src/lib/ports/`)만 부르고, 구현은 가짜(`src/mocks/`, 메모리 픽스처 — 개발 서버 · 미리보기 전용, 운영 배포에서 켜지면 오류)와 진짜(`src/lib/api/adapters/`, OpenAPI 생성 타입 → 포트 모델)로 나눈다. 생성 타입은 adapters 에서만. 공통 응답 · 오류 껍데기는 COMMON + BE 골격 `a605afa` 기준 런타임 검사. 가짜도 금액은 픽스처 주석 · 화면 계산 금지 그대로, 선물 · Q-25 · 네이버 · 구글은 범위 밖. 새 라이브러리 없음(MSW 미사용). 세부는 FRONTEND.md 1-2 | 2026-10-04 | 사용자 (선택지 A 목업 없음 · B MSW · C 포트 중 C) |
| CHECKOUT-POPUP | 등껍질 차감 확인은 별도 Checkout 경로 없이 앞 화면 안 Modal 로 한다 — 일반 운세는 질문 화면(옵션 버튼 → 팝업), 수능운은 정보 확인 화면, 선물은 위저드, 부적 추가는 결과 화면. `/fortune/[type]/checkout` · `/suneung/checkout` 삭제(`/gift/checkout` 은 v0.3 이 삭제). 잔액 부족 → 충전 → 앞 화면 복귀 후 팝업 다시 열기(PURCHASE-RESTORE). 팝업 표시: 상품명 · 대상 인물 · 옵션 · 보유 · 사용 · 구매 후 잔액(서버 견적) + 고지(F-08) | 2026-10-04 | 사용자 (FUNCTIONAL_SPEC v0.3 이 FE 판단에 맡김) |
| FIGMA-FINAL | 최종 와이어프레임 = Figma 페이지 `와이어프레임 최종`(node `195:91`). 화면 ID 는 프레임 이름(HOME-01 · FORT-06 · CSAT-04 · GIFT-03 · RECV-T-04 등)을 쓴다. 옛 페이지 `17:2` 와 구 번호 #1 ~ #40 은 기록용(PHASES 1장 "구 #" 열). 와이어와 팀 문서가 다르면 팀 문서를 따르고 Q 로 올린다(Q-23 ~ Q-27). 와이어 문구 · 수량은 자리표시 | 2026-10-04 | 사용자 (PD 최종본) |
| DOCS-TEAM-PR | 팀 문서(`docs/`) 반영은 FE 가 고쳐 `boyeon` → `main` PR 로 올리고 PD 가 검토한다 (PD 답변 8번). FE 범위: 기획 결정과 BE-A 제안 중 PD 가 동의한 결정. `API_SPEC` · `COMMON` · `ERD` · `BACKEND_ROLE_SPLIT` 의 계약 변경은 BE. 첫 반영 `cc60ab3` (PENDING_DECISIONS · FUNCTIONAL_SPEC · PRD). 병합 전에는 그 결정에 기대는 코드를 만들지 않는다 (TEAM-QUESTIONS 상태 `반영 PR 대기`) | 2026-10-03 | 팀 (PD 요청, 사용자) |
| P-03 · P-03A 변경 | PD 가격: 일반 `사주` 10 · `사주+부적` 15 · 부적 추가 10, 수능운 15(부적 포함 단일, 정가 20 할인 표기), 선물(수능운) `부적` 10 · `부적+사주` 15 — 단위 등껍질 N개. 보너스 유효기간 1년(P-04). 화면에는 서버 값만 (Q-22). 팀 문서 `cc60ab3` | 2026-10-03 | PD |
| TEAM-1003 | 팀 결정(사용자 전달): 선물도 **등껍질** 결제(원화는 충전만, P-07 변경), 1차 선물은 **수능운만**(핵심 전제 변경), 부적은 **고르지 않고 결과 기반 자동생성**(T-01 변경), 단위 표기 **"등껍질 N개"**(P-03), 신살은 일반 5종에 포함(잠정, F-03 미정). 팀 문서 반영 전에는 이 결정에 기대는 코드를 만들지 않는다 (TQ-ANSWERS) | 2026-10-03 | 팀 |
| CONFIRM-KEY | 충전 승인(`POST /top-up-orders/{orderId}/confirm`)의 `Idempotency-Key` 는 서버 주문 ID(UUID)를 그대로 쓴다 — PG 복귀 페이지 새로고침에도 저장소 없이 같은 키. 그 밖의 멱등 명령은 구매 의도당 무작위 UUID 하나 + 본문 직렬화 고정 (`src/lib/api/idempotency.ts`, `353d9b8`). BE-A 확인 요청 (Q-17) | 2026-10-03 | FE |
| FILE-HANDOFF | 지시문으로 넘기는 파일은 사용자 PC 의 `~/Downloads`(`$HOME/Downloads`)에 받아 두고 SHA-256 으로 대조한다. 압축 해제 임시 폴더는 고정 경로 `$HOME/Downloads/stepN-unzip`, 변수에 담은 경로를 `rm` 하지 않는다. **10/4 CODE-BY-CC 로 범위를 문서(DOCS-PLACE) · 외부에서 받은 파일로 좁힌다 — 코드 파일은 넘기지 않는다** | 2026-10-03 | 사용자 |
| TQ-ANSWERS | 팀 답 1차 반영 (PD 답변서 · BE 답변서 · BE-A 결정 기록 1~42). `TEAM-QUESTIONS.md` 상태를 `해결` · `답변 · 반영 대기` · `답 엇갈림` · `부분` · `대기` 로 나눈다. `반영 대기` · `엇갈림` 항목은 팀 문서(PENDING_DECISIONS · FUNCTIONAL_SPEC · API_SPEC)에 들어가기 전까지 그 답에 기대는 코드를 만들지 않는다. 답변 원문은 저장소에 두지 않고 요지만 적는다 | 2026-10-03 | FE |
| TOPUP-DONE | 충전 완료는 주문 `CREDITED` 일 때만 표시. `PAID` · `processing: true` 는 처리 중. 승인 결과가 불명확하면 주문 조회 2초 간격 최대 30초 → 확인 중 안내 + 주문 확인 버튼, 새 결제 · 새 멱등 키로 유도하지 않음 (BE-A 결정 17 · 39 · 42 채택, 계약 반영은 Q-17) | 2026-10-03 | FE |
| PURCHASE-RESTORE | 잔액 부족 → 충전 → 복귀 때 구매 선택은 `sessionStorage` 에 둔다 — 인물 ID · 최소 선택값만(생년정보 원문 금지), 마지막 변경 후 24시간, 읽을 때 만료 검증, 구매 성공 · 로그아웃 시 삭제, 저장한 가격 · 잔액은 표시 근거가 아님(`GET /quotes/{quoteId}` 재확인). 전역 상태가 아니며 Zustand 를 쓰지 않는다. FE 브라우저 저장소 규칙 확장 (Q-07, BE-A 결정 1 · 15 채택) | 2026-10-03 | FE |
| NO-SCREENSHOT | 실기기 확인 결과는 사용자가 말로 알려 준 것으로 받는다. FE 문서의 "스크린샷 첨부" 요구를 뺀다 (PR 템플릿 문구는 팀 공용이라 그대로) | 2026-10-03 | 사용자 |
| DOCS-GITHUB | 팀 문서의 원본은 GitHub 저장소 — 루트 `docs/`(PENDING_DECISIONS · PRD · FUNCTIONAL_SPEC · API_SPEC · COMMON_RESPONSE_AND_ERROR_CODES · ERD)와 `backend/docs/`. 결정 원본은 `docs/PENDING_DECISIONS.md`. Notion 스냅샷 방식(TEAM-DOCS)은 폐기하고, 10/1 스냅샷 묶음(`docs-20261001.zip`)은 배치하지 않는다 | 2026-10-03 | FE (사용자) |
| P-02 변경 | 충전 상품 6종으로 변경 — 1,000원 10개(보너스 없음) · 3,000원 33개(30+3) · 5,000원 56개(50+6) · 10,000원 114개(100+14) · 30,000원 346개(300+46) · 50,000원 582개(500+82). 팀 문서 PENDING_DECISIONS · PRD · FUNCTIONAL_SPEC · API_SPEC 반영. → 10/4 기능 명세서 v0.3 에서 50,000원은 보너스 80 · 총 580 (Q-22). `API_SPEC` 6장은 아직 82 · 582 — BE-A 수정 요청 | 2026-10-03 | 사용자 |
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

- 2026-10-10 (오전) · 58 ~ 62단계: BE-B 답변서 2종 · BE-A PR #17 · FE 전달 · 사업자등록증 대조 → TEAM-QUESTIONS(`1a9d877`, Q-37 · Q-38 신설) · 팀 회신 문서(`뿌기사주-FE-회신-20261010.md`). `main` PR #17 병합(`f207fd9`), 사업자 값 4개(`5bee76c`), 구매 결과 처리 Q-38(`8feb588`), OpenAPI 생성 타입(`2c9a17b`), 결정 ADAPTER-HTTP · 오행분석 adapter(`e6a603a`) · 충전 adapter(`c46bfa7`). 매 단계 작업 공간 재현 · 변이 확인. 테스트 67 파일 · 558 개
- 2026-10-09 (오후) · 39 ~ 56단계: HOME-01 로그인 제공자 · 화면은 카카오만(`4717e77`, LOGIN-3 개정), PAY-01 수정안(`b5f7f3a`), PAY-03 ~ 05(`5f61f94`), INFO-01(`5e37d52`), `main` PR #15 병합(`8340913`), 정책 초안 `docs/policies/`(`8fffac0`, 웹 대화 직접 push), COMMON-01(`78aa7ad`), INFO-02(`aa2296e`), 오행분석 포트 · 가짜(`13eb54d`) · 화면(`5c38cb3`). BE-B PR #15 검토 → 팀 질문 문서(Q-33 ~ Q-36) · BE-B 연동 TODO(PHASES). 테스트 62 파일 · 499 개
- 2026-10-09 (오전) · 팀 답 요청 문서(`뿌기사주-팀-답-요청-20261009.md`, 사용자에게 전달). 로그인 네이버 · 구글 버튼 39단계 지시(LOGIN-3). 최종 와이어 PD 추가 화면 7개 · 디자인 시스템 확정본(Figma `iFtLxChBQf3nQWaltdrJ37`) 조회 → PHASES v2.9 · Q-25 부분 · Q-15 부분 · Q-32. 다음 세션 지침 교체본 작성.
- 2026-10-08 (밤) ~ 10-09 (아침) · HOME-03 · 04 커밋 · 메뉴 줄 높이(`8c16730` · `ce4bf89`), FORT-01(`d7f988b`), FORT-02 · 03(`74a753e`), 가짜 시나리오 `signed_in_with_other`(`45828ff`), MATCH-01 · 02(`1f6150e`), MATCH-03(`ddeb780`), PAY-02 · FORT-04(`7c9b186`), FORT-05 · CSAT-02(`36d4284`) — 매 단계 작업 공간 재현 · 화면 대조. 오늘의 운세는 Q-31 로 넘김(TODAY-HOLD). 결정 MATCH-SELF · CLIENT-REDIRECT · SCREEN-CHECK. TEAM-QUESTIONS Q-31 · Q-23 l.
- 2026-10-08 (저녁) · 가짜 조작판(`aaa196e`, MOCK-PANEL) → Vercel Preview 가짜 모드 설정 · 팀 공유 가능. 화면 현황 49개 대조, PD 화면 제작 요청서 작성. HOME-03 · HOME-04 구현(24단계, 커밋 대기). PR #13 `main` 병합(`c4c2088`).
- 2026-10-08 (오후) · 와이어 배치 계속(`1504fe1` · `9234cc3` · `573d578`), MY-01(`8141672` · `71296ad`). 웹 대화가 원격 코드를 직접 띄워 화면 대조 시작 — 로그아웃 후 `/login` 으로 가던 문제를 화면에서 찾아 고침.
- 2026-10-07 ~ 10-08 · `main` BE-A PR #14 병합(`a47e428`) · FE_COMPATIBILITY 대조, 10/6 커밋 8개 사후 검토(유지). 잔액 부족 추천 충전 · 가짜 추천 BE-A 규칙 · 판매 상품 없음(`6526d3c`), `/about` 틀(`7aba2e1`), CSAT-01(`80a9a0b`), 와이어 배치 1차(`84cb468` · `bfb7de1`, LAYOUT-FIGMA). PR #13 본문 갱신. 팀 요청 자료 공유 문서 작성, TEAM-QUESTIONS Q-28 ~ Q-30.
- 2026-10-04 ~ 10-05 · MOCK-PORT 로 PG-1 · PG-2 · PG-3 가짜 범위와 Phase 3 본인 입력 진행 — API 코어(`ffe1080`), 충전 포트 · 화면(`e0ce4dd` · `03f01fa` · `816b130`), returnTo · 세션 · 로그인 가드 · `/login`(`728e9fb` · `84b5390`), 인물 포트 · PersonForm · `/onboarding`(`75b774c` · `8f94cde`), PersonForm 검증 규칙(`1a7fe39`), 테스트 시간 초과 완화(`ebb16b0`). 결정 MOCK-PORT(`fe549f4`) · CODE-BY-CC. PR #12 병합(`47e2055`). 로컬 충전 시나리오 6종 확인.
- 2026-10-04 · 기능 명세서 v0.3 팀 문서 반영(3차 `9fb9d21`, 병합 `d396cce`) — Q-23 b · c · h · i · j · k 해결(j: 서비스명 뿌기사주), Q-27 해결. 라우트: `/wallet` · `/share/[shareId]` 추가(`078662d`), Checkout 3개 삭제(`b9a95ea` · CHECKOUT-POPUP). 유저 플로우 FigJam v1 조회(새 충돌 없음). FE 문서 v2.5 · 지침 갱신.
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
