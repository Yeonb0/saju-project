# 뿌기사주 — 개발 Phase 계획

> **FE 개인 작업 문서다. 팀 합의 문서가 아니다.** 일정 · Phase · 담당 표기는 FE 가 작업 순서를 잡으려고 세운 계획이다 (DOCS-FE-OWN).
> **결정 기준은 GitHub 의 팀 문서다** — 루트 [`docs/PENDING_DECISIONS.md`](../../docs/PENDING_DECISIONS.md) (DOCS-GITHUB). 결정은 팀 ID(P- · F- · G- · A- · S- · T- · X- · O- · D- · I-)로 부른다. 옛 FE 결정 번호 D-01 ~ D-16 은 닫았고 기록에서만 "구 D-xx" 로 부른다 (7장, DECISION-IDS).
> 팀 문서끼리 어긋나거나 정의가 빈 곳은 [`TEAM-QUESTIONS.md`](TEAM-QUESTIONS.md) (Q-xx · R-xx). BE 일정은 [`backend/docs/BACKEND_ROLE_SPLIT.md`](../../backend/docs/BACKEND_ROLE_SPLIT.md) 9장.
> 작성: 2026-09-26 (토) · 상태: **v3.8** (2026-10-16 저녁 AWS Amplify 배포 시작 — 도메인 `ppugi-saju.kr` · 배포 단계 변수 `NEXT_PUBLIC_DEPLOY_ENV` · v3.7 2026-10-16 직접결제 전환 — 등껍질 충전 · 차감 삭제, 운세 건별 원화 결제 · 정책 v4 · 호스팅 AWS Amplify 예정 · v3.6 2026-10-14 저녁 PG 심사 점검 · 퀵오픈 방향 · 정책 원고 표시 구조 · v3.5 2026-10-14 기능 명세서 v0.4 반영 — PD 확정 문구 · 일반 운세 질문 칩 · 일반 운세 진짜 연결 · 수능 8장 카드 · 구매 완료 안내 · v3.4 2026-10-13 BE 고객센터 환불 전환 대응 · PAY-03 유료 · 보너스 내역 · 지갑 확인 안내 · v3.3 2026-10-12 FE 진짜 연결 코드 완료 · 결과 서버 구조 · 구매 상태 · v3.2 2026-10-11 BE ↔ FE 연결 점검 · 수능운 견적 · 구매 adapter · v3.1 2026-10-10 오전 팀 답 반영 · OpenAPI 생성 타입 · 첫 adapter 2개 · v3.0 2026-10-09 오후 PG 가짜 범위 배치 완료 · BE-B PR #15 연동 TODO · v2.9 PD 추가 화면 PAY-03 ~ 05 · INFO · COMMON, 디자인 시스템 확정 · v2.8 유료 운세 5종 · 궁합 · 잔액 부족 · 분석 로딩 가짜 범위 · 2026-10-08 와이어 배치 LAYOUT-FIGMA · BE-A PR #14 · 2026-10-05 MOCK-PORT 진행 · 2026-10-04 GitHub 팀 문서 + 팀 답 + 팀 결정 + 최종 와이어프레임 + 기능 명세서 v0.3)
> 변경: 2026-09-26 — 프론트엔드 스택 확정(3-1), 손그림 테두리 `border-image`(4장), 서비스명 「뿌기사주」(구 D-11), FE 전용 내용은 `FRONTEND.md` 로 분리
> 변경: 2026-09-30 — 용어 복원(DOCS-RESTORE), FE 브랜치 `boyeon`(BRANCH), Phase 0 · 1 체크박스, 글꼴 보류(FONT-HOLD), 라우트 오류 · 404 화면, `frontend/docs/` 로 이동(DOCS-FE-OWN)
> 변경: 2026-10-03 — **v2**: 공용 문서(PR #5) 반영. 구 D-xx 닫고 팀 ID 로 전환(DECISION-IDS). **PG 심사 트랙 신설 · 최우선 — 백엔드 연결과 결제 플로우만, 디자인 요소 배제 (PG-FIRST)**. Phase 2 를 PG 트랙에 흡수. 운세 6종(일반 5 + 수능운), 선물 단건 · 원화 결제 · 알림톡, 충전 상품 6종(P-02 변경). 결과 뷰어 ReadingViewer 하나(VIEWER), 손그림체(STYLE-HAND). (10/1 에 만든 v1 은 배치하지 않고 이 판으로 대체)
> 변경: 2026-10-03 — **v2.1 팀 답 1차 반영 (TQ-ANSWERS)**: CSRF 방식(Q-02 해결), 충전 완료는 `CREDITED` 만(TOPUP-DONE), 수능 시험 고정(Q-04 해결), 견적 · 구매 선택 복원(Q-07 해결, PURCHASE-RESTORE), 충전 화면 `/wallet` · 공유 랜딩 · 부적 자동생성(Q-01 · Q-10 · Q-06, 팀 문서 반영 대기), 선물 범위 · 결제 재화(Q-08 · Q-16, 팀 확정 대기). PG-4 사업자 정보 푸터 틀 진행 기록. BE 일정 재산정(R-06)
> 변경: 2026-10-03 — **v2.2 팀 결정 반영 (TEAM-1003, 팀 문서 반영 대기)**: 선물도 등껍질 결제(원화는 충전만, Q-16), 1차 선물은 수능운만(Q-08), 부적은 고르지 않고 결과 기반 자동생성(Q-06), 단위 "등껍질 N개"(Q-03), 신살 일반 5종 포함(잠정). PG-1 Idempotency-Key 헬퍼(`353d9b8`)
> 변경: 2026-10-03 — **v2.3 PD 추가 답변 · 팀 문서 2차 반영 (DOCS-TEAM-PR)**: FE 가 팀 문서를 고쳐 PR(`cc60ab3` — PENDING_DECISIONS · FUNCTIONAL_SPEC · PRD, `main` 병합 · PD 검토 대기). 가격 P-03 · P-03A(등껍질), 부적 자동생성 규칙 T-01, 신살 구성 F-03, 보너스 유효기간 1년 P-04, BE-A 제안 결정(P-02A · P-05 · G-07 · G-09 · G-10) PD 동의, `/wallet` · `/share/[shareId]` 라우트. 상태 "반영 PR 대기" 추가(TEAM-QUESTIONS)
> 변경: 2026-10-04 — **v2.4 최종 와이어프레임 (FIGMA-FINAL)**: Figma 페이지 `와이어프레임 최종`(node `195:91`)을 기준으로 1장 화면 목록을 새 화면 ID(HOME- · TODAY- · MY- · TALBOX- · FORT- · MATCH- · PAY- · GIFT- · RECV- · RECV-T- · CSAT-)로 다시 썼다. 구 번호 #1 ~ #40 은 1장 "구 #" 열에만 남긴다. 결제 확인이 화면에서 팝업으로, 충전 화면(PAY-01 · 02) · 궁합 사람 선택(MATCH) · `부적` 선물 수신(RECV-T) 추가. 와이어와 팀 결정이 부딪히는 곳은 Q-23 ~ Q-27 (해결 전에는 팀 문서를 따르고 와이어만의 부분은 만들지 않는다)
> 변경: 2026-10-04 — **v2.5 기능 명세서 v0.3 반영**: 팀 문서 3차(`9fb9d21`, `main`) — 소셜 로그인 3종(A-01 부분) · 로그인 직후 정보 입력(A-03) · 오늘의 운세 로그인 필요(A-02 · X-01) · 관계 칩(A-07) · 부적 동물 12지 랜덤 · 창고 같은 동물(T-01 · T-06) · 오행분석 결제 전 노출(F-09). Checkout 경로 3개 삭제 → 앞 화면 팝업 (CHECKOUT-POPUP). 서비스명 뿌기사주. 유저 플로우 FigJam `dd8IamO1coU9vpa4P7AgMi`
> 변경: 2026-10-05 — **v2.6 포트 + 가짜 구현 (MOCK-PORT) · 코드는 Claude Code (CODE-BY-CC)**: PG-1 · PG-2 · PG-3 · Phase 3 에 "가짜 구현 범위" 하위 체크박스를 두고 끝낸 것을 [x] 로 표시. 상위 체크박스는 진짜 구현 · 실기기 확인 후 [x]
> 변경: 2026-10-08 — **v2.7 와이어 배치 (LAYOUT-FIGMA) · 가짜 구현 확장**: PG-FIRST 를 일부 풀어 최종 와이어의 글자 크기 · 굵기 · 위치 · 간격 · 상자를 넣는다(글꼴 · 손그림 프레임 · 캐릭터 · 에셋 · 색 토큰은 계속 보류). Phase 4 차감 확인 · 잔액 부족(추천 충전) · 생성 대기 · ReadingViewer · CSAT-01 가짜 범위, PG-4 `/about` 틀. `main` BE-A PR #14(상품 · 견적 · 지갑 조회 내부 구현, HTTP 없음) 병합
> 변경: 2026-10-09 — **v2.8 A 화면 가짜 범위 완료**: HOME-03 · 04, FORT-01 ~ 05, MATCH-01 ~ 03, PAY-02 · FORT-04, CSAT-02 를 와이어 배치로 끝냈다(체크박스 아래). 오늘의 운세(TODAY-01 · 02)는 계약 대기로 보류(TODAY-HOLD · Q-31). 궁합 첫 칸은 본인 고정(MATCH-SELF)
> 변경: 2026-10-09 — **v2.9 PD 추가 화면 · 디자인 시스템**: 최종 와이어(`195:91`)에 PD 가 PG 관련 화면을 더했다 — PAY-01 수정안(`301:128`, 결제 수단 4칸 삭제 → 결제하기 누르면 바로 토스 결제창), PAY-03 충전 완료 · PAY-04 결제 확인 중 · PAY-05 결제 실패 · 취소, INFO-01 `/about` · INFO-02 정책 페이지 · COMMON-01 사업자 정보 푸터. 디자인 시스템 Figma `iFtLxChBQf3nQWaltdrJ37` 페이지 `디자인 시스템`(`67:66`) 확정(사용자 10/9) — 팀 문서 D-01 · D-02 · D-03 · D-05 와 달라 Q-32. 로그인 3종 버튼(LOGIN-3)
> 변경: 2026-10-09 — **v3.0 PG 가짜 범위 배치 완료 · BE-B 연동 TODO**: PG-2 로그인 제공자(화면은 카카오만, LOGIN-3 개정), PG-3 PAY-01 수정안 · PAY-03 ~ 05, PG-4 INFO-01 · 02 · COMMON-01 을 와이어 배치로 끝냈다(체크박스 아래). `main` BE-B PR #15(`08fccad` — 오행분석 · 카테고리별 견적 · 구매 · 결과 API) 병합, 연동 체크박스를 "BE-B 계산 · 생성 API 연동" 에 새로 두었다(오행분석은 가짜 범위 완료). 정책 초안은 `docs/policies/`(POLICY-DRAFTS)
> 변경: 2026-10-10 — **v3.1 OpenAPI 파일 수령 · 첫 adapter**: BE-A PR #17(`f47b719`)로 `docs/openapi/api-v1.json` · 견적 자금 필드 · 견적 재조회 · 오행 `personId` 가 `main` 에 들어왔다(Q-18 · Q-30 · Q-34 해결 다수). 생성 타입(`2c9a17b`), adapter 틀 ADAPTER-HTTP(`http.ts` + zod + 객체 리터럴 `satisfies`, openapi-fetch 미사용) — 오행분석(`e6a603a`) · 충전 지갑 · 주문 생성(`c46bfa7`). 진짜 모드 연결은 세션(CSRF) adapter 후. 구매 결과 처리 Q-38(`8feb588`), 사업자 값 4개(`5bee76c`, R-08 해결)
> 변경: 2026-10-11 — **v3.2 연결 점검 · 수능운 adapter**: `main` f704145 기준 BE ↔ FE 연결을 점검했다 — BE 에 로그인 · 세션 · CSRF 토큰 출처 · 인물 · 토스 연동이 없어 브라우저에서 성공하는 API 가 없다(R-06). 운세 포트를 수능운 OpenAPI 계약에 맞추고 수능운 견적 · 견적 재확인 · 구매 adapter(`1fcd9c0`) — 일반 운세 5종은 질문 칸 ↔ 서버 값 대응(Q-26 보강) · 관계 대응(Q-35 2) · 상품 메타(Q-28) 답 후(SUNEUNG-ADAPTER-FIRST). 구매 상태 조회는 OpenAPI 반영 후(PURCHASE-STATUS-WAIT), 구매 키 보관 원칙(PURCHASE-KEY-STORE). 팀 요청서(10/11) 공유
> 변경: 2026-10-12 — **v3.3 진짜 연결 코드 완료**: 10/11 오후 충전 상품 목록 · 승인 · 주문 조회(`b7af91a`) · 토스 V2 결제창(`ecaaba4`, TOSS-LAUNCHER-SWITCH · TOSS-ANONYMOUS) · PAY-03 지급 수량(`15d98ff`), 토스 1단계 로컬 통과. 10/12 `main` PR #23(BE-B 카카오 OAuth · 세션 · CSRF · 인물 · OpenAPI 재생성)으로 세션 · 인물 adapter 정리(`78952fe`) → `ports/index.ts` 전부 진짜 연결(`e781919`). 결과 포트를 서버 `suneungGuide` 구조로(RESULT-GUIDE-MODEL, `086ed90` · `a352829`), 구매 상태 조회 · 서버 상태 기준 팝업 · 구매 키 보관(`9f1366e` · `9addcee` · `afcd0b3`, PURCHASE-STATUS-UI · PURCHASE-KEY-STORE). 상위 체크박스는 스테이징 실제 확인 전이라 그대로 [ ]. BE-A PR #24(충전 환불 · 대사, 기능 꺼짐) → Q-41
> 변경: 2026-10-13 — **v3.4 BE 고객센터 환불 전환 대응**: `main` PR #25(관리자 조회 · 복구) · #28(BE-B — 세션 수 제한 · CSRF Origin 검사 · 로그아웃 204 · 충전 내역 목록 · 오늘의 운세 공유 링크 · **FE 환불 포트 · adapter 직접 추가**) · #27(BE-A — 정책 확정 · **남은 유료분 비례 환불 · 고객센터 접수 → 관리자 실행, 사용자 환불 POST 제거** · `PARTIALLY_REFUNDED`). `boyeon` 에 병합(`c6adbea`) 뒤 공유 OpenAPI 를 서버 export 로 맞추고 환불 신청 코드 제거(`78c8b10`, OPENAPI-FE-SYNC · REFUND-CS), PAY-03 유료 · 보너스 내역(`4182746`), `WALLET_REVIEW_REQUIRED` 문의 안내(`3f62019`). 충전 내역 · 주문 상세 환불 상태 화면은 PD 배치 대기(Q-41 6). 스테이징은 아직 없다(R-06)
> 변경: 2026-10-14 — **v3.5 기능 명세서 v0.4 반영**: PD 10/13 1 · 2차 답 · 정책 v2 · 와이어 수정안을 FE 가 기능 명세서 v0.4 로 옮겨 `main` 병합(PR #38 `84f87f0`, SPEC-V04 — PD 는 GitHub 를 쓰지 않아 검토는 팀 확인 요청 문서로). 그 뒤 코드: 수능 카드 · 교시 · 준비물 이름(`608a5f5`), 구매 상태 COMMON-02 ~ 04 · 문의하기 메일(`4e6cab1`), 충전 동의 · 안내 · PAY-06(`9be6276`), PAY-03 ~ 05 문구(`d777e5a`), 다시 결제 상품 유지(`df940fb`, TOPUP-RETRY-URL), 로그인 · 푸터 문구(`94f697f`), 일반 운세 질문 칩 · 복원 v4(`536b810`), 일반 운세 5종 진짜 adapter(`2f505f7`), 수능 8장 카드(`f8148ea`), 구매 완료 안내(`b3d2051`), 궁합 「연인」 미리 고르기(`6cee20d`). 새 질문 Q-42 ~ Q-63 (TEAM-QUESTIONS)
> 변경: 2026-10-14 (저녁) — **v3.6 PG 심사 점검 · 퀵오픈 방향**: `main` 과 토스 심사 체크리스트를 대조 — 충전 상품 6종 비활성 · 정책 본문 · 푸터 3개 값 · 상품 이미지 · 운영 배포가 없고, 일반 심사(최대 10 ~ 14영업일)로는 10/31 에 결제가 안 열린다. 사용자 방향: 토스 **퀵오픈** 추진(R-10 — 10/15 토스 통화 후 확정, 안 되면 계좌이체 수동 충전). PG 트랙 일정 · PG-4 · BE-A 줄 · 리스크 갱신. 통신판매업은 신고 진행(R-07). 정책 원고 표시 구조(`7fb0658`). `main` PR #33 · #39 · #41(BE-A — 선물 관리자 · 견적 고객센터 조회 · 직접 링크 재발급, 팀 문서 4개에 선물 1차 직접 공유) → Phase 5 이름 · Q-54 · Q-55
> 변경: 2026-10-15 ~ 16 — **v3.7 직접결제 전환 (DIRECT-PAY)**: 10/15 정책 v3 배치(`10e26c7`) · 푸터 항목 이름 한 줄 · 충전 버튼 「N원 결제하기」(`d8ddb91`) · 만들지 않은 화면은 「준비 중이에요」 만 · HOME-02 윤달 줄(`683506a`) · 유선번호(`c4b1e8d`) · 기능 명세서 MY-03 · 05 · 07 경로 PD 확정(`dc9b9d9`, Q-57). 10/16 사용자 결정으로 등껍질 충전 · 차감을 없애고 운세 한 건씩 원화로 결제한다 — 포트 · 가짜(`7c02326`), 결제하기 팝업(`33d6a83`), 복귀 화면 · 결제 뒤 생성(`06f6dca`), 홈 · `/about` 정리(`45d2422`), 정책 v4 이용약관 · 환불정책(`362d99d`), 진짜 adapter — PR #49 컨트롤러 기준(`274ebf8`), PR #51 병합(`2aadee5`), `/wallet` 삭제(`cf12a00`). PG-3 를 "직접결제 플로우" 로 다시 썼다. BE 답(10/16): PG사 아직 선정 전, 수능운 전용 결제 뒤 생성 경로(Q-64) · 결제 내역 목록(Q-66) · OpenAPI(Q-68) 진행. 통신판매업은 면제 표기(R-07), 호스팅은 AWS Amplify 로 이전 예정(R-11)
> 변경: 2026-10-16 저녁 — **v3.8 AWS Amplify 배포 (HOSTING-AMPLIFY 진행)**: 도메인 `ppugi-saju.kr`(Route 53, `www` → 301, SITE-DOMAIN), Amplify 앱 · 루트 `amplify.yml`(`63b0f61`), 배포 단계 변수 `NEXT_PUBLIC_DEPLOY_ENV`(`3fe6072`, DEPLOY-ENV) — PG 트랙 "AWS Amplify 이전 준비" 끝. `boyeon` 미리보기(가짜 모드)가 Amplify 에서 동작, Next.js 16 SSR 확인. BE 는 Elastic Beanstalk(PR #56), `api.ppugi-saju.kr` 인증서 진행 중. `main` PR #57 로 직접결제 컨트롤러 병합 · OpenAPI 는 아직(Q-68), 결과 10섹션 · 생성 미리보기 계약 차이(Q-69)
> 기준 날짜: 출시 **10/31(토)** · 평가 기간 **10/31 ~ 11/21** · 수능 **11/19(목)** → 출시일은 **수능 D-19**
> 디자인 소스: Figma `9zEtrqV4SoPbQzuworSNUj` / 페이지 **`와이어프레임 최종`(node `195:91`)** — FIGMA-FINAL. 옛 페이지 `와이어프레임`(node `17:2`)은 기록용

---

## 0. 한눈에 보기

| Phase | 이름 | 기간 | 핵심 담당 | 성격 |
|---|---|---|---|---|
| 0 | 결정 · 행정 착수 · 초기 세팅 | 9/26 ~ 9/30 | 전원 | 결정 · 세팅 (FE 부분 완료) |
| 1 | 화면 골격 (라우팅 + 빈 화면 + 공통 레이아웃) | 9/29 ~ 10/4 | FE | 기반 (FE 골격 완료, 디자인 항목 보류) |
| **PG** | **PG 심사 트랙 — 백엔드 연결 + 충전 결제 + 심사 노출 요건** | **10/3 ~ 10/12** (심사 요청 10/14 전후 → 못 함. 10/15 퀵오픈 결정, R-10) | FE, BE-A, BE-B | 🔴 최우선 (PG-FIRST) |
| ~~2~~ | ~~결제 파이프라인~~ → PG 트랙에 흡수 | — | — | — |
| 3 | 인물 정보 · 온보딩 | 10/6 ~ 10/14 | FE, BE-B | 기반 |
| 4 | **수능운 본인 구매(등껍질 차감) + 부적 + ReadingViewer** | 10/11 ~ 10/17 | FE, BE-B, BE-A | 💰 수익 핵심 #1 |
| 5 | **선물하기 (수능운 · 등껍질 결제 · 링크 직접 공유 · 비로그인 수신)** — 알림톡은 2차 (팀 문서 10/14, Q-54 PD 확인 대기) | 10/18 ~ 10/23 | FE, BE-A, BE-B | 💰 수익 핵심 #2 |
| 6 | 일반 운세 5종 · 부가 기능 · 디자인 적용 | 10/14 ~ 10/27 | FE, BE-B, PD | 컷 순위 있음 |
| 7 | 출시 준비 (QA · 약관 · 실결제 전환 · 모니터링) | 10/22 ~ 10/30 | 전원 | 게이트 |
| 8 | 운영 (지표 · 수능 피크 대응 · 정산) | 10/31 ~ 11/21 | 전원 | 운영 |

Phase 4 · 5 의 기간은 BE 일정(`BACKEND_ROLE_SPLIT.md` 9장)에 맞췄다 — 10/11~10/17 본인 수능 E2E, 10/18~10/23 선물 결제 → 알림톡 → 비로그인 결과 E2E, 10/24~10/27 일반 5종 · 공유 · 창고.

**달력상 주의**
- 10/3(토) 개천절 → 10/5(월) 대체공휴일, 10/9(금) 한글날. 세무서 · PG사 영업일이 빠진다.
- 코드 프리즈: **10/28(수) 18:00**. 이후는 버그 수정과 문구 수정만.
- 수능 매출은 10/31 ~ 11/19 에 몰린다.

---

## 1. 화면 목록

기준은 Figma 페이지 `와이어프레임 최종`(node `195:91`, FIGMA-FINAL). 화면 ID 는 프레임 이름을 그대로 쓴다. "구 #" 은 옛 와이어(node `17:2`)의 번호로, 기록 · 코드(`src/lib/screens.ts` 의 `ScreenNo`)에만 남아 있다 — 코드 전환은 Phase 5 의 `screens.ts` 체크박스.
"Phase" 열은 **기능이 실제로 동작하게 되는 단계**다. 모든 라우트의 빈 골격은 Phase 1 에서 만들었다.
같은 모양의 화면은 공통 컴포넌트 하나로 만든다 ([`FRONTEND.md`](FRONTEND.md) 3장). 일반 5종(`overall` · `love` · `wealth` · `compatibility` · `sinsal`)은 FORT-01 ~ FORT-09 를 함께 쓰고, 궁합만 MATCH-01 ~ 03 으로 사람 선택 · 질문이 다르다. 와이어의 문구 · 수량 · 회색 · 아이콘 · 글꼴은 자리표시다.

| ID | 화면명 | Figma (node) | 구 # | 라우트 | Phase · 비고 |
|---|---|---|---|---|---|
| HOME-01 | 로그인 | 195:774 | 1 | `/login` | PG (PG-2) — 카카오 · 네이버 · 구글 (A-01). 화면은 PG 심사까지 카카오만(LOGIN-3 개정). 진짜 연결은 카카오(`78952fe`, 백엔드 `authorize` 이동) — 네이버 · 구글 경로는 BE-B · 심사 후 |
| HOME-02 | 정보 입력 (정보 저장하기) | 195:256 | 2 | `/onboarding` | 3 — 이름 · 생년월일 · 양력/음력 · 시간 · 시간 모름 · 성별. 윤달은 음력일 때만 (FUNCTIONAL_SPEC 4장). 본인 정보가 없으면 로그인 직후 이동 (A-03) |
| HOME-03 | 홈 | 195:92 | 4 | `/` | 1 (정적) → 6 — 등껍질 잔액 · 충전 진입(→ PAY-01), 오늘의 운세, 부적 모음(→ TALBOX-01), 유료 운세 5종, 이벤트 운세(수능운 · 준비중), 선물하기 |
| HOME-04 | 사이드 메뉴 (오버레이) | 195:647 | 5 | 공통 | 1 — 메뉴: 홈 · 마이페이지 · 내 부적 창고 · 오늘의 운세 · 유료 운세 · 수능운 (FUNCTIONAL_SPEC 1장, 와이어의 취업운 · 애정운 삭제) |
| TODAY-01 | 오늘의 운세 — 로딩 | 195:113 | 6 | `/today` | 6 |
| TODAY-02 | 오늘의 운세 — 결과 | 195:758 | 7 | `/today` | 6 — 한줄 · 운세 지수 · 행운 색 · 아이템 · 장소 · 오늘의 행동 (X-02). 로그인 + 본인 정보 필요 (X-01) |
| MY-01 | 마이페이지 | 195:145 | 37 | `/me` | 6 — 로그인 계정 · 로그아웃, 내 운세 기록, 저장된 사람들, 충전 · 사용 내역 · 약관 · 환불정책 · 개인정보처리방침 · 탈퇴 링크. 보낸 선물 없음 (Q-24) |
| MY-02 | 사람 정보 저장 | 195:274 | 3 | `/me/people/new` · `[id]` | 3 — 관계 엄마 · 아빠 · 애인 · 직접 입력 (A-07). 권한 확인 체크(O-03)는 와이어 반영 예정 |
| TALBOX-01 | 내 부적 창고 — 목록 | 195:666 | 38 | `/vault` | 6 — 같은 동물 ×N · 12지 도감 (T-06, 와이어 반영 예정) |
| TALBOX-02 | 내 부적 창고 — 상세 (바텀시트) | 195:701 | 39 | `/vault` | 6 — 저장 · 공유 |
| (팝업) | 부적이 저장되었어요 (홈으로 · 창 닫기) | 195:796 부근 | 40 | 공통 | 4 (Modal) |
| FORT-01 | 유료 운세 — 정보 확인 | 195:180 | 8 | `/fortune/[type]` | 6 — 본인 카드 · 수정 · 저장된 다른 사용자 불러오기, 오행분석 5줄 — 결제 전 노출 (F-09 확정) |
| FORT-02 | 종합 · 재물 · 신살 — 질문 | 195:611 | 9 + 10 | `/fortune/[type]/questions` | 6 — "특별히 알고싶은 부분은?" + 옵션 버튼 2개(사주 보고 부적도 받기 / 사주만 보기) = 옵션 선택이 질문 화면에 합쳐짐 (CHECKOUT-POPUP). 입력 칸은 고민 입력 — 결과 반영은 F-04 변경 제안 (Q-26) |
| FORT-03 | 애정운 — 질문 | 248:956 | 9 + 10 | `/fortune/[type]/questions` | 6 — "지금 연애 상태는?" 추가 |
| MATCH-01 | 궁합 — 사람 선택 | 248:798 | — | `/fortune/compatibility` | 6 — 본인 + 상대, 상대가 없으면 MY-02 에서 새로 입력 후 복귀 |
| MATCH-02 | 궁합 — 사람 선택 완료 | 248:827 | — | `/fortune/compatibility` | 6 |
| MATCH-03 | 궁합 — 질문 | 248:849 | 9 + 10 | `/fortune/[type]/questions` | 6 — "두 사람의 관계는?" + 옵션 버튼 2개 |
| (팝업) | 등껍질 차감 확인 — "등껍질을 사용해서 사주를 볼까요?" 보유 · 사용 | 248:267 부근 · 248:537 부근 | 10 · 16 | 앞 화면 안 | 4 — Checkout 경로 없음 (CHECKOUT-POPUP). 상품명 · 대상 · 옵션 · 보유 · 사용 · 구매 후 잔액(서버 견적) + 고지 F-08 |
| (팝업) | 잔액 부족 — "등껍질이 부족해요." 보유 · 부족 → 충전 | 248:712 부근 · 240:123 부근 | — | 앞 화면 안 | 4 (P-06) |
| FORT-04 | 등껍질 충전 — 잔액 부족 | 248:244 | — | `/wallet` | PG (PG-3) / 4 — PAY-02 와 같은 화면 |
| FORT-05 | 유료 운세 — 분석 로딩 | 195:121 | 11 | `/fortune/r/[readingId]` | 6 (LoadingScene) |
| FORT-06 | 유료 운세 — 결과 (부적 포함) | 195:622 | 12 | `/fortune/r/[readingId]` | 6 — 요약 · 소제목/내용 반복 · 저장 · 공유 · 부적 확인하기 |
| FORT-07 | 유료 운세 — 결과 (부적 미포함) | 248:278 | 12 | `/fortune/r/[readingId]` | 6 — "부족한 기운을 채워줄 부적 만들기" → 차감 확인 팝업 (부적 추가 구매) |
| FORT-08 | 부적 생성 로딩 | 195:137 | 13 | `/talisman/[id]` | 4 (LoadingScene) |
| FORT-09 | 내 부적 | 195:744 | 14 | `/talisman/[id]` | 4 — 부적 설명 · 저장 · 공유 · 내 부적 창고로 |
| PAY-01 | 등껍질 충전 | 237:69 → **수정안 301:128** | — | `/wallet` | PG (PG-3) — 수정안(10/9): 결제 수단 4칸 삭제 → 결제하기 누르면 바로 토스 결제창, 상품 6종 카드형(선택 강조), 안내 상자(지급 시점 · 유효기간 · 7일 환불 · 차감 순서 · 환불정책 링크), 동의 체크(필수), "N원 결제하기". 문구 · 결제 수단은 Q-25 부분 · Q-21 |
| PAY-02 | 등껍질 충전 — 잔액 부족 | 240:98 | — | `/wallet` | PG (PG-3) — 상단에 보유 · 부족 (서버 견적 값) |
| (팝업) | 충전 완료 — "이어서 사주를 볼까요?" 보유 · 사용 → 원래 화면 | 244:189 부근 | — | `/wallet` | PG-3 / 4 (P-06 · PURCHASE-RESTORE) |
| PAY-03 | 충전 완료 | 301:166 | — | `/pay/success` | PG (PG-3) — 뿌기 · "충전 완료!" · 들어온 수량 · 보유(유료 · 보너스 내역). 구매 중 충전이면 "이어서 ~ 하러 가기" + 홈으로, 아니면 홈으로 하나. CREDITED 일 때만 (TOPUP-DONE). 위 (팝업) 충전 완료를 대체 |
| PAY-04 | 결제 확인 중 | 301:180 | — | `/pay/success` | PG (PG-3) — 처음엔 위쪽만, 30초 지나면 안내 상자 + "주문 확인하기" (TOPUP-DONE 와 같음). 확인되면 PAY-03, 실패면 PAY-05 |
| PAY-05 | 결제 실패 · 취소 | 301:190 | — | `/pay/fail` | PG (PG-3) — 둘째 줄 사유만 바뀜(취소 / 실패 + 토스 사유 — 원문 노출 규칙 확인 필요), "다시 결제하기" → PAY-01 (선택 상품 유지) · 홈으로 |
| INFO-01 | 서비스 · 상품 소개 | 302:129 | — | `/about` | PG (PG-4) — 로그인 없이. 소개 · 판매 상품(서버 값) · 등껍질로 이용하는 콘텐츠 · 제공 방법 · 기간 · 유효기간 · 환불 안내 · 고객센터 · 푸터. 원고는 R-01 · Q-21 · PD |
| INFO-02 | 정책 페이지 (`/refund` · `/terms` · `/privacy` 공통) | 302:155 | — | `/refund` · `/terms` · `/privacy` | PG (PG-4) — 제목 · 시행일 · 목차 · 조항, 글자 14 이상, 로그인 없이. 본문은 PD 원고 |
| COMMON-01 | 사업자 정보 푸터 | 302:175 | — | 공통 | PG (PG-4) — 모든 페이지 맨 아래(홈 · 충전 · 소개 · 정책 필수), 약관 3종 링크(개인정보처리방침 굵게) + 8개 항목 + © 뿌기사주. 값은 R-07 · R-08 |
| CSAT-01 | 수능운 — 정보 확인 | 195:202 | 15 | `/suneung` | 4 — 시험 선택 없음 (Q-04), 오행분석 5줄 — 결제 전 노출 (F-09). 다음은 차감 확인 팝업 (CHECKOUT-POPUP) |
| CSAT-02 | 수능운 — 분석 로딩 | 195:129 | 17 | `/suneung/r/[readingId]` | 4 |
| CSAT-03 | 수능운 — 결과 목차 (교시별 사주 · 준비물 · 도시락 추천) | 195:368 | 18 | `/suneung/r/[readingId]` | 4 — 수능 섹션 구성이 F-02 와 다름 (Q-23 g). 서버 `suneungGuide` 8개 항목을 카드로(RESULT-GUIDE-MODEL) — 이름 · 순서는 PD (Q-40 2) |
| CSAT-04 | 수능운 — 교시별 사주 | 195:397 | 19 | `/suneung/r/[readingId]` | 4 |
| CSAT-05 | 수능운 — 수능 도시락 | 195:432 | 20 | `/suneung/r/[readingId]` | 4 |
| CSAT-06 | 수능운 — 준비물 체크리스트 | 195:463 | 21 | `/suneung/r/[readingId]` | 4 |
| CSAT-07 | 수능운 — 수능 부적 | 195:495 | 22 | `/suneung/r/[readingId]` | 4 — 저장 · 공유 · 다른 운세도 보러가기 |
| GIFT-01 | 선물하기 — 옵션 선택 | 195:224 | 23 | `/gift/new` | 5 — 수능 칩 + `사주 + 부적 1장` / `부적만`, "수신인 당" 가격 |
| GIFT-02 | 선물하기 — 수신인 입력 | 195:238 | 25 | `/gift/new` | 5 — 이름 · 전화번호 · 번호 사용 동의 체크. "여러명에게 선물하실건가요?" 링크는 만들지 않음 (Q-23) |
| GIFT-03 | 선물하기 — 메시지 카드 | 195:509 | 27 | `/gift/new` | 5 — 보내는 사람 · 응원 메시지 → "선물하러 가기" → 차감 확인 팝업 (`/gift/checkout` 삭제, CHECKOUT-POPUP) |
| (팝업) | 선물 차감 확인 — "등껍질을 사용해서 부적을 선물할까요?" | 248:321 부근 | 28 | 앞 화면 안 | 5 |
| GIFT-04 | 선물하기 — 완료 | 195:557 | 29 | `/gift/done/[orderId]` | 5 — 발송 대기 상태 · 재발송 없음 (Q-24) |
| GIFT-05 | 선물하기 — 발송 실패 | 248:1081 | 29 | `/gift/done/[orderId]` | 5 — "직접 링크를 공유해주세요 · 링크 복사하기" 는 만들지 않음 (G-10 · I-06, Q-23) |
| GIFT-06 | 여러 명 — 수신인 입력 | 248:326 | — | — | 만들지 않음 — 선물 단건 (P-03A, Q-23) |
| GIFT-07 | 여러 명 — 메시지 공통 | 248:355 | — | — | 만들지 않음 (Q-23) |
| GIFT-08 | 여러 명 — 메시지 개별 | 248:374 | — | — | 만들지 않음 (Q-23) |
| RECV-01 | 선물 받기 (사주 + 부적) — 도착 | 195:340 | 30 | `/g/[token]` | 5 |
| RECV-02 | 선물 받기 — 정보 입력 | 248:771 | 31 | `/g/[token]` | 5 — 생년월일 · 양력/음력 · 시간 · 시간 모름 · 성별 · 개인정보 사용 동의 체크 |
| RECV-03 | 선물 받기 — 메시지 확인 | 195:358 | 32 | `/g/[token]` | 5 — 결과 안내 + 보낸 사람 메시지 |
| RECV-04 | 선물 받기 — 교시별 사주 | 195:377 | 33 | `/g/[token]` | 5 (CSAT-04 재사용) |
| RECV-05 | 선물 받기 — 수능 도시락 | 195:420 | 34 | `/g/[token]` | 5 (CSAT-05 재사용) |
| RECV-06 | 선물 받기 — 준비물 체크리스트 | 195:445 | 35 | `/g/[token]` | 5 (CSAT-06 재사용) |
| RECV-07 | 선물 받기 — 수능 부적 | 195:484 | 36 | `/g/[token]` | 5 (CSAT-07 재사용) |
| RECV-T-01 | 선물 받기 (`부적`) — 도착 | 248:415 | — | `/g/[token]` | 5 |
| RECV-T-02 | 선물 받기 (`부적`) — 정보 입력 | 248:422 | — | `/g/[token]` | 5 (RECV-02 와 같음) |
| RECV-T-03 | 선물 받기 (`부적`) — 메시지 확인 | 248:435 | — | `/g/[token]` | 5 |
| RECV-T-04 | 선물 받기 (`부적`) — 수능 부적 | 248:494 | — | `/g/[token]` | 5 — 수능 체크리스트 포함 여부가 v0.3 10장 ↔ 11장에서 다름 (Q-23 f) |
| — | 공유 랜딩 `/share/[shareId]` | 없음 | — | `/share/[shareId]` | 6 — FUNCTIONAL_SPEC 2장 PR `cc60ab3` 병합 대기 (Q-10) |
| — | 캐릭터 원본 (말, 레이어 분리 벡터) | horse_character_editable 1 (119:60, 옛 페이지) | — | 에셋 | 1 (규격) / 4 |

옛 와이어의 구 #24(D-day 가격) · #26(부적 고르기)은 최종 와이어에 없다.

**최종 와이어에서 읽은 사실 중 계획에 영향을 주는 것**
- **결제 확인이 화면에서 팝업으로 바뀌었다** — 일반 운세는 질문 화면(FORT-02 · 03 · MATCH-03)의 옵션 버튼 → 차감 확인 팝업, 수능운은 CSAT-01 → 팝업, 선물은 GIFT-03 → 팝업, 부적 추가는 FORT-07 → 팝업. 잔액 부족은 팝업 → PAY-02(FORT-04) → 충전 완료 팝업 → 원래 화면. 팝업의 보유 · 사용 · 부족 수량은 서버 견적 값만 쓴다 (P-06 · P-09). Checkout 경로 3개는 삭제했다 (`/gift/checkout` 은 v0.3, 나머지 둘은 CHECKOUT-POPUP). 팝업에 대상 인물 · 구매 후 잔액 · 고지(F-08).
- 원화 결제는 충전 화면(PAY-01 · 02) 하나뿐이다. 결제 수단 4칸 · 동의 체크 · 환불정책 링크가 있다 — 결제 수단 구성은 Q-25 (심사 중 변경 금지라 10/14 전에 정해야 한다).
- 로딩: TODAY-01 · FORT-05 · FORT-08 · CSAT-02 → LoadingScene 하나.
- 결과: CSAT-03 ~ 06 과 RECV-03 ~ 06 이 같은 구성(RECV-03 에 보낸 사람 메시지), FORT-06 · 07 은 요약 + 소제목/내용 반복 → ReadingViewer 하나 (VIEWER). 섹션 순서는 서버 응답을 따른다. 선물 `TALISMAN_ONLY` 는 편지 · 부적 · 수능 체크리스트, `READING_WITH_TALISMAN` 은 결과 전체 (P-03A — 와이어 RECV-T 에는 체크리스트가 없다, Q-23).
- 부적 뷰어: FORT-09 · CSAT-07 · RECV-07 · RECV-T-04 · TALBOX-02 → TalismanViewer 하나. 준비물의 "행운 부적 배경화면으로" → 부적 원본 1080×1920 (T-04).
- **최종 와이어와 팀 결정 (Q-23)** — 기능 명세서 v0.3 으로 로그인 3종 · 오늘의 운세 로그인 · 로그인 직후 정보 입력 · 관계 칩 · 오늘의 운세 결과 항목 · 메뉴가 팀 문서에 들어갔다. 서비스명은 뿌기사주. **남은 것: 여러 명 선물(d) · 발송 실패 링크 공유(e) · `부적` 선물 체크리스트(f) · 수능 결과 구성(g)** — 해결 전에는 팀 문서를 따르고 와이어에만 있는 부분은 만들지 않는다.
- **와이어에 없는 화면 · 상태 (Q-24)** — 보낸 선물 · 재발송, 발송 대기, 부적 창고 ×N · 도감, 결제 실패 · 충전 확인 중, 선물 링크 만료 · 폐기, 결과 생성 실패 · 환급, 판매 종료, 고지 위치, 탈퇴 전 잔액 안내. PG 심사 전에는 기본 HTML 로 상태만 만들고 문구는 `TODO(PD 문구)`.
- 와이어 문구 · 수량은 자리표시다 — "조각"(팀 결정은 "개", P-03), 부적 추가 팝업 "5조각"(P-03 은 10개), 상품 목록 "500조각 + 보너스 80"(PAY-01 본문은 82). 화면 값은 서버 값만 (Q-22).

---

## 2. 팀 구성과 역할

| 약칭 | 구성 | 주 담당 |
|---|---|---|
| **PD** | 기획&디자인 1명 | UI 비주얼(캐릭터 제외), 콘텐츠 작성 · 검수, 약관 문구, 마케팅 |
| **FE** | 프론트엔드 1명 (나) | 전체 화면 구현 + **캐릭터 일러스트** |
| **BE-A** | 백엔드 1 | 공통 응답 · 멱등성, 상품 · 견적, 등껍질 원장, 토스 결제 · 환불, 선물 · 알림톡, 관리자 |
| **BE-B** | 백엔드 2 | 카카오 인증 · 세션 · CSRF, 인물, 만세력 · Liner, 결과, 부적 · R2, 카카오톡 공유 리소스 |

**⚠️ 병목: FE 한 명이 화면 · 팝업 50여 개 + 캐릭터를 동시에 맡는다.**
1. 공통 컴포넌트 재사용(FRONTEND.md 3장)으로 실제 구현 화면 수를 줄인다.
2. 캐릭터 범위는 팀에 확인 중 (T-03 12지 전체, R-02).
3. 이미지 합성(부적 · 공유 이미지)은 BE-B. FE 는 받은 이미지를 붙인다.
4. 프레임 SVG · 부적 틀 · 아이콘은 PD.

---

## 3. 기술 스택

### 3-1. 프론트엔드 — ✅ 확정

Next.js(App Router)+TypeScript · AWS Amplify(10/16 — Vercel 미리보기 병행) · pnpm · Tailwind + CSS 변수 토큰 · 손그림 SVG `border-image` · 글꼴 보류(FONT-HOLD) · vaul/Radix · TanStack Query · openapi-typescript+openapi-fetch · 토스페이먼츠 SDK v2(TOSS-SDK) · RHF+Zod · Zustand(선물 위저드 한정) · dayjs(Asia/Seoul) · `generateMetadata`+Kakao JS SDK · Biome/Vitest/Playwright · Sentry/PostHog. 선택 이유와 API 연결 규칙은 **[`FRONTEND.md`](FRONTEND.md) 1장**.

### 3-2. 백엔드 · 인프라 — ✅ 확정 (팀 문서)

Spring Boot 3 + PostgreSQL, Railway(local · staging · production 분리), API `/api/v1`, 카카오 OAuth + 서버 세션 쿠키(`HttpOnly` · `Secure` · `SameSite=Lax`, 30일), 토스페이먼츠, Cloudflare R2 + 10분 signed URL, springdoc OpenAPI. 결과 문장은 만세력 계산 JSON 을 바탕으로 Liner 가 생성(S-06) — FE 는 AI 를 부르지 않는다. 출처: `PENDING_DECISIONS.md` I-01 ~ I-06, `backend/README.md`.

---

## 4. 손그림 톤 구현 방침 — 보류 (PG-FIRST)

손그림체(STYLE-HAND, 팀 문서 D-01 · D-05). 캐릭터는 직접 그리고, 카드 · 버튼 · 칩 · 입력창 테두리는 손그림 SVG 프레임을 CSS `border-image`(9-slice)로 재사용한다. 도트(픽셀) 처리는 쓰지 않는다. 상세는 **[`FRONTEND.md`](FRONTEND.md) 2장**. PG 심사 요청 전까지 적용하지 않는다.

---

## 5. 공통 컴포넌트 재사용 맵

AppShell · API 클라이언트 · 로그인 가드 · Checkout(PG 결제 / 등껍질 차감) · BusinessFooter · PersonForm · PersonCard · LoadingScene · ReadingViewer · TalismanViewer · GiftWizard · Modal · BottomSheet. 화면별 대응과 제작 단계는 **[`FRONTEND.md`](FRONTEND.md) 3장**.

---

## 6. Phase 상세

작업 표기: `(담당)` · 각 체크박스는 한 세션(반나절~하루) 안에 끝나는 크기. **(FE) 가 아닌 체크박스는 FE 가 추적만 한다.**

### Phase 0 — 결정 · 행정 착수 · 초기 세팅 (9/26 ~ 9/30)

**작업**
- ~~(전원) 결정 회의 — 구 D-01 ~ D-16~~ → 팀 결정사항 문서로 대체 (DECISION-IDS, 7장)
- [ ] (BE-A) PG 상담 · 사업자등록 · 통신판매업 신고 · PG 가입 신청 (O-01 · P-08, R-07)
- [x] (PD) 서비스명 확정 — **뿌기사주** → [ ] (BE-A) 도메인 구매
- [x] (FE) FE 저장소 생성: pnpm + Next.js(App Router) + TS + Tailwind, Biome, PR 템플릿, 브랜치 전략(`main` 보호 + `boyeon`)
- [x] (FE) Vercel 연결: `main` → 스테이징, PR → 미리보기 URL
- [x] (FE) 라이브러리 설치 · 버전 고정: TanStack Query, openapi-typescript/openapi-fetch, React Hook Form, Zod, Zustand, dayjs, vaul, Radix Dialog, Sentry, PostHog, Vitest, Playwright
- [ ] (BE-A · BE-B) BE 프로젝트 · DB · 스테이징 (BE 일정 10/2~10/4) — 골격만 `main` (`a605afa`), 스테이징 재산정 중 (R-06)

---

### Phase 1 — 화면 골격 (9/29 ~ 10/4)

**목표**: 모든 화면에 URL 로 들어갈 수 있는 "빈 앱". 디자인 항목은 PG 심사 요청 후로 보류 (PG-FIRST).

**라우트** — 출처 `FUNCTIONAL_SPEC.md` 2장 = `src/lib/screens.ts` ROUTES (26개 일치)

| 경로 | 화면 # |
|---|---|
| `/login` | 1 |
| `/onboarding` | 2 |
| `/` | 4 |
| `/today` | 6, 7 |
| `/suneung`, `/suneung/checkout`, `/suneung/r/[readingId]` | 15~22 |
| `/fortune/[type]`, `/fortune/[type]/questions`, `/fortune/[type]/checkout`, `/fortune/r/[readingId]` | 8~12 |
| `/talisman/[id]` | 13, 14 |
| `/gift/new` (단계형), `/gift/checkout`, `/gift/done/[orderId]` | 23~29 |
| `/g/[token]` (비로그인) | 30~36 |
| `/me`, `/me/people/new`, `/me/people/[id]` | 3, 37 |
| `/vault` | 38, 39 |
| `/pay/success`, `/pay/fail` | 결제 공통 |
| `/about`, `/terms`, `/privacy`, `/refund` | 심사용 · 법적 고지 |
| (PR 대기) `/wallet` · `/share/[shareId]` | Q-01 · Q-10 — `FUNCTIONAL_SPEC.md` 2장에 넣음(`cc60ab3`). PR 이 `main` 에 병합되면 ROUTES 에 추가 |

**작업**
- [x] (FE) 라우트 표대로 빈 페이지 생성
- [x] (FE) AppShell: 최대 폭 402 가운데 정렬, `100dvh` + safe-area, 헤더, 사이드 메뉴. 실기기: Android Chrome · 카카오톡 인앱 확인, iOS Safari 보류
- [x] (FE) 공통 컴포넌트 껍데기: Button, Card, Chip, FormField, BottomSheet(vaul), Modal(Radix Dialog), LoadingScene(빈)
- [x] (FE) 라우트 오류 · 404 화면: `error.tsx`(AppShell 유지 + Sentry, `retry`) · `not-found.tsx` · `global-error.tsx` `retry` — 문구 TODO(PD 문구). iOS Safari 보류
- [x] (FE) 관측 연결: Sentry, PostHog 페이지뷰 — URL 가리기 포함. 소스맵 업로드는 Phase 7
- [x] (FE) OpenAPI 타입 생성 스크립트(`pnpm api:types`) + TanStack Query 기본 설정 — 클라이언트는 PG-1
- [ ] (FE) 토큰 CSS 변수 + Tailwind 참조 — 디자인 시스템 확정(Figma `iFtLxChBQf3nQWaltdrJ37` `67:66`, 10/9). 팀 문서 D-01 반영(Q-32 · DOCS-TEAM-PR) 후 시작 시점은 사용자 결정 (PG-FIRST)
- [ ] (FE) 프레임 SVG `border-image` 적용 — **보류 (PG-FIRST)**. 에셋 D-03 (10/10 · 10/15)
- [ ] (FE) 폰트 적용 — 디자인 시스템이 정함: 로고 EF 제주돌담 · 제목 OkDanDan · 안내 창원단감아삭 · 본문 오뮤 다예쁨. 웹 배포 라이선스 · 파일(PD) 확인과 D-02 반영(Q-32) 후 — 그 전까지 FONT-HOLD
- [ ] (FE/캐릭터) 캐릭터 에셋 규격 + 뿌기 기본 포즈 — **보류 (PG-FIRST)**, Q-14 · R-02
- [x] (FE) 사이드 메뉴 운세 목록 정리(일반 5종 + 수능운, `navigation.ts` 의 `TODO(D-10)` 취업운 제거) — `8c16730` (유료 운세 5종 + 이벤트 운세 수능운), 줄 높이 `ce4bf89`
- [ ] (FE) 최종 와이어 배치 — 글자 크기 · 굵기 · 위치 · 간격 · 상자(테두리 · 모서리 · 와이어 회색) (LAYOUT-FIGMA, 10/8). 글꼴 · 손그림 프레임 · 캐릭터 · 아이콘 에셋 · 색 토큰 · 와이어 문구는 계속 넣지 않는다
  - [x] (FE) 공통 헤더(64px · 제목 30px) · Card · Button `cta`(327×69) · PersonCard · CSAT-01 (`84cb468`), AppShell `header` 옵션 · HOME-01 로그인 · PAY-01 충전 (`bfb7de1`)
  - [x] (FE) HOME-02 정보 입력 (`1504fe1`), 사업자 정보 푸터 · 등껍질 차감 팝업 · `/about` (`9234cc3`), 결과 카드 · 결제 복귀 화면 · 팝업 줄바꿈 (`573d578`), MY-01 (`8141672`)
  - [x] (FE) HOME-03 홈 · HOME-04 메뉴 (`8c16730` · `ce4bf89`)
  - [x] (FE) FORT-01 정보 확인 (`d7f988b`), FORT-02 · 03 질문 (`74a753e`), MATCH-01 · 02 사람 선택 (`1f6150e`), MATCH-03 질문 (`ddeb780`)
  - [x] (FE) PAY-02 · FORT-04 잔액 부족 충전 — 보유 · 부족(서버 견적) · 추천 상품 미리 선택 (`7c9b186`)
  - [x] (FE) FORT-05 · CSAT-02 분석 로딩 — 구매 대기 동안 팝업을 전체 화면으로 (`36d4284`)
  - [ ] (FE) FORT-06 · 07 결과 배치 — 요약 섹션 구분(Q-05) · 저장 · 공유(G-11) · 부적 확인하기 · 부적 만들기(부적 규칙) 후
  - [x] (FE) CSAT-03 결과 카드_수정안 — 수능 8장 카드 넘겨 보기(진행 점 · 1번 카드 목록 · 이전/다음 · 손가락 넘기기, 347:312 · 359:179) (`f8148ea`)
  - [ ] (FE) 나머지 화면은 그 화면을 만들 때 같이
  - 확인 방식: 웹 대화가 원격 코드를 받아 가짜 구현으로 띄우고 402×874 화면을 찍어 와이어와 대조한다 (10/8 부터)

---

### PG 심사 트랙 — 백엔드 연결 + 직접결제 + 심사 노출 요건 (10/3 ~ 10/12 → 진행 중) 🔴 최우선

**목표**: 운영 도메인의 실제 판매 사이트에서 로그인 → 운세 상품 결제하기 → PG 결제창이 열리고(10/16 전: 충전 상품 선택 → 토스 결제창), 심사가 보는 사업자 정보 · 상품 · 약관이 노출된 상태를 10/12 까지 만든다. 카드사 심사 요청은 10/14 전후 (카드사 심사 최대 14일, 10/29 운영 실결제 확인에서 역산).

**10/14 저녁 — 일정 다시 잡음 (R-10)**: 10/14 심사 요청은 못 했고, 일반 심사(계약 완료 후 최대 10 ~ 14영업일)로는 10/31 출시에 결제가 열리지 않는다. 사용자 방향은 토스 **퀵오픈** — 개인사업자 · 토스 첫 가입이면 연동 즉시 결제, 카드사 심사는 따로, 정산은 심사 결과까지 보류, 반려 시 중단 · 결제 취소 가능. 10/15 토스 통화 후 확정하고, 안 되면 계좌이체 수동 충전으로 간다. 어느 쪽이든 아래 심사 요건은 맞춘다. 일정 안: 10/15 통화 · 신청 · 통신판매업 신고 → 10/16 ~ 10/20 BE 운영 배포 · 키 · 상품 활성화 → 10/20 ~ 10/22 운영 결제 · 소액 실결제와 환불 · 실기기 → 10/22 ~ 10/27 정책 · `/about` · 푸터 · 이미지 반영 → 10/28 18:00 프리즈. 담당자별 할 일은 claude.ai 문서 "뿌기사주 PG 퀵오픈 준비 (10/14)".

**10/16 — 직접결제 전환 (DIRECT-PAY)**: 등껍질 충전 · 차감을 없애고 운세 한 건씩 원화로 바로 결제한다(PD 정책 v4 · BE PR #49 · FE PR #51). 심사 상품은 충전이 아니라 운세(사주만 1,800원 · 사주 + 행운 부적 2,500원 · 수능운 3,000원 — 서버 값), 한 번 결제 5만 원 미만. **PG사는 아직 선정 전**(BE 답 10/16) — FE 결제창은 토스 V2 형식으로 만들어 두었고, 다른 PG 로 정해지면 결제창 포트 구현만 바꾼다. 실제 승인 · 취소 확인은 PG 선정 · 계약 · 테스트 키 뒤. 호스팅은 백 + 프론트 모두 AWS Amplify 로 옮길 예정(R-11). 팀 확인 요청은 claude.ai 문서 "뿌기사주 팀 확인 요청 (FE, 10/16 직접결제)".

**원칙 (PG-FIRST)**: 디자인 요소(토큰 · 프레임 · 글꼴 · 캐릭터 · 꾸밈)는 넣지 않는다. 화면은 AppShell + 기본 HTML 로, 서버 연결과 결제 흐름의 정확성만 본다.

**심사 요건 (토스페이먼츠 안내 기준)**
- 결제창이 정상 노출되고 결제창 안에 카드사가 보일 것. 테스트 환경뿐 아니라 실제 판매 사이트에 연동돼 있을 것.
- 판매 예정 상품 1개 이상 노출. 비실물 상품은 제공기간 · 환불정책.
- (10/14 체크리스트 대조) 품절 · 샘플이 아닌 바로 살 수 있는 상품, 상품 금액 = 결제 금액, **상품 이미지**(빠짐 · 반복 · 깨짐 없이), 고객센터 연락처 · 이메일. **충전 서비스는 1일 최대 결제 금액 5만 원 미만** — 50,000원 상품 · 하루 누적 한도는 토스 답 (R-01).
- 푸터 사업자 정보: 상호 · 대표자 · 사업자등록번호 · 사업장 주소 · 유선번호 · 통신판매업 신고번호 — 사업자등록증과 글자까지 같게. 신고번호가 없으면 국민카드 심사 제외.
- 회원 구매면 심사용 테스트 계정 전달 (R-05).
- **심사 중 수정 금지**: 사업자 정보 · 전화번호 · 상품 카테고리 · 결제수단 · 판매 상태. 바꾸면 반려 후 재심사.

**작업** (번호 = 의존 순서. 막힌 체크박스는 건너뛰고 풀린 것부터)
- [ ] (FE) **PG-1 백엔드 연결 기반** — `/api` rewrites 를 스테이징에 연결(`API_PROXY_TARGET`), OpenAPI JSON → `pnpm api:types` → adapters(ADAPTER-HTTP — `http.ts` 요청 + zod 응답 검사, openapi-fetch 미사용), 공통 응답 `{data, traceId}` · 오류 `code` 처리(알 수 없는 code 는 status), `Idempotency-Key` 헬퍼(같은 명령의 재요청은 같은 키 · 같은 본문), CSRF — `GET /session` 의 `csrfToken` 을 `X-CSRF-Token` 으로, `403 CSRF_FAILED` 면 세션 1회 재조회 후 1회만 재시도 (Q-02 해결), API 경로 가리기(`maskUrl`). 선행: BE 스테이징 주소 · OpenAPI (R-06 · Q-18). BE 골격(`a605afa`)은 `main` 에 있으나 업무 API · 공개 OpenAPI 없음 → 10/9 BE-A PR #17 로 OpenAPI 파일 `docs/openapi/api-v1.json` (Q-18). 스테이징 주소는 아직 없음 (R-06)
  - [x] (FE) API 코어 — 공통 응답 · 오류 껍데기 런타임 검사, `classifyApiError`(code 우선 · status), CSRF 1회 재시도(`CsrfSource`), 멱등 키 · 본문 고정, 포트 모드 선택 · 운영 mock 차단 (`ffe1080`, MOCK-PORT)
  - [x] (FE) 테스트 간헐 시간 초과 완화 — 동시 워커 50% · 한도 15초 (`ebb16b0`)
  - [x] (FE) OpenAPI 생성 타입 — `pnpm api:types` 가 `docs/openapi/api-v1.json` → `src/types/api.d.ts`(손수정 금지 · biome 제외), 생성 결과 동기화 테스트 · 생성 타입은 `adapters/` 에서만 쓰는지 검사 (`2c9a17b`)
  - [x] (FE) adapter 틀 (ADAPTER-HTTP) — 오행분석 `POST /fortune/basic` (`e6a603a`), 충전 `GET /wallet` · `POST /top-up-orders` (`c46bfa7`), 수능운 `POST /quotes/fortune` · `GET /quotes/{quoteId}` · `POST /reading-purchases` (`1fcd9c0`), `maskUrl` 이 `/api/` 경로의 UUID 를 가림 (`1fcd9c0`). 요청은 `http.ts`, 응답은 zod 로 필수 · 정확한 모양 검사, 생성 타입과 이름 대조는 객체 리터럴 `satisfies`
  - [x] (FE) 나머지 adapter — 충전 상품 목록 · 승인 · 주문 조회 (`b7af91a`), 세션 · 인물 (`78952fe`), 운세 상품 목록 (`e781919`), 결과 `GET /readings/{readingId}` (`a352829`), 구매 상태 조회 `GET /reading-purchases/by-quote/{quoteId}` (`9f1366e`), 일반 운세 5종 견적 · 구매 — 운세별 경로 · 요청 전 선택 검사 · 응답 질문 대조 (`2f505f7`, Q-26 해결 · SUNEUNG-ADAPTER-FIRST 해제)
  - [x] (FE) `ports/index.ts` 진짜 모드 연결 — 모든 포트, 진짜 세션은 탭 하나에 하나(`getRealSession`)로 같은 CSRF 토큰 (`e781919`)
  - [ ] (FE) `API_PROXY_TARGET` 스테이징 · 실제 확인 — 스테이징 주소 대기 (R-06). 로컬 진짜 모드는 하지 않기로 함(10/12, Q-39 4)
  - 진행: Idempotency-Key 헬퍼 완료 (`353d9b8`, 10/3) — `src/lib/api/idempotency.ts`: 구매 의도당 무작위 UUID 키 하나 + 생성 시점에 본문 직렬화 고정, 충전 승인은 주문 ID(UUID)를 키로 (CONFIRM-KEY). 공통 응답 · 오류 code · CSRF 재시도 · API 경로 가리기는 위 API 코어 · adapter 틀로 끝
- [ ] (FE) **PG-2 카카오 로그인 · 세션** — `/login`(HOME-01, 카카오 먼저 — 네이버 · 구글은 A-01 부분 확정, BE-B 지원 · 개발 범위 확정 후). 로그인 직후 본인 정보가 없으면 `/onboarding`(A-03), 비로그인으로 홈 기능을 누르면 로그인(A-02) → `GET /api/v1/auth/kakao/authorize?returnTo=`(내부 경로만), `GET /session`, 로그인 필요 화면 가드 + 원 경로 복귀, OAuth 취소 · 세션 만료 처리, 로그아웃(`POST /auth/logout`, CSRF), 로그아웃 시 구매 선택 `sessionStorage` 삭제 (PURCHASE-RESTORE). 선행: PG-1, BE-B OAuth · 세션 · CSRF, 카카오 리다이렉트 URI (R-04)
  - [x] (FE) 가짜 구현 범위 — `safeReturnTo`(내부 경로만 · 로그인/온보딩 고리 · `/api` 차단), 세션 포트 · 가짜 세션 시나리오 4종 (`728e9fb`), 로그인 가드 `RequireSession`(새로 받은 세션으로만 판단) · `/login` 카카오 버튼 · `/wallet` 연결 (`84b5390`)
  - [x] (FE) 로그아웃 UI — MY-01 (`8141672`), 로그아웃 후 처음 화면 (`71296ad`, 세션 캐시를 무효화하면 가드가 `/login` 으로 먼저 보낸다)
  - [x] (FE) 로그인 제공자 — 세션 포트 `startLogin(provider, …)` · 가짜 카카오 · 네이버 · 구글(모르는 값은 던짐), HOME-01 화면은 PG 심사까지 카카오만 · 로고 자리 (`4717e77`, LOGIN-3 개정)
  - [ ] (FE) 네이버 · 구글 버튼 — PG 심사 후 (LOGIN-3 개정, 와이어 224:1087 · 195:781). 진짜 경로는 BE-B
  - [x] (FE) 진짜 세션 adapter — `GET /session`(평평한 응답, 로그인이면 `userId` · `hasPrimaryPerson` · `csrfToken` 필수) · 카카오 `authorize?returnTo=` 이동(KAKAO 만) · 로그아웃(토큰 비움) · CSRF 출처 (`78952fe`, BE-B PR #23 초안 정리). OAuth 취소 · 실패 안내는 BE-B 임시 문구 (Q-40 4)
  - [x] (FE) HOME-01 PD 문구 — 「카카오로 계속하기」 · 안내 문장 · 취소 · 실패 문구 (`94f697f`, FUNCTIONAL_SPEC v0.4 3장 · Q-43)
  - [ ] (FE) 진짜 로그인 실제 확인 · 세션 만료 처리 — 스테이징 · 카카오 callback 프론트 주소 등록 후 (R-04 · R-06). `/pay/success` 로그인 필요 링크의 `returnTo` (`TODO(PG-2)`)
- [ ] (FE) **PG-3 직접결제 플로우 (DIRECT-PAY, 10/16)** — 견적 → 결제하기 팝업(상품 · 대상 · 옵션 · 결제 금액은 서버 견적, 필수 동의 · 안내 4줄) → `POST /direct-payment-batches`(구매 의도당 `Idempotency-Key`) → PG 결제창 → `/pay/success?flow=direct` 에서 복귀 값 그대로 `POST …/{orderId}/confirm`(키 = 주문 ID) → 주문 `PAID` 일 때만 `POST …/{orderId}/reading-fulfillment`(생성 키는 복원값) → `FULFILLED` + `readingId` 일 때만 「준비가 끝났어요!」 · 「결과 보러 가기」. 결과 불명은 주문 조회 2초 간격 최대 30초, `REVIEW_REQUIRED` · 충돌은 문의, 생성 실패는 문의(Q-65). 선행: PR #49 병합 · OpenAPI(Q-68) · 스테이징(R-06) · PG사 선정(R-10)
  - [x] (FE) 직접결제 포트 · 가짜 구현 — 주문 · 승인 · 조회 · 결제 뒤 생성, 시나리오 6종(paid · pending_then_paid · stuck_pending · confirm_lost · review_required · rejected), 금액 · 키 · 선택 대조 (`7c02326`)
  - [x] (FE) 결제하기 팝업 `DirectCheckout` — 등껍질 차감 팝업을 대체(CSAT-01 · FORT-02 · 03 · MATCH-03), 복원 v5(주문 키 · 주문 ID · 생성 키), 다시 열면 주문 조회로 이어 가기 · 확인 필요 안내 (`33d6a83`)
  - [x] (FE) 복귀 화면 — 승인 확인 · 결과 불명 조회 · 결제 뒤 생성 · 「결과 보러 가기」(누를 때 이동 · 복원값 삭제) · 확인 필요 · 생성 실패 문의, `/pay/fail?flow=direct&return=` 의 「다시 결제하기」 (`06f6dca`)
  - [x] (FE) 진짜 adapter — 주문 · 승인 · 조회 · 결제 뒤 생성, 운세 상품 가격 원화만 (`274ebf8`, PR #49 `c2a1392` 컨트롤러 기준 — 생성 타입 대조는 Q-68 뒤)
  - [x] (FE) 충전 화면 · 복귀 주소 닫기 — `/wallet` 삭제, `/pay/success` · `/pay/fail` 은 `flow=direct` 만, PAY-01 · 02 · FORT-04 는 만들지 않는 화면 (`cf12a00`)
  - [ ] (FE) 수능운 결제 뒤 생성 — BE 수능운 전용 `POST …/{batchId}/suneung-fulfillment` `{quoteId, personId}` (Q-64 답, 계약 반영 뒤). 지금 진짜 adapter 는 요청 전에 던진다
  - [ ] (FE) 직접결제 생성 타입 대조 — 공유 OpenAPI 반영 뒤 `pnpm api:types` · adapter `satisfies` (Q-68, OPENAPI-FE-SYNC)
  - [ ] (FE) 스테이징 실제 확인 — 로그인 → 일반 운세 결제 → 결과 → 새로고침 복귀 · 결제창 닫기 · 실패 복귀 · 실기기 3곳 (R-06 · PG 선정 뒤)
  - [ ] (FE) 옛 충전 코드 제거 — 충전 포트 · adapter · 가짜 · `confirmTopUp`(공용 조회 간격은 옮김) · `retryProduct` · 결제창 `top_up` 경로 · MockPanel 충전 시나리오 (BE 가 충전 API 를 닫을 때)
  - [ ] (FE) 선물 · 행운 부적 추가 직접결제 — BE `direct-gift-batches` · `talisman-fulfillment` (선물은 Q-54 결정, 화면은 PG 결제 확인 뒤)
- [ ] ~~(FE) **PG-3 충전 결제 플로우**~~ — 10/16 DIRECT-PAY 로 대체. 아래 기록은 남긴다. 충전 화면 `/wallet`(PAY-01 · PAY-02 · FORT-04, 결제 수단 · 동의 체크는 Q-25 전까지 정하지 않는다)(잔액 `GET /wallet` · 충전 상품 `GET /products?category=TOP_UP`, 서버 값만) → 주문 생성(`POST /top-up-orders`, 구매 의도당 `Idempotency-Key` 하나) → 토스 결제창(SDK v2) → `/pay/success` 에서 PG 복귀 쿼리를 그대로 `POST /top-up-orders/{orderId}/confirm` → **주문 `CREDITED` 일 때만 충전 완료 · 잔액(서버 값) 표시**. `PAID` · `processing: true` 는 처리 중, 결과가 불명확하면 `GET /top-up-orders/{orderId}` 를 2초 간격 최대 30초 조회 → 그래도 미확정이면 확인 중 안내 + 주문 확인 버튼, 새 결제 · 새 키로 유도하지 않음 (TOPUP-DONE). `/pay/fail` · 결제 취소 처리. 선행: PG-2, `/wallet` 라우트(Q-01 — 팀 문서 PR `cc60ab3` 병합), BE-A 충전 API(R-06 재산정), `processing` 계약(Q-17), 결제 수단 구성(Q-25), 토스 테스트 클라이언트 키(R-09) · SDK 설치(사용자)
  - [x] (FE) 가짜 구현 범위 — 충전 포트 · 가짜 서버 시나리오 6종 · 승인 확인 흐름(`e0ce4dd`), `/wallet` · `/pay/success` · `/pay/fail` 화면(`03f01fa`), 가짜 상품 구성 판매 6 + 비활성 1(`816b130`). 로컬 시나리오 6종 확인 (10/4)
  - [x] (FE) PAY-02 · FORT-04 잔액 부족으로 왔을 때 — 저장된 견적의 보유 · 부족 표시, 추천 충전 상품 미리 선택, 만료 · 없는 견적은 일반 화면 (`7c9b186`)
  - [x] (FE) 판매 상품 없음 안내 — 진짜 API 첫 연결 때 전부 비활성일 수 있음(BE-A FE_COMPATIBILITY) (`6526d3c`), 주문 내용 상자(고른 상품의 서버 가격) · 결제하기 하단 CTA (`bfb7de1`)
  - [x] (FE) 진짜 충전 adapter 1차 — `getWallet` · `createOrder`(받은 키로 `createKeyedCommand`, 예정 지급량은 포트에 넣지 않음) (`c46bfa7`). 상품 목록(Q-28) · 승인 · 주문 조회(Q-17)는 계약 대기라 근거와 함께 던진다 → 2차에서 해결
  - [x] (FE) 진짜 충전 adapter 2차 — 상품 목록 · 승인 · 주문 조회, `grantedShellAmount` · 충전 `nextAction`(모르는 값 UNKNOWN), CREDITED 인데 지급량 · 잔액이 null 이면 계약 위반 (`b7af91a`)
  - [x] (FE) 토스 V2 결제창 — 위젯 `renderPaymentWindow` → `requestPayment` redirect, `customerKey: ANONYMOUS`, 클라이언트 키 `NEXT_PUBLIC_TOSS_CLIENT_KEY`, 가짜 모드 스위치 `NEXT_PUBLIC_MOCK_PAYMENT_LAUNCHER` (`ecaaba4`, TOSS-LAUNCHER-SWITCH · TOSS-ANONYMOUS). 1단계 로컬 시험(문서용 테스트 키) 통과 — 결제창 표시 · 결제 · `/pay/success` 복귀 (10/11)
  - [x] (FE) PAY-03 지급 수량 — 승인 · 주문 응답의 `grantedShellAmount` 만, 숫자만 (`15d98ff`, Q-17 해결)
  - [x] (FE) PAY-01 필수 동의 · 안내 상자 5줄 · 동의 전 결제하기 비활성 · PAY-06 상품 없음 · 지갑 확인 안내 COMMON-04 (`9be6276`, Q-25 · Q-42 · Q-51)
  - [x] (FE) PAY-03 ~ 05 PD 문구 · 취소 / 실패 구분 · 다시 결제는 서버 `RETRY_PAYMENT` 일 때만 (`d777e5a`, Q-33 · Q-50). 토스 사유 문구는 Q-58
  - [x] (FE) 다시 결제 시 상품 유지 — 토스 `failUrl` 에 `?product=` (`df940fb`, TOPUP-RETRY-URL)
  - [ ] (FE) 토스 2단계(스테이징 실제 승인 · 지급) · 결제창 닫기 확인(테스트 B) · `failUrl` 의 `product` 쿼리 실기기 확인(카드 · 간편결제 앱 취소 → `/pay/fail?product=…&code=…`, `?` 두 번 없음) · 실기기 3곳
  - [x] (FE) PAY-03 유료 · 보너스 내역 칸 — CREDITED 뒤 지갑 `paidBalance` · `bonusBalance` 를 다시 받아 그대로, 보유 합계와 맞추지 않음, 가짜 지갑 유료 · 보너스 분리(가짜 차감은 보너스 먼저 — 실제는 서버가 만료 빠른 순) (`4182746`, BE-A FE_REQUEST_RESPONSES 1장)
  - [x] (FE) 공유 OpenAPI 서버 export · 사용자 환불 신청 제거 · `PARTIALLY_REFUNDED` 알려진 상태(완료 아님) · 환불 조회(`getRefund`)만 유지 (`78c8b10`, Q-41 2 · 3)
  - [x] (FE) `409 WALLET_REVIEW_REQUIRED` — 구매 팝업 · 충전 화면에서 다시 시도 대신 문의 안내 · 버튼 잠금 (`3f62019`, Q-41 5). 문구 · 문의 채널 PD (Q-38 g)
  - [ ] (FE) 충전 내역(`GET /top-up-orders`) · 주문 상세 환불 상태(`REFUND_PENDING` · `PARTIALLY_REFUNDED` · 환불 GET 의 실제 환불액 · 완료 시각) · 고객센터 안내 — 와이어 없음, PD 배치 · 문구 후 (Q-41 6). 충전 `nextAction` CONTACT_SUPPORT 즉시 안내 문구 (PD)
  - [x] (FE) PAY-01 수정안(301:128) 배치 — 상품 카드(서버 값 · 고른 카드 강조) · 안내 상자 자리 · 동의 자리, 결제 수단 칸 없음, 주문 내용 상자 삭제 (`b5f7f3a`). 문구 · 동의 필수 여부는 Q-33 · Q-25 · Q-21 확정 후
  - [x] (FE) PAY-03 충전 완료 · PAY-04 결제 확인 중 · PAY-05 결제 실패 · 취소 배치 — 공통 `PayResultLayout`, 기존 흐름(TOPUP-DONE) 그대로 (`5f61f94`). 지급 수량은 `15d98ff`, 유료/보너스 내역은 `4182746`, 취소 · 실패 문구 · 상품 유지는 Q-33
- [ ] (FE) **PG-4 심사 노출 요건 (스타일 없이)** — 사업자 정보 푸터(값은 한 파일, 사용자가 준 값만, 미정은 `TODO(O-01)`), `/about` 상품 소개(심사 상품의 이름 · 가격(서버 값) · 제공 내용 · 제공 기간 · 환불 요약 · 구매 진입), `/terms` · `/privacy` · `/refund` 본문(PD 원고 그대로). 선행: 사업자 정보(R-07 · R-08), 심사 상품 = 충전(R-01 부분 — 카테고리 · 제공기간은 토스 상담 대기), 충전 환불정책 문구(Q-21 — PD 초안 수령, 토스 상담 후 10/10 확정 원고. 확정 전에는 넣지 않는다), PD 원고
  - 진행: 푸터 틀 완료 (`d42ba99` · `22f9712` · `4453ba9`, 10/3) — `src/lib/business.ts` 에 8개 항목(토스 심사 6 + 전자우편주소 · 호스팅 제공자), 값이 없으면 "(미정)", AppShell 하단 BusinessFooter + 약관 3종 링크. `/about` 틀 완료 (`7aba2e1`, 10/8) — 로그인 불필요 · 정적, 활성 충전 상품만 서버 값 그대로, 소개 · 제공 기간 · 환불 요약은 자리만. 남은 것: 값 8개 · 공정위 사업자정보 공개페이지 링크(R-07), `/about` 원고(R-01 · P-04 · PD), 약관 3종 본문, `GET /products` 비로그인 호출 가능 여부(Q-28)
  - [x] (FE) INFO-01 `/about` · INFO-02 정책 페이지 · COMMON-01 푸터 와이어 배치 (302:129 · 302:155 · 302:175) — 원고 · 값 없이 틀만 (`5e37d52` · `aa2296e` · `78aa7ad`)
  - [ ] (FE) 정책 원고 · 시행일 — PD 확정본(`docs/policies/`, POLICY-DRAFTS)을 INFO-02 틀에, `/about` 소개 · 제공 · 유효기간 · 환불 · 고객센터 원고 (Q-21 · Q-36 · R-01 · R-07)
    - [x] 표시 구조 — 정책 3페이지가 빌드 때 `src/content/policies/{terms,privacy,refund}.md` 를 읽어 시행일 · 목차 · 본문을 그린다. 파일이 없으면 빈 자리. 정해진 마크다운만 받고 `[ ]` · `○` · 링크 · HTML · 인용 · 코드가 있으면 빌드 실패 (`7fb0658`, Q-56)
    - [x] 원고 — v3 이용약관 · 환불정책(`10e26c7`, 10/15) → **v4**(PD 10/15, 직접결제 · 시행 10/31) 이용약관 · 환불정책(`362d99d`)
    - [ ] 개인정보처리방침 v4 — 국외 이전 · 위탁 표의 서버 리전 · R2 위치 빈칸, Railway → AWS (Q-44 · R-11)
    - [x] `/about` — 운세 상품 · 가격(서버 값) · 결제 한도 · 제공 방법 · 환불 요약(환불정책 v4 문장 그대로) · 고객센터 이메일 · 전화 (`45d2422`). 서비스 소개 원고는 PD
  - [ ] (FE) 상품 이미지 — 토스 체크리스트 요건(10/16 부터 심사 상품은 운세). 디자인 에셋 후 (PD)
  - [x] (FE) 사업자 값 4개 — 상호 · 대표자 · 사업자등록번호 · 주소를 사업자등록증 그대로 `src/lib/business.ts` 에 (`5bee76c`, R-08 해결)
  - [x] (FE) 전자우편 `ppugisaju@gmail.com` (`b33cfb5`), COMMON-01 「문의는 이메일이 가장 빨라요.」 (`94f697f`)
  - [x] (FE) 유선번호 `010-7429-0716` (`c4b1e8d`, 10/15)
  - [x] (FE) 통신판매업 — 신고하지 않고 「면제 (직전연도 통신판매 거래 50회 미만)」 표기 (10/16 사용자 결정, R-07 — 이 세션 문서 커밋 앞의 코드 커밋)
  - [ ] (FE) 호스팅서비스 제공자 상호 — AWS Amplify 이전 뒤 법적 상호 (Q-44 · R-11). 10/16 FE 는 Amplify 로 옮김 — AWS 계정의 계약 법인명(결제 화면)을 사용자가 확인해 주면 넣는다. 카드사 심사 요청 전에
- [ ] (FE) **PG-5 운영 도메인 실기기** — 로그인 → 충전 결제창 열림 · 카드사 노출 · 복귀를 Android Chrome · 카카오톡 인앱에서 확인 (iOS 는 R-03), 심사용 테스트 계정 경로 확인 (R-05)
- [x] (BE-A · BE-B) BE 프로젝트 골격 — 공통 응답 · 오류 · traceId · 최소 보안 (`a605afa`, `main`, 10/3). 스테이징 · 업무 API 없음
- [ ] (BE-A) 상품 · 견적 · 지갑 · 충전 주문 · 토스 승인 API (스테이징) — 제공일 재산정 중 (R-06). 도메인 타입 · 내부 계약 · 확정 정책 골격은 `main` `2c682e0` (10/4, `backend/docs/BACKEND_A_SCAFFOLD.md`) — 업무 API · DB · 토스 연동 없음. → 10/6 PR #14(`a064ea6`): 상품 · 견적(30분) · 견적 잔액 계산(추천 충전) · 지갑 잔액 · 내역 조회를 DB · 서비스로 구현, 충전 상품 6종(50,000원 80 · 580) 비활성 등록 — HTTP API 없음 (`backend/docs/FE_COMPATIBILITY.md`). → PR #17 · #19 · #21 로 지갑 · 충전 주문 · 상품 목록 · 승인 · 주문 조회 · 토스 승인 HTTP API 가 `main`(10/11), PR #24 로 충전 환불 · 결제 대사 · 지갑 운영(10/12, 기능 꺼짐 — Q-41). 스테이징 없음 (R-06)
- [x] (BE-B) 카카오 OAuth · 세션 · CSRF — `main` PR #23 (`7bac2a1`, 10/12). 스테이징 배포 · 카카오 callback 등록은 R-04 · R-06
- [ ] (BE-A) 토스페이먼츠 가입 · 심사 요청 (10/14 전후) → **10/15 대표가 퀵오픈 신청** (R-10, 개인사업자는 대표 본인만 신청). 시크릿 키는 BE 환경 변수에만, 클라이언트 키는 FE(Amplify `main` 환경 변수)
- [ ] ~~(BE-A) 충전 상품 활성화 마이그레이션~~ — 10/16 DIRECT-PAY 로 필요 없음. 대신 (BE-A) 직접결제 PR #49 병합 · 운세 상품 원화 가격(`V202610151400`) · 수능운 전용 결제 뒤 생성(Q-64) · 결제 내역 목록(Q-66) · OpenAPI(Q-68) · P-07 팀 문서(Q-67)
- [ ] (BE-A · 대표) PG사 선정 · 계약 · 테스트 키 (R-10 — BE 답 10/16: 아직 선정 전)
- [x] (FE) AWS Amplify 이전 준비 — 운영 mock 차단 · Sentry/PostHog 키 검사 · 관측 환경 이름을 배포 단계 변수 `NEXT_PUBLIC_DEPLOY_ENV` 로(`3fe6072`, DEPLOY-ENV), 루트 `amplify.yml`(`63b0f61`), 미리보기(가짜 모드) `boyeon` 브랜치, Next.js 16 SSR 빌드 확인 (R-11, 10/16). rewrites(`/api` → BE)는 BE 주소가 나온 뒤 확인
- [ ] (사용자) 도메인 `ppugi-saju.kr` · Route 53 · Amplify 사용자 지정 도메인 — 연결 끝(10/16), `main` 빌드는 PR 병합 뒤. `main` 환경 변수 덮어쓰기(`production` · `real`) 확인, BE 주소가 나오면 `API_PROXY_TARGET`
- [ ] (BE-A · BE-B) 운영 배포 · `FRONTEND_BASE_URL` · 카카오 운영 callback · 토스 웹훅 주소 (R-04 · R-06) — 10/16: AWS Elastic Beanstalk prelaunch 문서(PR #56), 주소 `https://api.ppugi-saju.kr` · `FRONTEND_BASE_URL=https://ppugi-saju.kr` · redirect `https://ppugi-saju.kr/api/v1/auth/kakao/callback` 전달, ACM 확인 레코드 수령. 심사용은 prelaunch 가 아니라 PG 테스트 키 설정
- [ ] (BE-A · FE) 심사용 결제 경로 파일 (R-05) — 운영 배포 후
- [ ] (대안일 때만) (BE-A) 관리자 수동 지급 API · (FE) 입금 안내 · 대기 화면 — 계좌이체 수동 충전 (R-10)

**완료 기준 (검증)**
- 운영 도메인에서 로그인 후 운세를 결제하면 PG 결제창이 열리고, 테스트 결제 후 `/pay/success?flow=direct` 가 주문 `PAID` 를 확인한 뒤에만 결제 뒤 생성을 보내고, `FULFILLED` + `readingId` 에서만 결과로 가는 버튼을 보인다. 지연 · 결과 불명은 확인 중으로 남고 새 결제를 권하지 않는다 (테스트). (10/16 전 기준: 충전 주문 `CREDITED`)
- 같은 구매 의도의 재요청(더블 탭 · 새로고침 · PG 복귀 · CSRF 재시도)이 같은 `Idempotency-Key` · 같은 본문을 쓴다 (테스트).
- 승인 실패 · 결제 취소 · 4xx · 5xx 가 조용히 넘어가지 않는다 (오류 화면 + Sentry, 테스트).
- 모든 화면 하단에 같은 사업자 정보가 나온다 (한 파일 출처). `/about` · 약관 3종이 운영 도메인에서 열린다.
- Android Chrome · 카카오톡 인앱 확인. iOS 는 보류 목록에 남긴다.

**게이트**: 10/20(화)까지 PG 승인이 안 나면 대안 판단 (BE-A · PD). 심사 요청 후에는 푸터 · 전화번호 · 심사 상품 · 카테고리 · 결제수단 · 판매 상태를 바꾸지 않는다.

---

### Phase 2 — PG 트랙에 흡수

옛 Phase 2(결제 파이프라인 테스트 키 E2E)의 FE 작업은 PG-1 · PG-3 으로, BE 작업은 BE-A 일정(`BACKEND_ROLE_SPLIT.md` 4.4)으로 옮겼다. 환불(부분 · 전체 취소)은 BE-A 10/24~10/27, FE 화면은 Phase 6 마이페이지 내역. → BE-A 가 10/12 PR #24 로 충전 전액 환불(7일 · 전부 미사용, 기능 꺼짐)을 넣었다 — FE 화면 여부 · 위치는 Q-41 (와이어 없음). → 10/13 PR #27 로 정책이 바뀌었다: 충전 건별 남은 유료분 비례 환불(수수료 0, 7일 이후도), 고객센터 접수 → 관리자 API 실행, 사용자 환불 POST 제거. FE 는 신청 화면을 만들지 않고 조회 · 표시만(Q-41 6)

---

### Phase 3 — 인물 정보 · 온보딩 (10/6 ~ 10/14)

**목표**: 로그인한 사용자가 본인과 저장한 사람(최대 10명)의 정보를 저장한다. 본인 정보가 없으면 로그인 직후 입력한다 (A-03 · A-07).

**작업**
- [ ] (FE) PersonForm (React Hook Form + Zod): 이름/닉네임 · 양력/음력 · 윤달(음력일 때만) · 생년월일(1900-01-01~오늘 KST) · 시 · 분 또는 시간 모름 · 성별(남성/여성/선택하지 않음) · 관계(타인) · 타인 정보 권한 확인 (F-01 · A-04 ~ A-07, O-03). 오류는 `fieldErrors` 필드로 이동
  - [x] (FE) 검증 규칙 `src/lib/person/schema.ts`(`1a7fe39`, 한 번 제출에 모든 오류 보정 `8f94cde`) · 본인 입력 폼 `src/components/PersonForm.tsx`(`8f94cde`) — 음력 30일 텍스트 입력, 윤달은 음력만
  - [ ] (FE) 타인 입력(관계 · 타인 권한 확인 체크) — 권한 확인 문구 PD 확정 후 (MY-02)
- [ ] (FE) 내 정보 저장(HOME-02) · 다른 사람 추가 · 수정(MY-02) — `POST/PATCH /people`, `409 PERSON_LIMIT_EXCEEDED` · 중복 경고 후 저장 허용
  - [x] (FE) 가짜 구현 범위 — 인물 포트 · 가짜 계정 공유(`75b774c`), `/onboarding`(HOME-02 · A-03, 저장 후 returnTo, `8f94cde`)
  - [x] (FE) 진짜 인물 adapter — 관계 대응(엄마 · 아빠 → FAMILY, 애인 → LOVER, 직접 입력 → OTHER, Q-35 2), 시간 모름이면 `birthTime` 생략, 목록은 본인 먼저 (`78952fe`, BE-B PR #23 초안 정리). 직접 입력 관계 이름은 보내지 않는다 (`TODO(A-07 · BE-B)`)
  - [ ] (FE) MY-02 추가 · 수정, 중복 경고 — 권한 확인 문구(PD) · 본인 수정 경로(Q-29) 후
- [x] (FE) PersonCard + "저장된 다른 사용자 불러오기" (FORT-01 · CSAT-01 · MY-01 · MATCH-02) — `0e5cb37`, 배치 `84cb468`. 관계 표시(A-07)는 MY-02 후, 생년 표시는 인물 요약 필드 대기(BE-B), 본인 수정 경로는 Q-29
- [x] (BE-B) people API · 암호화 · 소유권 · OTHER 10명 제한 — `main` PR #20 (10/11)

**완료 기준**: 실기기에서 본인 · 타인 저장 · 수정, 10명 초과 거부, 시간 모름 저장.

---

### BE-B 계산 · 생성 API 연동 (10/9 추가, `main` PR #15 `08fccad`)

**배경**: BE-B 가 결제 전 오행분석(`POST /fortune/basic`) · 일반 운세 카테고리별 견적 · 구매(`POST /quotes/fortune/{category}` · `POST /reading-purchases/{category}`) · 결과(`GET /readings/{category}[/{id}]`) 컨트롤러와 API_SPEC 5 · 8장을 올렸다. 스테이징 · 공개 OpenAPI 는 없다(springdoc 은 local 프로필만). 계약 차이는 Q-34 · Q-35.

**작업**
- [x] (FE) OpenAPI JSON 받기 → 생성 타입 · `adapters/` (Q-18 · Q-35 5) — `2c9a17b` · ADAPTER-HTTP
- [ ] (FE) FORT-01 · CSAT-01 오행분석 (F-09)
  - [x] (FE) 가짜 범위 — 포트 `getBasicSaju(personId)` · 가짜(소유 검사 · 401 · 404 · 422) (`13eb54d`), 공통 `FiveElementsSection` 이 막대 5줄에 서버 값 · 422 출생 시간 필요 안내 자리 (`5c38cb3`, BASIC-SAJU-PORT)
  - [x] (FE) 진짜 adapter — `{ personId }` 입력(Q-30 해결), 키 5개 정확히 검사 · ELEMENTS 순서 (`e6a603a`). `ports/index.ts` 연결 (`e781919`)
  - [ ] (FE) 시간 미상 고지 문구 (F-08)
- [x] (FE) FORT-02 · 03 · MATCH-03 질문 — 선택지 칩(질문 하나 = `questionKey` 하나, 궁합은 관계 칩 + 질문 칩, 고르기 전 구매 버튼 비활성), 결제 CTA 위 고지 문장, 구매 선택 복원 v4 (`536b810`, FUNCTIONAL_SPEC v0.4 8장 · Q-26 해결). 궁합 상대의 저장 관계가 애인이면 「연인」 미리 고르기 (`6cee20d`)
- [x] (FE) 일반 운세 견적 · 구매 포트를 카테고리 경로로 — `POST /quotes/fortune/{category}` · `POST /reading-purchases/{category}`, 견적 응답의 옵션 · 질문을 보낸 값과 대조 (`2f505f7`). 실제 확인은 스테이징 (R-06)
  - [x] (FE) 수능운 먼저 (SUNEUNG-ADAPTER-FIRST) — 선택값 `fortuneType`, 견적 `price` → `charged` · `productName` nullable(Q-28), 구매 `charged` nullable(Q-38), 구매 선택 복원 v2, 수능운 adapter (`1fcd9c0`). 견적 `productName` · `regularPrice`(검사만) (`41f93af`), 운세 상품 목록 (`e781919`). 일반 5종은 `2f505f7`
- [x] (FE) 구매 상태 조회 · 구매 키 보관 — `GET /reading-purchases/by-quote/{quoteId}`(404 `PURCHASE_NOT_FOUND` → 기록 없음) · `compensationStatus` · `nextAction` 을 포트 · 가짜 · adapter 에 (`9f1366e`, PURCHASE-STATUS-WAIT 해제), 팝업을 서버 상태 기준으로 · 자동 반복 조회 없음 (`9addcee`, PURCHASE-STATUS-UI), 구매 키 `sessionStorage` · 새로고침 복귀 때 상태 조회부터 (`afcd0b3`, PURCHASE-KEY-STORE, 복원 v3). 남은 것 Q-38 f · g
- [x] (FE) 구매 결과 처리 — 오류 code · `FAILED` 만으로 환급 문장을 보이지 않음(서버 `REFUNDED` 일 때만), `FULFILLED` + `readingId` 일 때만 결과 이동, 처리 중은 같은 키로 결과 확인 (`8feb588`, Q-38). 환급 확인 API 는 BE (Q-38)
- [x] (FE) 수능 결과 구조 — 서버 `suneungGuide` 를 결과 포트 · 화면 · adapter 에 (`086ed90` · `a352829`, RESULT-GUIDE-MODEL, Q-37 해결). 이름 · 순서는 PD (Q-40 2)
- [ ] (FE) FORT-06 · 07 결과 — 카테고리별 고정 필드를 ReadingViewer 섹션으로, `meta.disclaimers` (Q-35 1 · Q-05). 지금은 `SECTIONS` 본문(`key` · `content`, 아는 key 25개 · 모르는 key 는 건너뜀 + Sentry)으로 그린다 (`086ed90` · `a352829`). 결과는 호환 경로 `GET /readings/{readingId}` 로 받는다(API_SPEC 8장 — 운세별 상세 경로는 아직 안 씀). 섹션 소제목 이름은 PD (Q-62)
- [ ] (FE) 마이페이지 운세 목록 — `GET /readings/{category}` cursor 목록 · 재열람
- [ ] (FE) 새 오류 code — `BIRTH_TIME_REQUIRED_AT_TERM` 분류 완료(`13eb54d`), `PURCHASE_DEBIT_MISMATCH` · `READING_FULFILLMENT_UNAVAILABLE` 처리는 Q-35 4 후 (지금은 status 로 server). 구매 `failureCode` 목록은 COMMON 에 들어왔다 — 화면 사용은 `TODO(Q-38 c)`
- [ ] (FE) CSAT-01 D-day · 판매 마감 — 수능 견적의 `event.date` 서버 값 (Q-04)
- [ ] (FE) 가입 약관 동의 · 만 14세 확인 — Q-36 1 · 2 답에 따라 HOME-02 흐름에
- [ ] (BE-B) 부적 창고 · 상세 · 다운로드 · 공유 API — PR #15 에서 "후속" (TALBOX · FORT-08 · 09 막힘)

---

### Phase 4 — 수능운 본인 구매 + 부적 + ReadingViewer (10/11 ~ 10/17) 💰

**목표**: 로그인 → 정보 확인(CSAT-01) → 등껍질 차감 확인 팝업(부족하면 충전 후 복귀) → 분석 연출(CSAT-02) → 결과(CSAT-03 ~ 06) → 부적 저장 · 공유(CSAT-07). 수능운은 부적 포함 단일 상품 (P-03).

**작업**
- [ ] (FE) 수능운 정보 확인(CSAT-01): 본인/저장 인물 선택. 시험 종류 · 시험일은 고르지 않는다 — `examType: CSAT`, 시험일 · 판매 종료(전날 23:59:59 KST, F-07)는 서버 값만 표시 (Q-04 해결). 마감 판단은 서버 상품 `active` · `saleEndsAt` 기준, 범위 밖은 `422 EVENT_DATE_NOT_ALLOWED`
  - [x] (FE) 가짜 구현 범위 — 본인 기본 · 불러오기 · 진행 → 차감 확인 팝업 → 결과 이동, 충전 후 복귀, 활성 상품 0개는 판매 종료 · 2개 이상은 오류 (`80a9a0b`). 오행분석 값은 API 대기(F-09 · Q-30), 판매 종료 오류 code 는 Q-28
- [ ] (FE) 등껍질 차감 확인 팝업 — 앞 화면 안 Modal (CHECKOUT-POPUP, 상품명 · 대상 · 옵션 · 보유 · 사용 · 구매 후 잔액): 서버 견적(`POST /quotes/fortune`)의 상품 · 대상 · 옵션 · 차감량 · 구매 후 잔액 표시, 단일 구매 명령(`POST /reading-purchases`, `Idempotency-Key`), 결제 버튼 위 고지(F-08)
  - [x] (FE) 가짜 구현 범위 — 운세 구매 포트 · 가짜 견적 · 견적당 키 · 409 처리 (`3d939c4`), 견적 401 은 로그인 안내 (`6526d3c`). 고지 문구는 PD (F-08)
  - [x] (FE) 진짜 adapter — 수능운 견적 · 견적 재확인 · 구매 (`1fcd9c0`). 상품명은 견적 `productName`(`41f93af`, 없으면 상품 줄 없음). `ports/index.ts` 연결 (`e781919`)
  - [x] (FE) 서버 구매 상태 기준 팝업 — 화면 분기 `status` · 버튼 `nextAction`, 결과 불명 뒤 상태 조회 한 번 자동 · 그 뒤는 "결과 확인", 처리 중 팝업 닫기 막음, 새로고침 복귀 이어 가기 (`9addcee` · `afcd0b3`, PURCHASE-STATUS-UI · PURCHASE-KEY-STORE). 문구 · 닫기 허용 여부는 Q-40
- [ ] (FE) 잔액 부족 모달: 견적의 `walletBalance` · `shortage` · `recommendedTopUp` 과 `409 INSUFFICIENT_BALANCE` 의 `details`(서버 값만) → `/wallet` 충전 → `CREDITED` 후 원래 화면으로 복귀. 구매 선택은 `sessionStorage`(인물 ID · 최소 선택값, 24시간)에서 복원하고 `GET /quotes/{quoteId}` 로 재확인 — `409 QUOTE_EXPIRED` 면 선택 유지 + 새 견적, 가격이 바뀌면 다시 확인받음 (P-06, Q-07 해결, PURCHASE-RESTORE)
  - [x] (FE) 가짜 구현 범위 — 구매 선택 저장 · 충전 완료 후 이어 가기 · 로그아웃 삭제 (`3d939c4` · `c83ae3e`), 서버 추천 충전 상품 표시 · 가짜 추천을 BE-A 규칙으로 (`6526d3c`)
- [ ] (FE) LoadingScene: 생성 대기 · 실패 시 환급 상태 안내 (`READING_GENERATION_FAILED`)
  - [x] (FE) 가짜 구현 범위 — 구매 요청 동안 대기 장면, FAILED · 생성 실패 code 는 환급 안내 + 새 견적 (`57ceedd`). 캐릭터 연출은 디자인 재개 후
  - [x] (FE) 대기 장면 와이어 배치(FORT-05 · CSAT-02) — 전체 화면 · 캐릭터 자리 60×70 · 문구 (`36d4284`)
  - [x] (FE) 실패 안내를 서버 상태로 — `RETRY_PURCHASE` 는 다시 시도, `REFUNDED` + `COMPLETED` 만 환급 문장, 그 밖의 실패는 "고객센터로 문의해 주세요"(`TODO(PD 문구)`) (`9addcee`). 문의 채널 Q-38 g
  - [x] (FE) COMMON-02 ~ 04 PD 문구 · 생성 실패 「결과를 만들지 못했어요」 · 문의하기 메일 링크(제목에 주문 · 구매 · 견적 번호, `src/lib/support/contact.ts`) (`4e6cab1`, Q-40 · Q-47). FORT-10 와이어 문구 차이는 Q-61
  - [x] (FE) 구매 완료 안내 「준비가 끝났어요!」 — 결과 이동은 「결과 보러 가기」 를 누를 때 한 번, 완료 중 팝업 닫기 막음 (`b3d2051`, FUNCTIONAL_SPEC v0.4 8장)
- [ ] (FE) ReadingViewer: 본문 종류별 렌더 — `SUNEUNG_GUIDE`(카드 8개 · 교시 · 음식 · 준비물 분류별 체크) · `SECTIONS`(`key` · `content`, 모르는 key 는 그 섹션만 건너뜀 + Sentry 경고) (RESULT-GUIDE-MODEL), 고지(`disclaimers`), 재열람(`GET /readings/{id}`). 남은 것: 이름 · 순서(Q-40 2) · 요약(Q-05) · 고지 문구(F-08)
  - [x] (FE) 가짜 구현 범위 — 4타입 · 모르는 타입 건너뜀 + 경고 · 고지 코드 · 재열람 `/suneung/r` · `/fortune/r` (`16e56aa`). 고지 문구는 PD — 지금은 코드 원문이 보인다
  - [x] (FE) 서버 구조로 바꿈 · 진짜 결과 adapter (`086ed90` · `a352829`). 준비물 체크 저장 키는 예전과 같은 `PREPARATION`
  - [x] (FE) 수능 카드 제목 · 교시 이름 · 준비물 묶음 PD 문구 (`608a5f5`, Q-40 · Q-45), 8장 카드 넘겨 보기 (`f8148ea`)
- [x] (FE) 준비물 체크 상태 로컬 저장 (결과 스냅샷 불변) — `04bcb28`
- [ ] (FE) 부적 추가 구매(`POST /readings/{id}/talisman-purchases`) — 부적 선택 단계 없음 (T-01 자동생성, 부족한 기운 산출은 BE-B Q-19). 일반 운세 `사주` 구매자만 — 수능운은 부적 포함 단일 상품 (P-03)
- [ ] (FE) TalismanViewer: 부적 상태(`PENDING/READY/FAILED`, F-06), 원본 저장(10분 signed URL, iOS 길게 눌러 저장 안내), 카카오톡 공유하기(서버 share 리소스, G-11)
- [ ] (BE-B) 수능운 구매 · 결과 · 부적 · R2 (10/11~10/17) — 구매 · 상태 조회 · 결과 구조 · 생성 lease 는 `main`(PR #20 · #23), 부적 · R2 남음
- [ ] (PD) 수능 콘텐츠 1차 (D-04 10/15)

**완료 기준**: 테스트 충전 → 수능운 구매 → 결과 전 섹션 → 재열람에서 같은 결과. 결제 · 차감 없이 결과 URL 을 열면 403/404. 실기기 부적 저장.

---

### Phase 5 — 선물하기 (10/18 ~ 10/23) 💰

**목표**: 구매자가 수능운 단건 선물을 등껍질로 결제하면 (P-07 · 핵심 전제, 팀 문서 PR 대기) 서버가 수신자에게 알림톡을 보내고, 수신자는 로그인 없이 링크를 열어 정보를 입력하고 편지 · 부적 · (옵션에 따라) 결과를 받는다.

**작업**
- [ ] (FE) GiftWizard(GIFT-01 · 02 · 03, 여러 명 GIFT-06 ~ 08 은 만들지 않음 — Q-23): 옵션 (운세 유형 단계 없음 — 1차 수능운만, Q-08)(`TALISMAN_ONLY` / `READING_WITH_TALISMAN`) → 수신자 이름 · 휴대전화번호 → 편지(200자, 추천 5개, 이모지 · 줄바꿈) + 보내는 사람 이름(카카오 닉네임 기본값, 수정 가능, 보이는 글자 1~20자 · 이모지 허용 · 공백만 · 줄바꿈 불가, `fieldErrors.senderName`) → 검토. 부적 단계 없음 (Q-06 자동생성). Zustand + `sessionStorage` 복원 실패 시 결제로 가지 않음 — Figma 는 최종 와이어 (FIGMA-FINAL)
- [ ] (FE) 선물 결제 — GIFT-03 다음 팝업 (CHECKOUT-POPUP): 서버 선물 견적 → 등껍질 차감 단일 명령 — 본인 구매의 등껍질 차감 Checkout · 잔액 부족 모달 · 구매 선택 복원 재사용. 선물 가격은 P-03A(서버 값만 표시), 견적 잔액 부족 필드 · 주문 API · 환급은 BE-A(Q-20) 대기. 팀 문서 병합 · OpenAPI 전에는 만들지 않는다
- [ ] (FE) 선물 완료(GIFT-04 · GIFT-05 — 발송 실패의 링크 복사는 만들지 않음 Q-23, 대기 상태 · 재발송 위치 Q-24): 알림톡 발송 상태(`delivery.status` `PENDING` · `SENT` · `FAILED`, 마스킹 번호). 재발송 버튼은 서버의 `canResend` 만 따르고 `remainingResends` · `nextResendAt` 을 표시 (FE 가 횟수 · 시간 계산 안 함). 재발송은 새 링크 발급 · 이전 링크 폐기 — 요청 전에 알림. `429 RATE_LIMITED` · `409 IDEMPOTENCY_REQUEST_PROCESSING` 뒤엔 주문 재조회. 번호 수정 · 만료 선물은 CS 안내 (G-10 — 팀 문서 PR 대기, API 는 Q-17). 재발송 확인 모달 · 남은 횟수 · 초과 · 간격 문구는 PD 답(10/3)을 쓴다
- [ ] (FE) 수신자 `/g/[token]`: 도착(RECV-01 · RECV-T-01) → 정보 입력(RECV-02 · RECV-T-02, PersonForm, 1회 후 잠김, `409 ALREADY_SUBMITTED`) → 결과(RECV-03 ~ 07 · RECV-T-03 ~ 04, ReadingViewer + 편지) · 만료 · 폐기 410 화면 · 변조 404. 안내 단계에서 결과를 미리 조회하지 않는다 (제공 개시 = 서버가 완성 콘텐츠를 처음 응답한 때, G-09 · Q-17)
- [ ] (FE) `/g/[token]` OG: 수신자 · 보낸 사람 이름 노출 (Q-13 해결), 생년정보 · 메시지 금지 (G-08). 문구는 PD
- [ ] (FE) 수신자 결과 하단 "다른 운세도 보러가기" CTA, 로그인 후 부적 claim(`POST /talismans/{id}/claim`)
- [x] (FE) `screens.ts` 화면 번호를 최종 와이어 화면 ID(HOME- · FORT- · CSAT- · GIFT- · RECV- 등)로 전환 — 구 #24 · #26 정리 포함 (FIGMA-FINAL) — `4ce2e6a`
- [ ] (BE-A) 선물 결제 · 토큰 · 알림톡 · 재발급 (10/18~10/23) · (BE-B) 수신 입력 · 결과 · claim

**완료 기준**: 테스트 결제 → 알림톡 수신 → **다른 기기 · 비로그인 · 카카오톡 인앱** 에서 수신 흐름 완주. 토큰 1글자 변조 → 정보 미노출. 수신자 정보 재입력 차단.

---

### Phase 6 — 일반 운세 5종 · 부가 기능 · 디자인 적용 (10/14 ~ 10/27)

PG 심사 요청 후 디자인 보류 항목(Phase 1)을 여기서 재개한다. 위에서부터 만들고, 밀리면 아래부터 자른다.

| 순위 | 기능 | 화면 | 자를 때 대체안 |
|---|---|---|---|
| 1 | 디자인 적용 (토큰 · 프레임 · 사이드 메뉴 정리) | 공통 | 토큰 색만 적용 |
| 2 | 일반 운세 5종 (같은 흐름 · 같은 뷰어) | FORT-01 ~ 09 · MATCH-01 ~ 03 | BE 우선순위(`BACKEND_ROLE_SPLIT.md` 9장): 수능운 → 충전 → 선물 → 부적 → 일반 5종 |
| 3 | 마이페이지 (계정 · 잔액 · 저장 인물 · 구매 결과 · 보낸 선물 · 충전/사용 내역 · 로그아웃 · 탈퇴) | MY-01 · MY-02 (보낸 선물 와이어 없음, Q-24) | 결과 · 선물 목록만 |
| 4 | 홈 연결 완성 (판매 종료 상태, 수능 후 신규 구매 숨김 F-07) | HOME-03 · HOME-04 | 수능운 · 선물하기만 활성 |
| 5 | 오늘의 운세 (무료, 로그인 + 저장된 본인 정보 — A-02 · X-01) | TODAY-01 · 02 | 연출 없이 결과만 |
| 6 | 부적 창고 (×N 묶음 · 12지 도감) | TALBOX-01 · 02 | 마이페이지 안 목록 |

**작업**
- [ ] (FE) 디자인 보류 항목 재개 (Phase 1 의 보류 5개)
- [ ] (FE) 일반 운세 5종: 정보 확인 · 선택형 질문(관심 항목, 자유 메모는 결과 미반영 표시 — 입력 형태 Q-26) · 궁합 사람 선택(MATCH, Q-26) · 등껍질 차감 · 결과 (신살은 F-03 부분)
  - [x] (FE) 가짜 구현 범위 — FORT-01 (`d7f988b`), FORT-02 · 03 (`74a753e`), MATCH-01 · 02 (`1f6150e`, 첫 칸 본인 고정 MATCH-SELF), MATCH-03 (`ddeb780`), 가짜 시나리오 `signed_in_with_other` (`45828ff`). 질문 입력(선택지 · 고민)은 자리만 (Q-26 · F-04)
  - [x] (FE) 질문 선택지 · 관계 값 전송 (`536b810` · `2f505f7` · `6cee20d`). 궁합 상대 새로 입력 — MY-02 후
- [ ] (FE) 마이페이지 · 홈 · 오늘의 운세 · 부적 창고 (위 표 순서)
  - [x] (FE) 홈 HOME-03 · 메뉴 HOME-04 (`8c16730` · `ce4bf89`). 판매 종료 상태 · 수능 후 숨김(F-07)은 남음
  - [ ] (FE) 오늘의 운세 TODAY-01 · 02 — **보류 (TODAY-HOLD)**: API 는 들어왔다(`GET /daily-fortunes/today?personId=`, 로그인 · 지수 5종 0 ~ 100 — X-01 은 맞음). X-02 의 한줄 운세 · 행운 색 · 아이템 · 장소 · 오늘의 행동 · 공유가 응답에 없어 1차 화면 범위(PD · BE-B, Q-31) 후
  - [x] (FE) MY-01 가짜 구현 범위 — 계정 닉네임 · 로그아웃 · 저장된 사람들 · 추가 링크 · 약관 링크 (`8141672` · `71296ad`). 내 운세 기록 · 충전 사용 내역 · 탈퇴는 API · 경로 · A-08 대기
  - [ ] (FE) MY-04 내 운세 기록 · MY-07 회원탈퇴 — 경로 Q-57 (FE 제안 `/me/readings` · `/me/withdraw`, 기능 명세서 v0.4 2장). API 는 `GET /readings` · `DELETE /me`. 탈퇴 조건(유료 잔액 있으면 불가)은 Q-52
  - [ ] (FE) MY-03 충전 · 사용 내역 — 경로 Q-57, 사용 · 환급 · 소멸 내역 API Q-60. 환불 태그 4종은 Q-48 (기능 명세서 v0.4 13장)
  - [ ] (FE) HOME-02 가입 동의 4개 — 동의 API 가 API_SPEC · COMMON 에 들어오면 (Q-59)
  - [ ] (FE) CSAT-08 수능운 판매 종료 — 마감일 출처 Q-63, 「내 운세 기록 보기」 경로 Q-57
- [ ] (FE) 공유 랜딩 `/share/[shareId]` — 캐릭터 · 결과 요약 카드 · "나도 보러 가기" CTA, 상세 결과 · 생년정보 · 메시지 미노출 (Q-10 PD 답, FUNCTIONAL_SPEC 2장 반영 · G-11 대기)

**완료 기준**: 순위 1~3 완료 + 실기기 1회 완주. 4~6 은 10/26 18:00 에 끝나지 않으면 대체안.

---

### Phase 7 — 출시 준비 (10/22 ~ 10/30) — 출시 게이트

**작업**
- [ ] (전원) QA 체크리스트 → 흐름 × 브라우저 3종, 교차 QA 2회 (10/24, 10/27)
- [ ] **10/28(수) 18:00 코드 프리즈**
- [ ] (PD) 약관 · 개인정보처리방침(국외 이전 고지 포함) · 환불정책 확정, 표현 검수
- [ ] (BE-A) 운영 키 전환 → **10/29(목) 운영 실결제 · 환불 확인**
- [ ] (FE) PostHog O-06 이벤트 심기 (금지 속성 제외). `gift_alimtalk_sent/failed` 는 서버 이벤트라 FE 가 보내지 않는다 (Q-12 해결)
- [ ] (FE) Playwright E2E (모바일 뷰포트, 테스트 결제): 충전 · 수능운 · 선물 구매 · 선물 수신
- [ ] (FE) Vitest: 판매 마감 경계(11/18 23:59 / 11/19 00:00 KST, `TZ=UTC`), 오류 code 분기, 멱등 키 재사용
- [ ] (FE) Sentry 소스맵 업로드

**완료 기준**: Blocker/Critical 0건, 운영 실결제 1건 + 환불, 약관 3종 · 사업자 정보가 모든 결제 화면에서 보인다, 테스트 결제 1건이 퍼널 끝까지 찍힌다.

---

### Phase 8 — 운영 (10/31 ~ 11/21)

| 구간 | 기간 | 할 일 |
|---|---|---|
| 8-1 안정화 | 10/31 ~ 11/6 | 지표 리뷰(결제 전환율 · 결제 실패율 · 알림톡 성공률 · 선물 링크 열람률), 긴급 버그, CS |
| 8-2 실험 | 11/7 ~ 11/15 | 문구 · 홈 CTA 실험 (가격은 서버 카탈로그 · 팀 결정) |
| 8-3 피크 | 11/16 ~ 11/19 | **기능 변경 금지**, 모니터링 강화 |
| 8-4 마무리 | 11/20 ~ 11/21 | 매출 증빙 정리(PG 결제 완료 금액 · 정산액, O-01 · O-08), 회고 |

---

## 7. 결정 — 구 D-xx 대응표 (DECISION-IDS)

FE 가 9/26 에 만든 결정 목록 D-01 ~ D-16 은 닫았다. 지금 결정은 팀 결정 원본 `docs/PENDING_DECISIONS.md` 의 ID 로 부른다.

| 구 ID | 내용 | 팀 ID · 상태 (2026-10-03) |
|---|---|---|
| 구 D-01 | 결제 2번 → 옵션 1회 결제 | P-03 확정 — 일반 운세 `사주` / `사주+부적`, `사주` 후 부적 추가 구매. 수능운은 부적 포함 단일 (10/3 변경, PR 대기) |
| 구 D-02 | 재화 도입 여부 | P-01 확정 — 등껍질 1종 (`TURTLE_SHELL`). 충전 상품 P-02 확정 (10/3 변경) |
| 구 D-03 | 선물 대상 범위 | 핵심 전제 — 1차 선물은 수능운만 (10/3 팀 결정, 팀 문서 PR 대기) |
| 구 D-04 | 선물 전달 방식 | G-10 부분 확정 — 서버가 알림톡 발송. 자동 재시도 3회 · 수동 하루 3회 · SMS 없음 (PR 대기), 대행사 미정 |
| 구 D-05 | 결과 재열람 | F-05 확정 — 계정 유지 중 무제한, 스냅샷 |
| 구 D-06 | 만화 인트로 | X-03 보류 |
| 구 D-07 | 선물 D-day 가격 옵션 | 폐기 — 선물 단건 (P-03A 등껍질 가격) |
| 구 D-08 | n일 상품 부적 도착 | 폐기 — 선물 단건 |
| 구 D-09 | 부적 동물 기준 · 수 | T-02 부분 확정(띠, 경계 미정) · T-03 확정(12지 전체) |
| 구 D-10 | 유료 운세 · 취업운 포함 | 핵심 전제 확정 — 일반 5종(종합 · 애정 · 재물 · 궁합 · 신살) + 수능운. 취업운 없음 |
| 구 D-11 | 서비스명 | 확정 — 뿌기사주 |
| 구 D-12 | 사업자 · 대회 수익 기준 | O-01 부분 확정 · O-08 미정 |
| 구 D-13 | 사주 계산 기준 | S-01 부분 확정 (자시 경계 미정) · S-02 |
| 구 D-14 | 백엔드 스택 | I-01 확정 — Spring Boot 3 + PostgreSQL, Railway |
| 구 D-15 | 로그인 필수 범위 | A-02 확정 — 무료 오늘 운세 · 선물 수신은 비로그인 |
| 구 D-16 | 청소년 결제수단 | P-08 부분 확정 — 카드 필수, 간편결제 계약상 노출, 휴대폰 소액결제 제외 |

FE 에 걸린 미정 결정: T-01(부족한 기운 산출 · 기준일, BE-B) · F-03(17종 계산 가능 여부 BE-B, 관심 항목 PD) · P-03A(선물 견적 잔액 부족 필드, BE-A) · G-09(선물 환급 방식) · F-06(Liner 재시도 · 부적 동기/비동기) · F-09(오행분석 노출) · G-11(공유 범위) · S-01 · T-02 · I-02(세션 초과 폐기 등 — CSRF 는 Q-02 해결) · D-02(글꼴) · D-05(말투).
팀 문서 PR `cc60ab3`(`main` 병합 · PD 검토 대기)로 반영한 결정: 핵심 전제(1차 선물 수능운만 · 선물 등껍질 결제 · 부적 자동생성), P-02A · P-03 · P-03A · P-04 · P-05 · P-07 · F-01 · F-02 신살 · F-03(부분) · G-07 · G-09 · G-10 · T-01 · T-01A · T-06. 병합 전에는 이 결정에 기대는 코드를 만들지 않고, 병합 후에도 API 는 OpenAPI 를 기다린다 (API_SPEC 반영은 BE, Q-17).
최종 와이어(FIGMA-FINAL)와 팀 결정이 부딪히거나 와이어가 비어 있는 곳: Q-23 d ~ g(남은 충돌) · Q-24(PD ✚ 프레임) · Q-25(충전 결제 수단) · Q-26(선택지 · 고민 입력). Q-27 은 해결(CHECKOUT-POPUP). 해결 전에는 팀 문서를 따른다.

---

## 8. 리스크

| 리스크 | 가능성 | 영향 | 대응 |
|---|---|---|---|
| PG 심사 지연 · 반려 (운세 · 충전형 재화 업종 해석) | **높음** (10/14 — 일반 심사로는 10/31 에 결제가 안 열림) | 치명 | **토스 퀵오픈**(R-10, 10/15 통화 후 결정) — 결제를 먼저 열고 심사는 따로. 안 되면 계좌이체 수동 충전(BE-A 지급 API · FE 입금 안내). 심사 요건은 그대로 맞춘다(R-01 · R-07 · Q-56) |
| 충전 1일 결제 한도 (토스 체크리스트: 충전 서비스 5만 원 미만) | 중 | 높음 | 50,000원 상품 제외 또는 BE-A 하루 누적 한도 — 10/15 토스 답 (R-01) |
| 퀵오픈 정산 보류가 대회 매출 인정에 걸림 | 중 | 높음 | 대표가 대회 규정 확인 (결제 내역 ↔ 통장 입금), 입금만이면 계좌이체 대안 병행 (R-10) |
| BE 충전 API 가 늦어 FE PG-3 이 밀림 (10/4 스테이징 미충족, 10/10 이전 제공 약속 불가 — R-06) | 높음 | 치명 | PG-1 · PG-2 · PG-4 를 먼저, BE 재산정 일정 확인, 10/14 심사 요청일 재검토 |
| 팀 답과 팀 문서가 어긋난 채 구현 (Q-01 · Q-06 · Q-08 · Q-16 · Q-17) | 중 | 높음 | 반영 대기 항목은 팀 문서에 들어간 뒤 구현 (TQ-ANSWERS) |
| 충전형 재화 가맹 불가 — 선물도 등껍질이라 원화 결제는 충전뿐 (R-01) | 중 | 치명 | 토스 상담 10/10, 10/20 대안 게이트 미리 검토 |
| 심사 중 수정 금지 항목 변경 → 재심사 | 중 | 높음 | 푸터 · 상품 · 결제수단을 제출 전 최종본으로 |
| FE 1명 병목 (화면 + 캐릭터) | 높음 | 높음 | 컴포넌트 재사용, 캐릭터 범위 확인(R-02), Phase 6 컷 |
| 카카오톡 인앱에서 결제 · 저장 실패 | 중 | 높음 | PG-5 · Phase 4 실기기 완료 기준 |
| iOS 실기기 없음 | 높음 | 중 | R-03 |
| ~~알림톡 대행사 · 템플릿 심사 지연 (G-10)~~ | — | — | 10/14 팀 문서가 1차 직접 공유 · 알림톡 2차로 바뀌어 1차 위험에서 빠짐 (Q-54 PD 확인 대기) |
| 미성년자 결제 취소 요청 | 중 | 중 | 환불정책 · CS 절차 (P-05) |
| 카카오 미리보기 캐시로 OG 가 안 바뀜 | 중 | 낮음 | 링크마다 고유 URL 로 확인 |

---

## 9. 출시 범위 컷 라인

- **Must (없으면 출시 안 함)**: 카카오 로그인 · 인물 정보, 충전 결제, 수능운 본인 구매 · 결과 · 부적, 단건 선물(구매자 + 비로그인 수신), 약관 3종 · 사업자 정보
- **Should**: 일반 운세 5종, 마이페이지, 오늘의 운세(무료), 카카오톡 공유하기
- **Could**: 부적 창고 도감, 공유 랜딩, 만화 인트로(X-03 보류)
