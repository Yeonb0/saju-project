# PROGRESS

> **FE 개인 작업 문서다. 팀 합의 문서가 아니다** (DOCS-FE-OWN).
> 모든 세션은 시작할 때 같은 폴더의 `PHASES.md` · `TEAM-QUESTIONS.md` 와 이 파일을 읽는다. 갱신은 웹 대화에서 작성한 완성본으로만 한다 (`frontend/CLAUDE.md` "문서 변경 규칙").
> 갱신할 때는 "현재 상태"를 덮어쓰고, "세션 로그"에는 맨 위에 한 줄씩 추가한다.

## 현재 상태

- **현재 단계**: **PG 심사 트랙 (PG-FIRST)** + **가짜 구현(MOCK-PORT)** + **진짜 adapter(ADAPTER-HTTP)**. 최종 와이어의 글자 · 상자 배치는 넣는다 (LAYOUT-FIGMA) — 글꼴 · 손그림 외곽선 · 색 토큰은 팀 문서 반영(Q-32) · 적용 시작(사용자 결정) 전까지 넣지 않는다. 캐릭터 · 아이콘 · 이미지 에셋은 자리만. **사용자 문구는 기능 명세서 v0.4 (PD 확정)를 쓰고, 그 밖의 와이어 문구는 자리표시**. 코드는 Claude Code, 웹 대화는 명세 · 검토 · 작업 공간 재현 · 변이 확인 (CODE-BY-CC · SCREEN-CHECK)
- **위험 (10/14 오후, `boyeon` `6cee20d` · `main` `84f87f0`)**: FE 진짜 연결 **코드는 일반 운세 5종까지 끝났지만 실제 브라우저에서 확인한 API 는 아직 0개다** — 스테이징이 없다 (R-06).
  - **10/14 카드사 심사 요청은 못 했다.** 스테이징이 나오는 날 실제 확인 → 토스 2단계 → 실기기 → 심사 요청. **10/20 PG 미승인 대안 판단을 앞당겨 검토할 때다**
  - 심사 요청 전에 정해져야 하는 것: 스테이징(R-06) · callback · `FRONTEND_BASE_URL`(R-04), 정책 v3(Q-56), 공급 업체 · 전화번호 · 통신판매업 면제 표기(Q-44 · R-07), 토스 사유 문구(Q-58). 심사 중에는 푸터 · 전화번호 · 상품 · 결제수단 · 판매 상태를 바꿀 수 없다
  - **CSRF Origin 검사 (PR #28)**: 변경 요청은 `Origin` 이 백엔드 `FRONTEND_BASE_URL` 출처와 같아야 통과 — 스테이징 FE 주소와 다르면 모든 변경 요청이 403
  - 로컬 진짜 모드 확인은 하지 않는다(10/12 사용자). 로컬 시드 상품 비활성 · 토스 disabled (Q-39 4)
- **10/14 새로 들어온 것**
  - `main` PR #29 ~ #36 (10/14 새벽): 가입 동의 저장 `GET/POST /me/consents`(BE-B — API_SPEC · COMMON 에 없음, Q-59), 부적 창고 API(BE-B), 선물 주문 · 생성 · 복구(BE-A), **선물을 직접 링크 `/g#token=` 으로 바꿈(BE-B PR #35 · #36 — 팀 문서 · 정책 v2 는 알림톡, Q-54) · FE 파일 5개 직접 수정(`/g/[token]` → `/g`, Q-55)**, 관리자 주문 조회. `boyeon` 에 병합(`1755755`)
  - PD 10/13 1 · 2차 답 · 정책 확정본 v2 3종 · PD 추가 수정 요청 · BE-A 정책 검토(`backend/docs/PD_POLICY_V2_REVISION_REQUEST.md`) · BE-B 답 — 사용자 전달(저장소 밖)
  - FE 가 결정된 것을 **기능 명세서 v0.4** 로 옮겨 `main` 병합(`ad4d928` → PR #38 `84f87f0`, 결정 SPEC-V04). PD 는 GitHub 를 쓰지 않아 검토는 claude.ai 문서 "뿌기사주 팀 확인 요청 (FE, 10/14)" 으로. 선물 · 정책 원문은 v0.4 에서 뺐다(Q-54 · Q-56)
  - 사용자: 통신판매업 신고는 직전연도 기준이라 신규 사업자는 면제로 알고 있음 → 대표 · PD 가 관할 구청 확인 · 푸터 표기 문구 (R-07)
- **마지막으로 끝낸 작업** (10/14, `boyeon` `b33cfb5` ~ `6cee20d`, 문서 1 + 코드 14커밋 — 매 단계 웹 대화가 작업 공간에서 diff 재현 · 검사 · 변이 확인 · 가짜 모드 화면 대조 후 커밋)
  - 전자우편 `ppugisaju@gmail.com` (`b33cfb5`), 기능 명세서 v0.4 (`ad4d928`), `main` `572a763` 병합 (`1755755`)
  - 수능 카드 제목 · 교시 · 준비물 묶음 (`608a5f5`), COMMON-02 ~ 04 · 생성 실패 · 문의하기 메일 (`4e6cab1`), PAY-01 필수 동의 · 안내 상자 · PAY-06 (`9be6276`), PAY-03 ~ 05 문구 · 취소 / 실패 구분 · `RETRY_PAYMENT` 일 때만 다시 결제 (`d777e5a`), 다시 결제 시 상품 유지 `failUrl ?product` (`df940fb`, TOPUP-RETRY-URL), 로그인 · 푸터 문구 (`94f697f`)
  - 일반 운세 질문 칩 · 구매 선택 `questionKey` · `relationType` · 복원 v4 · 가짜 서버 규칙 (`536b810`), 진짜 adapter 일반 운세 5종 견적 · 구매 (`2f505f7`, SUNEUNG-ADAPTER-FIRST 해제), 수능 결과 8장 카드 넘겨 보기 (`f8148ea`), 구매 완료 안내 「준비가 끝났어요!」 (`b3d2051`), 인물 `relationType` · 궁합 「연인」 미리 고르기 (`6cee20d`)
  - 테스트 78 파일 · 1010 개
- **진짜 adapter 현황 — 전부 `ports/index.ts` 에 연결**: 세션 · 인물(`78952fe`, `relationType` `6cee20d`) · 오행분석 · 지갑 · 충전(주문 생성 · 상품 목록 · 승인 · 주문 조회) · 환불 조회(화면 미연결) · 토스 결제창 · 수능운 견적 · 재확인 · 구매 · **일반 운세 5종 견적 · 구매(`2f505f7`)** · 운세 상품 목록 · 결과(`GET /readings/{readingId}` — API_SPEC 8장 호환 경로, 일반 운세도 이 경로로 받는다) · 구매 상태 조회. 만들지 않은 것: 오늘의 운세(Q-46 부가 항목 · Q-31), 가입 동의(Q-59), 충전 · 사용 내역(Q-57 · Q-60), 운세별 결과 상세 경로, 선물(Q-54 · Q-55), 부적
- **화면 현황 (10/14, 최종 와이어 + 수정안 기준)**
  - 동작 + 배치: HOME-01 ~ 04, FORT-01 ~ 05, MATCH-01 ~ 03(질문 칩), CSAT-01 ~ 06(8장 카드), PAY-01 ~ 06, MY-01, INFO-01 · 02, COMMON-01 ~ 04, (팝업) 차감 확인 · 잔액 부족 · 구매 완료 — 가짜 모드에서 확인. 진짜 모드는 코드 연결만(스테이징 대기)
  - 부분: FORT-06 · 07 일반 결과 — 섹션 소제목 없음(Q-62), 요약 Q-05 · 공유 G-11 · 부적. FORT-10 생성 실패 — 명세 문구로(와이어 차이 Q-61)
  - 보류: TODAY-01 · 02 (Q-46 · Q-31), CSAT-08 판매 종료 (Q-63 마감일 출처 · Q-57), MY-03 · 04 · 05 · 07 (Q-57 경로 · Q-60 · Q-52), HOME-02 동의 (Q-59), GIFT · RECV (Q-54 · Q-55)
  - 규칙으로 막음: GIFT-01 ~ 05 · RECV-* · FORT-08 · 09 · CSAT-07 · TALBOX — 선물 · 부적은 팀 문서 확정 전 가짜 포트로도 만들지 않는다
- **다음 작업** (막힌 것은 건너뛴다 — 지금 FE 가 답 없이 할 코드 작업은 거의 없다)
  1. (BE → FE) 스테이징 + 카카오 callback 프론트 주소 + `FRONTEND_BASE_URL` + 스테이징 상품 활성 → 진짜 모드 실제 확인: 로그인 → 본인 등록 → 지갑 → 충전(토스 2단계) → 수능운 · 일반 운세 견적 · 구매 → 완료 안내 → 결과(8장 카드 · 준비물 체크) → 새로고침 복귀 → 로그아웃(204). 그다음 운영 도메인 실기기 (PG-5)
  2. (사용자) 토스 `failUrl` 쿼리 확인 — 갤럭시에서 카드 · 간편결제 앱 취소 → 주소가 `/pay/fail?product=…&code=…`(`?` 두 번 없음)이고 「다시 결제하기」 가 상품을 유지하는지 (`df940fb` — 토스 문서에 기존 쿼리 처리가 없어 실제 확인 필요)
  3. (FE, 답 오면) Q-62 결과 소제목 → `labels.ts`, Q-61 FORT-10 문구, Q-57 경로 → MY-04 · MY-07 · CSAT-08 버튼, Q-59 → HOME-02 동의, Q-60 → MY-03, Q-63 → CSAT-08, Q-54 · Q-55 → 선물, Q-56 · Q-44 · R-07 → 정책 페이지 · 푸터, Q-46 → TODAY
  4. (FE, 선택) 일반 운세 결과를 운세별 상세 경로(`GET /readings/{category}/{readingId}`)로 받을지 — 지금 호환 경로가 동작하므로 BE 가 호환 경로를 닫는다고 할 때. 테스트 `asyncUtilTimeout` 전역 판단
- **`main` 배포 주의**: `main` 배포는 진짜 모드다(가짜 금지). 스테이징 연결 전에는 API 화면이 오류. 팀 확인은 `boyeon` 미리보기 주소(가짜 모드)로만. 진짜 로그인은 고정 스테이징 · 운영 도메인에서만 (R-04). `boyeon` → `main` PR 은 사용자가 올린다 (지금 `boyeon` 이 `main` 보다 14커밋 앞)
- **보류 (재개 조건)**
  - 디자인 — 글꼴(FONT-HOLD), 손그림 외곽선(Q-32), 캐릭터(D-06 · T-01 · R-02 — 자리만), 아이콘 에셋, 색 토큰(Q-32): 팀 문서 반영 · 적용 시작 결정 후
  - 네이버 · 구글 로그인 버튼 — PG 심사 후 (LOGIN-3 개정 · Q-43)
  - 오늘의 운세 TODAY-01 · 02 — 한줄 `oneLineFortune` · 지수 5개 이름은 결정, 부가 항목 회의 후 (Q-46)
  - 일반 결과 소제목 (Q-62), 요약 Q-05 · 공유 G-11 · 부적 규칙, FORT-10 문구 (Q-61)
  - 구매 실패 `failureCode` 화면 사용 (`TODO(Q-38 c)`), 견적 `regularPrice` 표시 (Q-22), 버튼 둘째 줄 가격 (P-09 · Q-22)
  - 토스 사유 문구 (Q-58), 충전 `nextAction` CONTACT_SUPPORT 즉시 안내 문구 — PD
  - MY-03 · 04 · 05 · 07 · CSAT-08 — 경로 Q-57, 내역 API Q-60, 마감일 Q-63
  - HOME-02 가입 동의 4개 — Q-59 (API 는 공유 OpenAPI 에 있지만 API_SPEC · COMMON 에 없다)
  - 정책 원고 · 시행일 — Q-56 v3 · Q-44 · R-07
  - 신살 1차 판매 여부 — PD 10/20 (기능 명세서 v0.4 8장)
  - 타인 정보 입력(MY-02 · 궁합 상대 새로 입력) — 권한 확인 문구 PD · 본인 수정 경로 Q-29. 직접 입력 관계 이름은 서버에 보내지 않는다 (`TODO(A-07 · BE-B)`)
  - 결과 고지 문구 — 질문 화면 CTA 위 문장은 넣었다(`536b810`). 결과 하단 고지 코드별 문구 · 시간 미상 고지는 PD (F-08)
  - 선물 위저드 · 선물 결제 · 수신 · 부적 화면 — Q-54 · Q-55 · Q-20 후, 가짜 포트로도 만들지 않는다
  - iOS Safari 실기기 — iPhone 확보 후 (R-03). Sentry 소스맵 — Phase 7
- **막힌 점 · 전달할 것**
  - 팀 확인 요청: claude.ai 문서 **"뿌기사주 팀 확인 요청 (FE, 10/14)"** — Q-54 ~ Q-63 · Q-44 · R-07 (Q-61 · Q-62 · Q-63 은 10/14 오후 추가). 사용자가 PD(Q-54 · Q-56 · Q-57 · Q-58 · Q-61 · Q-62) · BE-B(Q-55 · Q-59) · BE-A(Q-44 · Q-60 · Q-63) · 대표(통신판매업 면제 확인 · 전화번호)에 전달
  - **PD 에 알릴 것**: 기능 명세서 v0.4 는 PD 검토 전에 `main` 에 들어갔다(PR #38) — 고칠 곳은 확인 요청 문서로 받는다. 10/14 오후 로그인 안내 위치를 "버튼 아래" → "서비스명 아래 · 버튼 위"(와이어 359:156 그대로)로 고쳤다(문서 정리 커밋)
  - BE 에 받을 것: 스테이징 · callback · `FRONTEND_BASE_URL` · 상품 활성 (R-04 · R-06), Q-38 f 공동 확인
  - **BE-B 에 전할 것**: PR #35 가 다시 FE 파일을 직접 고쳤다(Q-55) — FE 파일 변경은 FE 리뷰어로
  - **비밀값 교체 (사용자 → BE)**: 10/12 웹 대화에 `backend/.env` 가 붙여 넣어졌다 — Liner API 키 · 카카오 클라이언트 시크릿 교체 권고. 값은 어떤 문서에도 옮기지 않는다
  - 가짜 구현 환경 변수 (사용자가 `frontend/.env.local` 에 직접, 바꾸면 `pnpm dev` 재시작): `NEXT_PUBLIC_API_MODE=mock`, `NEXT_PUBLIC_MOCK_SESSION_SCENARIO`(signed_out · new_user · signed_in · signed_in_without_person · signed_in_with_other, 비우면 signed_out), `NEXT_PUBLIC_MOCK_TOP_UP_SCENARIO`(credited · paid_then_credited · stuck_paid · confirm_lost · processing_409 · rejected, 비우면 credited), `NEXT_PUBLIC_MOCK_FORTUNE_SCENARIO`(fulfilled · generation_failed · processing_409 · quote_expired · generating · review_required · refunded, 비우면 fulfilled), `NEXT_PUBLIC_MOCK_PAYMENT_LAUNCHER`(toss 또는 비움), `NEXT_PUBLIC_TOSS_CLIENT_KEY`(문서용 테스트 키 — 원문은 문서 · Git · 스크린샷에 넣지 않는다). 미리보기에서는 왼쪽 아래 "가짜" 조작판이 우선 (MOCK-PANEL). 가짜 상태는 탭 메모리
  - 진짜 모드 로컬 연결: `API_PROXY_TARGET=http://localhost:8080` · `NEXT_PUBLIC_API_MODE=real`. `.env.example` 에 `NEXT_PUBLIC_MOCK_*` · 토스 변수 줄은 아직 없다 (사용자 할 일). `.env.local` 의 `NEXT_PUBLIC_MOCK_PAYMENT_LAUNCHER=toss` 되돌리기 (사용자 할 일)
  - 구매 선택 복원 저장값이 **v4** (`536b810`, `productOption` · `questionKey` · `relationType` 추가) — v3 · v2 는 질문이 없어 읽을 때 지운다(24시간 만료라 배포 전 값은 하루 안에 사라짐)
  - Vercel "Automatically expose System Environment Variables" 켜짐(10/4) — 운영 mock 차단 · Sentry 키 검사가 기댄다. Production 의 `NEXT_PUBLIC_TOSS_CLIENT_KEY` 는 진짜 연결 때
  - `pnpm test` — 사용자 PC 는 `--maxWorkers=2` 로 90 ~ 210초. 작업 공간은 기본 설정으로 약 100초
  - 명세 정확도 (웹 대화 자체 점검): "이 조건을 빼면 이 테스트가 실패해야 한다" 요구가 10/8 ~ 10/14 에 일곱 번 성립하지 않았다. 10/14: 견적 queryKey 의 질문 키는 팝업이 다시 받는 동안 옛 견적을 쓰지 않아(`isFetching` 이면 표시 안 함) 테스트로 잡히지 않는 방어 코드. 그 밖 기록: `satisfies` 는 객체 리터럴에만 이름 대조, `git apply --include` 의 `[type]` 은 `\[type\]`, 같은 쿼리 키를 구독하는 화면은 무효화 직후 다시 받는다, 신선한 캐시를 심은 테스트는 `enabled` 변이를 못 잡는다. 10/14 Claude Code 가 명세 밖 간격 값을 바꾸고 보고하지 않은 적이 1번(18단계 — 받아들임)
  - 사업자등록증 원본은 저장소에 두지 않는다. 푸터에는 사용자가 준 값만
  - 유저 플로우: FigJam `dd8IamO1coU9vpa4P7AgMi`. 와이어 배치 값은 웹 대화가 Figma 에서 읽어 지시문에 숫자로 넘긴다
  - (PD 전달) 개인정보처리방침 국외 이전 고지 — PostHog(US) · Sentry(지역 확인 필요), 카카오톡 인앱의 떠 있는 버튼이 화면 오른쪽 가운데를 가린다
  - `app/error.tsx` 는 실기기에서 띄워 보지 못했다 — 첫 실제 API 연결 화면에서 확인
  - 테스트 출력의 jsdom 경고 "Not implemented: navigation to another Document" 는 `AppShell.test.tsx` 가 낸다 (통과에 영향 없음)
  - Claude Code 는 `frontend/` 에서 시작한다. Playwright 브라우저 미설치 — E2E 처음 돌리기 전에 `pnpm -C frontend e2e:install`
  - 웹 대화가 Claude Code 에 시키는 보고 파일(diff 등)은 사용자 PC `~/Downloads` 에 저장한다

## 마감 체크

| 날짜 | 마감 | 상태 |
|---|---|---|
| 9/29 (화) | 구 D-01 · D-02 · D-12 · D-14 확정 | 닫힘 — 팀 결정 문서로 대체 (DECISION-IDS) |
| 10/4 (일) | BE 스테이징 · 공통 응답 · 로그인 골격 (BE 일정) · Phase 1 FE 골격 | ⬜ FE 골격 완료(디자인 보류). BE 는 골격만(`a605afa`), 스테이징 · 로그인 미충족 — 재산정 중 (R-06) |
| 10/10 (토) | BE 충전 · 토스 테스트 결제 · 디자인 에셋 1차 (D-03) | ⬜ BE-A 충전 주문 생성 · 지갑 API 는 `main`(PR #17), 승인 · 지급 · 스테이징 없음 (R-06). FE 는 생성 타입 · adapter 2개 |
| 10/12 (월) | FE 심사용 사이트 (PG-1 ~ PG-5) | ⬜ 놓침 — FE 진짜 연결 코드는 끝(`afcd0b3`, 모든 포트). BE 기능도 `main` 에 있음. 스테이징이 없어 실제 확인 0 (R-06) |
| 10/14 (수) 전후 | 카드사 심사 요청 | ⬜ 못 함 — 스테이징 없음(R-06), 정책 v3(Q-56) · 공급 업체 · 전화번호 · 통신판매업 표기(Q-44 · R-07) 대기. 스테이징 나오는 날 실제 확인 · 토스 2단계 · 실기기 · 심사 요청. 10/20 대안 판단 앞당겨 검토 |
| 10/15 (목) | 디자인 에셋 확정 · 콘텐츠 1차 (D-03 · D-04) | ⬜ Q-40 은 10/14 해결(기능 명세서 v0.4). 이날 답 기한: Q-54 · Q-57 · Q-59 · Q-46 |
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
| SPEC-V04 | PD 10/13 1 · 2차 답 · 정책 v2 · 와이어 수정안 중 결정된 것을 FE 가 기능 명세서 v0.4 로 옮겨 `main` 에 넣고(`ad4d928` → PR #38 `84f87f0`), 그 문서를 근거로 코드에 반영한다. PD 는 GitHub 를 쓰지 않아 검토 · 수정 요청은 claude.ai 확인 요청 문서로 받는다. 선물 · 정책 원문 · 미정 항목은 v0.4 에서 빼고 ⚠️ · Q 로 남긴다 (DOCS-TEAM-PR) | 2026-10-14 | 사용자 ("일단 결정된 것 기준으로 바꾸기") |
| TOPUP-RETRY-URL | PAY-05 「다시 결제하기」 의 상품 유지는 토스 `failUrl` 에 상품 코드(`?product=`)를 붙이고 `/pay/fail` 이 형식 검사(`^[A-Z0-9_]{1,64}$`) 뒤 `/wallet?product=` 로 넘긴다. 기기 저장 없음, 성공 주소는 그대로. `/wallet` 은 활성 상품일 때만 미리 고른다 (`df940fb`) | 2026-10-14 | 사용자 (추천 채택) |
| SUNEUNG-ADAPTER-FIRST 해제 | Q-26 이 기능 명세서 v0.4 로 해결돼 일반 운세 5종 견적 · 구매 진짜 adapter 를 운세별 경로로 만든다. 요청 전 선택 검사(수능운은 질문 값 없음 · 일반은 옵션 · 허용 질문 키 · 궁합은 상대 · 관계), 견적 응답의 옵션 · 질문이 보낸 값과 다르면 계약 위반 (`2f505f7`) | 2026-10-14 | 웹 대화 (Q-26 해결에 따른 후속) |
| REFUND-CS | 충전 환불은 고객센터 접수 → 운영자 관리자 API 실행(BE-A PR #27). FE 는 환불 신청 버튼 · 신청 adapter 를 만들지 않고, BE-B 가 PR #28 에서 넣은 `requestRefund` · `KeyOnlyIdempotentCommand` · `refund_not_eligible` 을 지운다. 소유자 환불 조회(`getRefund`)는 BE-A 요청대로 유지 — 가짜는 `refunds` 픽스처로만 기록을 갖고 없으면 404 `RESOURCE_NOT_FOUND`(서버와 같음). `PARTIALLY_REFUNDED` · `REFUND_PENDING` 은 알려진 주문 상태지만 완료가 아니다. 화면(충전 내역 · 주문 상세)은 PD 배치 후(Q-41 6) (`78c8b10`) | 2026-10-13 | 사용자 (BE-A 전달 문서 · 추천 순서 채택) |
| OPENAPI-FE-SYNC | BE 가 FE 에 넘긴 경우(BE-A `CUSTOMER_CENTER_REFUNDS_20261013.md` — "공유 OpenAPI 와 생성 타입은 FE 가 같은 PR 에서") 공유 `docs/openapi/api-v1.json` 은 FE 가 `backend/` 에서 `./gradlew --no-daemon exportOpenApi`(H2 테스트 DB)로 만들고 `pnpm api:types` 와 같은 커밋에 넣는다. 파일을 손으로 고치거나 백엔드 전용 스냅숏을 복사하지 않는다 — export 결과를 스냅숏과 대조만 한다. 실패하면 우회하지 않고 멈춘다 (`78c8b10`) | 2026-10-13 | 사용자 (BE-A 위임) |
| PURCHASE-STATUS-UI | 구매 팝업은 서버 구매 상태로 분기한다 — 화면 분기는 `status`, 버튼 · 안내는 `nextAction`(API_SPEC 8장 상태표). 완료 = `FULFILLED` + `readingId`. `WAIT`(처리 중 · 보상 진행 중)은 처리 중 안내 + "결과 확인" 버튼, `RETRY_PURCHASE` 는 다시 시도, 그 밖의 실패는 문의 안내(새 구매를 열지 않음). 환급 문장은 `REFUNDED` + `COMPLETED` 일 때만. **처리 중 자동 반복 조회는 하지 않는다** — 결과 불명 · 처리 중 · 생성 실패 오류 뒤 상태 조회를 한 번만 자동으로 하고, 그 뒤는 사용자가 "결과 확인" 을 누를 때. 기록이 없으면(404) 자동 재전송하지 않고, 사용자가 누를 때만 같은 키 · 같은 견적으로 다시 보낸다(I-05). 처리 중에는 팝업을 닫지 못한다(Q-40 1 대기) (`9addcee`) | 2026-10-12 | 사용자 (추천 채택 — 자동 반복 조회 없음 · 새로고침 복귀까지 지금) |
| RESULT-GUIDE-MODEL | 결과 포트 본문을 서버 구조로 바꾼다 — `SUNEUNG_GUIDE`(서버 `suneungGuide` 8개 항목: 요약 · 시험 당일 · 교시별 · 집중 팁 · 음식 · 준비물 · 마음 관리 · 부족한 오행) 또는 `SECTIONS`(예전 결과 · 일반 운세의 `key` · `content`, 모르는 key 는 UNKNOWN). VIEWER 의 섹션 타입(PERIOD_GUIDANCE · FOOD_RECOMMENDATION · CHECKLIST)과 `birthTimeUnknown` 삭제. 카드 순서는 API_SPEC 필드 순서, 빈 목록 카드는 그리지 않는다. 교시 · 준비물 분류는 아는 코드만 이름, 모르는 분류는 마지막. 이름은 `src/lib/reading/labels.ts` 임시값 `TODO(PD 문구)`(Q-40 2). 준비물 체크 저장 키는 예전과 같은 `PREPARATION`. 같은 준비물 id 가 두 번 오면 계약 위반 (`086ed90` · `a352829`) | 2026-10-12 | 사용자 (선택지 A 서버 구조를 옛 섹션으로 변환 · B 포트를 서버 구조로 중 B, 추천 채택) |
| TOSS-LAUNCHER-SWITCH | 결제창 선택 — 진짜 모드는 토스 결제창. 가짜 모드는 `NEXT_PUBLIC_MOCK_PAYMENT_LAUNCHER` 가 `toss` 일 때만 토스 결제창(1단계 시험용 — 토스에서 돌아오면 가짜 메모리가 비어 `/pay/success` 가 실패 화면, 의도), 비우거나 `fake` 는 가짜 결제창, 다른 값은 던진다 (`ecaaba4`) | 2026-10-11 | 사용자 (추천 채택) |
| TOSS-ANONYMOUS | 토스 V2 결제창형(위젯 `renderPaymentWindow` → `requestPayment` redirect)에 `customerKey: ANONYMOUS` 를 쓴다 — 회원 식별자를 토스에 보내지 않는다. 클라이언트 키는 `NEXT_PUBLIC_TOSS_CLIENT_KEY`(`test_gck_` · `live_gck_` 만), 시크릿 키는 FE 에 두지 않는다. 결제창 닫기는 `PaymentCanceledError` → 충전 화면 오류 안내 없음 (`ecaaba4`) | 2026-10-11 | 사용자 (추천 채택) |
| SUNEUNG-ADAPTER-FIRST | 운세 견적 · 구매 진짜 adapter 는 수능운 경로만 먼저 만든다 — 수능운은 OpenAPI 계약이 완전하다(견적 `{ personId }`, 구매 `{ quoteId, personId }`). 일반 운세 5종은 필수 요청 값 `questionKey` · `relationType` 의 화면 칸 대응(Q-26 보강) · 관계 대응(Q-35 2) · 상품 메타(Q-28) 답 전이라 adapter 가 요청 전에 던진다. 포트 선택값에 `fortuneType`, 견적 `charged` · `productName` nullable, 상품명이 없으면 팝업의 상품 줄을 그리지 않는다 (`1fcd9c0`) | 2026-10-11 | 사용자 (선택지 A 수능운만 · B 일반 5종까지 · C 보류 중 A, 추천 채택) |
| PURCHASE-KEY-STORE | 구매 의도의 `Idempotency-Key` 와 `quoteId` 를 `sessionStorage` 에 둔다 — PURCHASE-RESTORE 와 같은 조건(마지막 변경 후 24시간, 읽을 때 만료 검증, 구매 성공 · 로그아웃 시 삭제, 생년정보 · 가격 · 잔액은 넣지 않음). BE-B 합의안(응답 유실 시 같은 키 · 같은 견적으로 상태 확인)에 대한 원칙 동의. → **10/12 구현** (`afcd0b3`): 구매 선택 복원 v3 의 `purchaseKey`. 구매를 보내기 직전 저장, 완료 · 실패 때 삭제. 돌아오면 견적보다 먼저 그 견적 ID 로 상태 조회 — 완료면 결과로, 실패면 실패 안내, 처리 중이면 견적 없이 처리 중, 기록이 없을 때만 견적을 받아 같은 키로 보낸다 | 2026-10-10 | 사용자 (추천 채택) |
| ~~PURCHASE-STATUS-WAIT~~ | ~~가짜 구현을 BE-B 합의안(구매 상태 조회 · `compensationStatus` · `nextAction`)에 지금 맞추지 않는다. API_SPEC · OpenAPI 반영 후 포트 · 가짜 · adapter 를 한 번에~~ → **10/12 해제**: API_SPEC 8장 · OpenAPI(PR #20 · #23)에 들어와 포트 · 가짜 · adapter 를 한 번에 바꿨다 (`9f1366e`) | 2026-10-10 | 사용자 (추천 채택) |
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
| VIEWER | 결과 화면은 ReadingViewer 하나로 수능 · 일반 5종 · 선물 수신에 재사용한다. SuneungResultViewer 대체. → 10/12 RESULT-GUIDE-MODEL 로 섹션 타입 대신 본문 종류(`SUNEUNG_GUIDE` · `SECTIONS`)별로 그린다 | 2026-10-01 | FE |
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

- 2026-10-14 · 1 ~ 20단계: PR #31 병합 확인 · `boyeon` fast-forward(`e16a91d`), 전자우편(`b33cfb5`). PD 10/13 1 · 2차 답 · 정책 v2 · BE-A 정책 검토 · BE-B 답 대조 → 팀 확인 요청 문서 2종(claude.ai "(FE, 10/13)" · "(FE, 10/14)", Q-42 ~ Q-63), 기능 명세서 v0.4(`ad4d928`, 결정 SPEC-V04), `main` `572a763`(PR #29 ~ #36 — 동의 · 부적 창고 · 선물 직접 링크 · FE 파일 수정) 병합(`1755755`) → PR #38. 코드 14커밋: 수능 이름(`608a5f5`) · 구매 상태 문구 · 문의 메일(`4e6cab1`) · 충전 동의 · PAY-06(`9be6276`) · PAY-03 ~ 05(`d777e5a`) · 다시 결제 상품 유지(`df940fb`, TOPUP-RETRY-URL) · 로그인 · 푸터(`94f697f`) · 일반 운세 질문 칩 · 복원 v4(`536b810`) · 일반 운세 진짜 adapter(`2f505f7`) · 수능 8장 카드(`f8148ea`) · 구매 완료 안내(`b3d2051`) · 「연인」 미리 고르기(`6cee20d`). 매 단계 작업 공간 재현 · 변이 확인 · 화면 대조. 테스트 78 파일 · 1010 개
- 2026-10-13 · 1 ~ 9단계: `main` PR #25 · #27 · #28 대조(BE-B 가 FE 파일 직접 수정 · BE-A 고객센터 환불 전환 · 사용자 환불 POST 제거 · `PARTIALLY_REFUNDED`), BE-A 전달 문서 "FE 전달: 환불 화면 및 연결 작업" 반영. `main` 병합(`c6adbea` — 사용자 PC 테스트가 느려 1회 타이밍 실패, 작업 공간에서 통과 확인), 공유 OpenAPI 서버 export · 환불 신청 제거(`78c8b10`, 결정 REFUND-CS · OPENAPI-FE-SYNC), PAY-03 유료 · 보너스 내역(`4182746`, 와이어 301:166 대조), `WALLET_REVIEW_REQUIRED` 문의 안내(`3f62019`). 매 단계 작업 공간 재현 · 변이 확인. Q-39 해결 · Q-41 부분(6 신설) · Q-21 정책 변경. 테스트 74 파일 · 852 개
- 2026-10-12 (저녁) · 1 ~ 2단계: PR #26 병합(`30780c8`) 확인, `boyeon` 을 `main` 으로 fast-forward · push(`5edf304..30780c8`, FE · 공유 OpenAPI 차이 없음, stash@{0} 그대로). PR #24 · 미병합 `codex/admin-financial-recovery` 의 FE 연결 점검 → 연결할 것 없음, `WALLET_REVIEW_REQUIRED` 범위를 Q-41 5 로(이 문서 갱신). 코드 변경 없음
- 2026-10-12 · 1 ~ 16단계: `main` PR #23(BE-B 카카오 OAuth · 세션 · CSRF · 인물 · OpenAPI 재생성) 확인, `boyeon` 을 `main` `7bac2a1` 로 fast-forward. 로컬 진짜 모드 확인 시도 → 사용자 PC PostgreSQL 인증에서 멈추고 "코드 작업 먼저" 로 전환. 세션 · 인물 adapter 정리(`78952fe`), 모든 포트 진짜 연결 · 운세 상품 목록(`e781919`), 결정 RESULT-GUIDE-MODEL → 결과 포트 · 화면(`086ed90`) · 결과 adapter(`a352829`), 구매 상태 조회(`9f1366e`), 결정 PURCHASE-STATUS-UI → 구매 팝업(`9addcee`), PURCHASE-KEY-STORE 구현(`afcd0b3`). 매 단계 작업 공간 재현 · 변이 확인. 오후 `main` PR #24(BE-A 충전 환불 · 대사, FE 변경 없음) → Q-41. 문서(PROGRESS · PHASES v3.3 · TEAM-QUESTIONS Q-39 ~ Q-41 · CLAUDE). 테스트 73 파일 · 833 개
- 2026-10-11 (오후) · `main` 76b7f67(PR #19 · #21) 병합 + 수능운 `productName` · `regularPrice`(`41f93af`), 충전 adapter 상품 목록 · 승인 · 주문 조회(`b7af91a`), 토스 V2 결제창 + 가짜 모드 스위치(`ecaaba4`, 결정 TOSS-LAUNCHER-SWITCH · TOSS-ANONYMOUS), PAY-03 지급 수량(`15d98ff`). 토스 1단계 로컬 시험(사용자 PC Chrome, 문서용 테스트 키) 통과 — 결제창 표시 · 결제 · `/pay/success` 복귀. `boyeon` → `main` PR #22 병합. 테스트 69 파일 · 674 개
- 2026-10-11 (오전) · 1 ~ 5단계: 원격 확인 · BE ↔ FE 연결 점검(브라우저에서 성공하는 API 0개 — 인증 · 세션 · CSRF · 인물 · 토스 없음, R-06), `boyeon` 을 `main` f704145 로 fast-forward, 결정 SUNEUNG-ADAPTER-FIRST → 운세 포트 수능운 계약 · 수능운 adapter(`1fcd9c0`, 작업 공간 재현 · 변이 22개). 팀 요청서 "뿌기사주 팀 요청서 (FE, 10/11)"(claude.ai 문서). 세션 마무리 문서(PROGRESS · PHASES v3.2 · TEAM-QUESTIONS · CLAUDE). 테스트 68 파일 · 610 개
- 2026-10-10 (오후) · BE-B 회신 "A/B 구매 연결 합의 회신" 검토 → Q-38 답변 · 반영 대기, 후속 질문 a ~ g, 결정 PURCHASE-STATUS-WAIT · PURCHASE-KEY-STORE(문서 반영은 10/11). 코드 변경 없음
- 2026-10-10 (오전) · 58 ~ 63단계: BE-B 답변서 2종 · BE-A PR #17 · FE 전달 · 사업자등록증 대조 → TEAM-QUESTIONS(`1a9d877`, Q-37 · Q-38 신설) · 팀 회신 문서(`뿌기사주-FE-회신-20261010.md`). `main` PR #17 병합(`f207fd9`), 사업자 값 4개(`5bee76c`), 구매 결과 처리 Q-38(`8feb588`), OpenAPI 생성 타입(`2c9a17b`), 결정 ADAPTER-HTTP · 오행분석 adapter(`e6a603a`) · 충전 adapter(`c46bfa7`), 문서(`a4d2dbd`), 63단계 HOME-03 테스트(`79a2df0`). 매 단계 작업 공간 재현 · 변이 확인. 테스트 67 파일 · 558 개
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
