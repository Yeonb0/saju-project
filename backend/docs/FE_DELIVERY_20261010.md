# FE 전달: 질문 답변·반영 사항·확인 목록

기준: 2026-10-10, BE-A [PR #17](https://github.com/Yeonb0/saju-project/pull/17) main 병합 완료(`f47b719`).
타입 기준: [docs/openapi/api-v1.json](https://github.com/Yeonb0/saju-project/blob/f47b719adb32900bbf8702a11e0dc578059ff9e6/docs/openapi/api-v1.json).
아래 경로는 `/api/v1` 기준, 성공 응답은 `{data, traceId}`입니다.
**코드 병합은 스테이징 제공/실결제 가능을 의미하지 않습니다.**

## 1. Q-34: A 구현 답변

| 요청 | 현재 답변 |
|---|---|
| 보유·구매 후 잔액·부족·추천 | 일반 5종/수능 견적에 walletBalance, balanceAfter, shortage, recommendedTopUp 구현 |
| 사용 금액 통일 | 모두 charged {currency, amount}. 수능 최상위 currency/amount 대신 사용 |
| 견적 재조회 | GET /quotes/{quoteId} 구현. 기존 가격/만료 유지, 현재 잔액 기준 자금 정보 재계산 |
| 지갑 유료·보너스 | GET /wallet: currency, balance, paidBalance, bonusBalance |
| 충전 지급량 | 주문 생성/GET에 paidShellAmount, bonusShellAmount, creditedShellAmount **예정량** 제공 |
| 500 상품 수량 | 유료 500 + 보너스 80 = 총 580. 82/582 고정값 사용 금지 |

- 숫자는 서버 값 사용. shortage=0은 부족 없음, balanceAfter=null은 부족입니다.
- recommendedTopUp=null은 추천 상품 없음이며 잔액 충분을 뜻하지 않습니다.
- 충전 복귀 시 견적 GET으로 갱신합니다. 타인/없는 견적은 404 RESOURCE_NOT_FOUND, 본인 만료는 409 QUOTE_EXPIRED.
- 견적 GET에는 종류별 questionKey/productOption/event 전체가 없으므로 최소 선택값은 별도 유지합니다.
- productName/정가 metadata, GET /products는 아직 후속입니다.

### 충전 주문: 완료 표시 주의

- POST /top-up-orders 본문은 `{ "productCode": "TURTLE_SHELL_50" }`. 인증·CSRF·Idempotency-Key 필수.
- 응답: orderId, productCode, orderName, amount, currency, 유료/보너스/총 예정량, status.
- amount는 원화 정수, currency는 KRW. FE Money에는 `{currency, amount}`로 매핑합니다.
- 현재 PAYMENT_PENDING만 생성하며 **실제 승인/지급 없음**. 예정량으로 “N개가 들어왔어요”를 표시하지 않습니다.
- 주문 GET에는 FE 초안의 processing/walletBalance가 없습니다. confirm API와 지급 후 응답은 후속입니다.
- 같은 사용자/키/상품은 기존 주문, 같은 키/다른 상품은 409 IDEMPOTENCY_KEY_REUSED입니다.
- 충전 완료는 서버 CREDITED 확인 후에만 표시합니다. 토스 SDK/테스트 키/스테이징은 별도 인계입니다.

## 2. Q-35: 결과·오행·오류 답변

### 결과와 궁합: 전달된 B 답변 기준

- 일반 섹션은 `{content, sourceFactKeys}`, 선택 섹션 누락은 null. meta/counterpart는 별도 구조입니다.
- summary는 요약 상자, 일반 섹션은 TEXT. 제목은 PD 문구, JSON 키 나열 순서 대신 FE 매핑표 사용.
- 종합: summary → currentFlow → relationships → studyAndWork → wealthFlow → condition → luckyPoint → missingElement
- 연애: summary → currentFlow → goodPeriod → caution → actionTip → missingElement
- 재물: summary → wealthFlow → income → spendingCaution → goodPeriod → actionTip → missingElement
- 궁합: summary → matchStrength → matchConflict → communication → relationshipTip → missingElement
- 신살: summary → specialStars → balancingGuide → missingElement
- 궁합 관계: 엄마/아빠→FAMILY, 애인→LOVER, 직접 입력→OTHER. 직접 입력 문자열은 현재 서버에 보내지 않습니다.
- **수능은 계약 추가 협의 필요:** 현재 sections는 key/content/sourceFactKeys입니다. key→type 변환만으로
  FE의 교시 items/음식 primary·alternatives/체크리스트 items가 생기지 않습니다. 문자열 임의 파싱 금지.

### 오행 입력과 오류

- POST /fortune/basic에 `{ "personId": "uuid" }` 단독 입력 구현. 생년정보 원문과 혼합 금지.
- 실제 인물 adapter는 미연결입니다. FE가 생년정보를 다시 받아 보내는 우회는 하지 않습니다.
- basic: 미연결 503, 타인/없는 인물 404, 절기 경계 시간 미상 422 BIRTH_TIME_REQUIRED_AT_TERM.
- 유료 구매의 동일 상황은 현재 UNSUPPORTED_CALENDAR_DATE로 매핑되며, 견적은 계산을 하지 않습니다.
- 503 READING_FULFILLMENT_UNAVAILABLE은 의존성 미연결 경로에서 차감 전 거절하지만,
  네트워크 오류/다른 결과 불명까지 전부 “차감 없음”으로 일반화하지 않습니다.
- PURCHASE_DEBIT_MISMATCH는 정상 보상 호출 후 반환하는 흐름입니다. receipt/환급 확정 계약은 B와 보완 중입니다.
- READING_GENERATION_FAILED는 복구 실패여도 반환될 수 있습니다. **오류 code만으로 refunded=true 설정 금지.**
- FULFILLED이며 readingId가 확인됐을 때만 결과로 이동합니다. 처리 중 readingId/balance,
  완료 재조회 balance는 null일 수 있습니다. null을 0/완료로 바꾸지 않습니다.

## 3. 구매 재시도·복구: A 측 선택 기준

1. 응답 유실/새로고침은 새 구매 대신 기존 구매 확인으로 복귀합니다.
2. 자동 복구 실패/한도 초과는 '처리 확인 중'과 문의 경로를 제공하며 환급 완료를 약속하지 않습니다.

- 같은 의도에는 같은 Idempotency-Key/quote/경로/선택 유지. 다른 요청으로 같은 키 사용 시 409.
- 기존 요청 binding은 완료 응답 replay/서버 중단 재개를 보장하지 않습니다.
- purchaseId 상태 GET과 ID 수신 전 응답 유실의 재조회 방법은 A/B 협의·구현 대기입니다.
- 위 두 기준은 사용자 선택이며 B/FE 공동 계약 승인이나 구현 완료는 아닙니다.
- 정확한 안내 문구/문의처/처리 기한/조회 간격은 별도 합의입니다. 생년정보 원문·세션 토큰 저장 금지.

## 4. Q-33·Q-36: 담당 답변/결정 대기

- Q-33: 취소/실패 분류·문구, 다시 결제 상품 유지, 필수 충전 동의/서버 기록은 PD+A 확정 필요.
  PG 원문 오류는 그대로 표시하지 않습니다. 현재 주문 API는 동의 저장을 구현하지 않습니다.
- Q-36: 가입 동의 기록/만 14세 확인은 B의 방향 제안이며 화면·최종 방식·구현은 PD+B 대기.
- 전달된 B 답변상 심사 전 카카오만, 심사 후 Google/네이버 추가. 정책은 실제 제공 범위와 맞춥니다.
- 자유 고민 입력은 현재 서버 수집/전송 없음. 정책 초안/사업자·CS·환불·보관 기간 빈칸은 임의 확정 금지.

## 5. FE 확인 및 회신 요청

- [ ] OpenAPI 타입과 FE 모델의 charged/funding/null/원화 amount 매핑을 대조했는지.
- [ ] 충전 예정량과 CREDITED 완료를 분리하고, 미구현 confirm/상품 API를 완료로 취급하지 않는지.
- [ ] 생성 오류만으로 환급 완료를 표시하는 코드와 처리 중 결과 이동을 수정할 계획이 있는지.
- [ ] 일반 결과 순서/궁합 enum을 반영할 수 있는지, 수능 구조화 DTO 중 필요한 필드를 B에게 전달했는지.
- [ ] B의 구매 상태 조회/재개 계약과 세션·CSRF·인물 연결을 기다리는 항목을 구분했는지.
- [ ] 실제 연결에 필요한 staging Base URL, 세션/CSRF 계약, 토스 테스트 클라이언트 키를 별도 요청했는지.
- [ ] 서버 준비 후 충전→견적 재조회→구매→결과/환급, 새로고침·응답 유실·타인 접근 E2E를 계획했는지.

현재 지갑 구매/복구는 기본 비활성이고 실서비스 연결 완료 상태가 아닙니다.
기존 PostgreSQL/H2 검증과 PR CI는 통과했지만 실제 FE+토스+로그인 전체 흐름 검증은 아직입니다.
위 체크 항목의 반영 가능/추가 계약 필요 항목을 구분해 회신 부탁드립니다.
