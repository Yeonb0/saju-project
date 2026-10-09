# FE 요청 답변 및 BE-A 구현 인계

작성일: 2026-10-10. 공유 대상: FE, BE-B, PD.
코드 기준: main `f47b719`, BE-A [PR #17](https://github.com/Yeonb0/saju-project/pull/17) 병합 완료.
FE 비교 기준: main과 원격 boyeon `0ab2472`. 이 문서는 FE 개인 진행 문서의 상태를 대신 갱신하지 않는다.

## 1. 먼저 전달할 결론

BE-A의 견적 자금 정보, 사용 금액 통일, 견적 재조회, 지갑 잔액 분리,
구매 차감/실패 복구 기반, 요청 키 충돌 방지, 충전 주문 생성/조회가 main에 병합됐습니다.
타입 생성 기준은 저장소의 `docs/openapi/api-v1.json`입니다.

실제 충전 승인/지급은 아직 없습니다. 충전 주문의 지급량은 예정량이며 결제 완료를 의미하지 않습니다.
지갑 구매/복구는 기본 비활성입니다. 실제 세션/인물 연결, 구매 중단 재개,
FE real adapter와 통합 검증 전에는 실사용 구매가 완료된 것으로 안내하지 않습니다.
스테이징 Base URL과 제공 날짜는 이 문서에서 확정하지 않습니다.

## 2. Q-34 답변: 견적·충전·지갑

모든 아래 경로의 접두사는 `/api/v1`입니다. 성공 응답은 `{data, traceId}` 껍데기를 사용합니다.

| 질문 | 답변 및 구현 상태 | FE 반영 |
|---|---|---|
| Q-34.1 보유·구매 후 잔액·부족·추천 | 일반 5종과 수능 견적에 공통 funding 필드 구현 | 숫자를 FE에서 다시 계산하지 않고 서버 값 사용 |
| Q-34.2 사용 금액 필드 통일 | 모두 `charged {currency, amount}` | 수능 최상위 currency/amount에 의존하던 매핑 변경 |
| Q-34.3 견적 재조회 | `GET /quotes/{quoteId}` 구현 | 충전 복귀 후 재조회해 팝업 갱신 |
| Q-34.4 유료·보너스 잔액 | `GET /wallet` 구현 | paidBalance/bonusBalance/balance 표시 |
| Q-34.4 지급 수량 | `POST /top-up-orders`, `GET /top-up-orders/{orderId}`에 예정량 구현 | 완료 화면이 아니라 주문/결제 준비 정보로 사용 |
| Q-34.4 승인 후 지급 결과 | 미구현 | confirm API·실제 지급·지급 후 잔액을 기다려야 함 |
| Q-34.5 500 상품 수량 | 명세는 유료 500 + 보너스 80 = 총 580 | 82/582 고정값이 남았으면 수정, 표시 값은 서버 기준 |

### 견적 공통 필드

| 필드 | 의미 |
|---|---|
| charged | 사용 통화 TURTLE_SHELL과 사용 수량 |
| walletBalance | 조회 시점 사용 가능한 총잔액 |
| balanceAfter | 구매 가능하면 예상 잔액, 부족하면 null |
| shortage | 부족 수량, 부족하지 않으면 0 |
| recommendedTopUp | 부족분을 채우는 단일 상품 코드, 적합한 판매 상품이 없으면 null |
| expiresAt | 기존 견적 만료 시각 |

`recommendedTopUp=null`은 잔액 충분이라는 뜻이 아닙니다. shortage를 함께 봅니다.
GET은 저장 가격/만료를 유지하고 현재 지갑 기준 funding을 다시 계산합니다.
GET에는 POST 종류별 questionKey/productOption/event 전체가 포함되지 않으므로
최소 선택값을 별도로 보존합니다. 견적은 잔액 예약이 아닙니다.
타인/없는 견적은 404 RESOURCE_NOT_FOUND, 본인 만료는 409 QUOTE_EXPIRED입니다.
productName/정가 메타데이터와 공개 GET /products는 아직 후속입니다.

### 충전 주문과 지갑

- 지갑 응답: currency, balance, paidBalance, bonusBalance. 만료/미래 지급분은 사용 가능 잔액에서 제외합니다.
- 주문 생성 본문: `{ "productCode": "TURTLE_SHELL_50" }`. 사용자 ID/가격/지급량은 FE가 지정하지 않습니다.
- 생성에는 인증/CSRF/Idempotency-Key(비어 있지 않은 최대 512자)가 필요합니다.
- 같은 사용자/키/상품은 같은 주문 snapshot, 같은 키/다른 상품은 409 IDEMPOTENCY_KEY_REUSED입니다.
- 주문 응답: orderId, productCode, orderName, amount, currency,
  paidShellAmount, bonusShellAmount, creditedShellAmount, status.
- amount는 원화 정수이고 currency는 KRW입니다. FE Money 모델에는 `{currency, amount}`로 매핑합니다.
- 현재 상태는 PAYMENT_PENDING입니다. 예정량만 저장하며 원장/lot/잔액을 지급하지 않습니다.
- 현재 주문 GET에는 FE 초안의 processing/walletBalance가 없습니다. 실제 승인/지급 상태 계약은 후속입니다.
- POST /top-up-orders/{orderId}/confirm 및 webhook/cancel/refund는 미구현입니다.
- 상품 판매 활성화나 테스트 키 전달은 아직 하지 않았습니다. 키는 문서/커밋에 넣지 않습니다.

PAY-03의 “N개가 들어왔어요”는 CREDITED가 확인된 뒤에만 표시해야 합니다.
PAYMENT_PENDING/PAID/예정량만으로 충전 완료를 표시하거나 새 주문으로 재결제를 유도하지 않습니다.

## 3. Q-35 답변: 결과·궁합·오행·오류

아래는 전달된 BE-B 답변과 현재 코드를 구분해 정리한 것입니다. A가 B의 후속 일정을 확정하지 않습니다.

### Q-35.1 결과 변환

BE-B 답변상 일반 결과 순서는 다음과 같습니다. JSON 객체 나열 순서가 아닌 FE 매핑 테이블을 사용합니다.

- 종합: summary → currentFlow → relationships → studyAndWork → wealthFlow → condition → luckyPoint → missingElement
- 연애: summary → currentFlow → goodPeriod → caution → actionTip → missingElement
- 재물: summary → wealthFlow → income → spendingCaution → goodPeriod → actionTip → missingElement
- 궁합: summary → matchStrength → matchConflict → communication → relationshipTip → missingElement
- 신살: summary → specialStars → balancingGuide → missingElement

일반 결과의 meta/counterpart는 별도 구조이며 결과 섹션은 `{content, sourceFactKeys}`입니다.
선택 섹션 누락은 현재 상세 응답에서 null입니다. 일반은 TEXT로 렌더링하고 null은 빈 본문으로 만들지 않습니다.
BE-B 답변상 summary는 요약 상자이며 화면 제목/섹션 제목은 PD 문구를 붙입니다.

수능은 sections 배열이지만 실제 항목은 `{key, content, sourceFactKeys}`입니다.
BE-B 답변의 key→type 제안(EXAM_PERIODS→PERIOD_GUIDANCE, MEAL→FOOD_RECOMMENDATION,
PREPARATION→CHECKLIST)만으로 FE의 구조화 items/primary/alternatives가 생기는 것은 아닙니다.
현재 FE ReadingSection의 구조화 타입과 불일치하므로 B와 구조화 DTO 또는 텍스트 표시 축소 계약을 확정해야 합니다.
본문 문자열을 임의로 파싱해 교시/음식/체크리스트 데이터를 만들지 않습니다.

### Q-35.2 궁합 관계

BE-B 답변의 1차 매핑은 엄마/아빠→FAMILY, 애인→LOVER, 직접 입력→OTHER입니다.
현재 서버는 직접 입력 문자열을 받거나 저장하지 않습니다. 문자열 표시/재열람이 필요하면
PD/B가 relationLabel 같은 추가 계약을 확정해야 합니다. FRIEND/CRUSH/WORK_SCHOOL enum도 유지됩니다.

### Q-35.3 personId 오행 입력

`POST /fortune/basic`에 `{ "personId": "uuid" }` 단독 입력이 구현됐습니다.
원문 생년 입력 방식도 유지하지만 두 방식을 혼합할 수 없습니다.
서버가 본인 소유 인물을 조회하는 계약이며 FE가 생년정보를 다시 받아 보내는 우회를 하지 않습니다.
실제 ReadingSubjectPort adapter는 아직 없어서 저장 인물 real 연결은 대기입니다.
미연결은 503, 타인/없는 인물은 404, basic 절기 경계 시간 미상은 422 BIRTH_TIME_REQUIRED_AT_TERM입니다.

### Q-35.4 오류와 재시도: 중요한 주의

- READING_FULFILLMENT_UNAVAILABLE(503): 의존성 미연결 경로는 차감 전 거절합니다.
  다만 다른 adapter의 결과 불명까지 모든 503를 “차감 없음”으로 일반화하지 않습니다.
- 같은 구매 의도를 재시도할 때 동일 키/quote/경로/선택을 유지합니다.
  다른 quote/선택/경로로 같은 키를 보내면 409 IDEMPOTENCY_KEY_REUSED입니다.
- 구매 요청 binding은 최초 HTTP 응답 replay나 중단 작업 재개를 구현한 것은 아닙니다.
- PURCHASE_DEBIT_MISMATCH: BE-B 답변과 현재 정상 A 흐름은 보상 호출 성공 후 이 code를 반환하는 구조입니다.
  B 즉시 보상은 반환 receipt 자체를 검증하지 않으므로 모든 adapter까지 환급 성공을 일반화하지 않습니다.
  복구 receipt/REFUNDED 확인 계약을 B와 정하고, 생성 실패 code와 동일하게 취급하지 않습니다.
- READING_GENERATION_FAILED: 보상이 실패해도 같은 code가 반환될 수 있습니다.
  FE generation.ts의 code만으로 refunded=true를 만드는 처리는 실제 연결 전에 수정해야 합니다.
- 소유자 purchaseId 상태 GET은 아직 없습니다. 환급 완료 확인용 API/응답은 A/B 합의 및 구현 대기입니다.
- 현재 유료 구매 계산의 절기 오류는 UNSUPPORTED_CALENDAR_DATE로 매핑됩니다.
  견적은 계산을 하지 않으므로 basic의 422를 견적/구매 전체에서 기대하지 않습니다.

FULFILLED에서 readingId가 확인된 경우만 결과 화면으로 이동합니다.
처리 중에는 readingId/balance가 null일 수 있고 완료 재조회에서도 balance가 null일 수 있습니다.
FE 필수 string/number 모델을 이에 맞춰 분리하고 null 잔액을 0으로 채우지 않습니다.

### Q-35.5 OpenAPI와 스테이징

- 타입 생성 파일: `docs/openapi/api-v1.json` ([main 파일](https://github.com/Yeonb0/saju-project/blob/main/docs/openapi/api-v1.json)).
- 생성 출처: local의 `/v3/api-docs/v1`, 갱신 작업: backend의 `exportOpenApi`.
- 인증 필요 여부, CSRF, 오류 의미, 기본 비활성은 별도 계약도 함께 읽습니다. 문서의 경로 존재가 real 사용 가능 증거는 아닙니다.
- staging/production springdoc 공개는 전제하지 않습니다. 스테이징 URL·세션/CSRF 발급·배포일은 별도 인계 필요입니다.

## 4. Q-33·Q-36: 이미 전달된 방향과 아직 필요한 결정

| 항목 | FE에게 전달할 내용 | 추가 담당 |
|---|---|---|
| Q-33 취소/실패 문구 | PG 원문을 그대로 표시하지 않고 검수된 문구 사용. 취소 분류/원고/상품 유지의 최종 확정은 대기 | PD, A 오류 매핑 |
| Q-33 충전 필수 동의 | 차단 여부/동의 원고/서버 기록 범위 확정 필요. 현재 주문 생성은 동의 저장을 구현하지 않음 | PD+A |
| Q-36 가입 동의 | B는 약관 종류/버전/동의 시각 기록 방향을 답변. 화면과 실제 구현은 아직 대기 | PD+B |
| Q-36 만 14세 | B의 가입 확인/서버 기록 제안은 최종 법무·PD 확정이나 구현 완료가 아님 | PD+B |
| Q-36 로그인 | 전달된 B 답변은 심사 전 카카오만, 심사 후 Google/네이버 추가. 정책도 실제 제공 범위에 맞춤 | B+PD |
| Q-36 고민 입력 | 현재 서버는 자유 고민 입력을 받지 않음. 확정/구현 전 수집·Liner 전송을 실제 제공처럼 표시하지 않음 | PD+B |
| Q-36 정책 빈칸 | 등록 정보 답변은 별도 전달 자료를 사용. CS/시행일/보관 기간/환불/위탁사 등 미정 값은 임의로 채우지 않음 | PD+A+B |

사업자 등록 검토 문서의 법률/심사 판단을 이 문서에서 재검증하거나 승인한 것은 아닙니다.
정책 초안 전체를 확정본으로 간주하지 말고 담당자가 최종본과 실제 설정을 맞춰 전달해야 합니다.

## 5. FE에게 요청할 반영 순서

1. main PR #17과 OpenAPI를 반영하고 생성 타입은 real adapters 내부에서 FE port 모델로 변환합니다.
2. 견적 charged/funding/null과 GET 재조회, 지갑 유료/보너스 필드를 먼저 맞춥니다.
3. 충전 주문 예정량과 실제 지급 완료를 분리하고 FE Money/processing/walletBalance 초안과 서버 응답을 대조합니다.
4. 구매의 FULFILLED/처리 중/실패/REFUNDED를 분리합니다. 오류 code만으로 환급 완료를 표시하지 않습니다.
5. 일반 결과/궁합/questionKey를 반영하고 수능 구조화 결과는 B와 계약을 먼저 맞춥니다.
6. 실제 세션/인물/토스 승인/지급과 스테이징이 준비되면 real adapters를 연결합니다.
7. 실제 충전→견적 재조회→구매→결과/환급의 E2E, 인증/CSRF/타인 자원/네트워크 유실을 검증합니다.

현재 A 후속은 상품 metadata/GET products, 토스 승인·결과 불명 조회·중복 없는 원자적 지급입니다.
B 공동 후속은 구매 상태 조회, 완료 후 견적 만료 재전송, 생성 lease/fencing과 서버 중단 재개,
환급 receipt 검증입니다. 합의 제안은 `backend/docs/A_B_PURCHASE_HANDOFF.md`에 있습니다.

## 6. FE는 거의 끝났는가?

**주요 화면과 목업 흐름은 많이 구현됐지만 실서비스 연결이 거의 끝난 상태는 아닙니다.**

| 기준 | 확인한 현황 |
|---|---|
| 주요 화면/목업 | 로그인·입력·운세·수능·충전·안내 등 여러 화면의 동작과 와이어 배치 구현 |
| 실제 API | ports/index.ts의 real 선택은 아직 noRealYet 오류. real adapters 미구현 |
| 결제 | 목업 충전/확인 중 시나리오는 존재. 실제 SDK/승인/지급 연결은 대기 |
| 결과 | 일반 변환과 수능 구조화 계약 등 남은 작업 존재 |
| 디자인/전체 기능 | 디자인 토큰/에셋 적용, 오늘의 운세, 선물/부적 등 보류·미구현 범위 존재 |
| 품질 확인 | PR #17 FE CI는 lint/typecheck/test/build 성공. 실서비스 E2E 완료를 뜻하지 않음 |

원격 boyeon의 main 대비 FE 추가 변경은 목업 구매 결과와 충전 후 잔액 재조회 보완이며
real adapter 추가가 아닙니다. FE 작업량을 근거 없이 완료율 숫자로 표현하지 않습니다.
FE 개인 문서는 아직 Q-34/Q-35를 대기로 기록하고 있으므로 이번 답변을 받은 뒤 FE 담당자가 갱신해야 합니다.

## 7. 근거와 검증 범위

- PR #17: main/B 파일 병합 충돌 없음, GitHub backend(PostgreSQL)·frontend CI 성공 후 머지.
- 10/9 로컬 전체 PostgreSQL/H2 각각 296건: 통과 293, 실패/오류 0, 선택 제외 3.
- Flyway 14건 성공, 최종 충전 신규 PostgreSQL 7건 및 OpenAPI export 성공.
- 이번 10/10 문서 작업은 현재 코드/원격 브랜치/FE 문서/전달된 B 답변을 대조했으며 테스트를 새로 실행하지 않았습니다.
- 실제 PG 승인/지급, 실제 OAuth/인물, Liner 포함 A+B 구매 E2E, 운영 부하·OS 중단 복구는 완료 증거가 없습니다.
- 참고: `frontend/docs/PROGRESS.md`, `frontend/docs/TEAM-QUESTIONS.md`,
  `frontend/src/lib/ports/index.ts`, `backend/docs/TOP_UP_IMPLEMENTATION.md`,
  `backend/docs/PURCHASE_REQUEST_BINDINGS.md`, `docs/OPERATIONS_CHECKLIST.md`.
