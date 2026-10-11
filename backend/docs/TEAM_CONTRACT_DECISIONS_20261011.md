# FE/B 회신 반영: 확정 기준과 남은 작업

확인일: 2026-10-11.
근거: 사용자가 전달한 `뿌기사주-FE-회신-20261010.md`, `A_B_PURCHASE_HANDOFF_REPLY_B.md`.
코드 대조: 로컬 A `eee0128`, fetch한 origin/main `f704145`, origin/boyeon `1fcd9c0`,
origin/feat/saju-calculation `81410c3`. 브랜치 병합/배포나 테스트 재실행은 하지 않았다.

사용자의 회신 확인·미확정 정리 요청에 따라 B안을 A 측에서 수용하는 항목을 기록한다.
계약 채택, 각 담당의 구현, FE의 실제 연결, 운영 준비 완료는 서로 다르다.
아래 보완안은 B/FE에 다시 전달해야 하며 아직 상대가 승인한 것으로 표시하지 않는다.

## 1. 확정한 책임과 원칙

| 항목 | 채택 기준 | 구현 상태 |
|---|---|---|
| 구매 상태·GET·중단 재개 | B 담당 | 실제 GET/재개 미구현 |
| 생성 lease·단조 증가 fence·늦은 결과 차단 | B 담당, 시간 초과만으로 환급하지 않음 | 미구현 |
| 지갑 원장·차감/보상 receipt·연결 검증 | A 담당, B는 지갑 테이블 직접 접근 금지 | 쓰기/replay 구현, 공개 읽기 포트 후속 |
| 보상 오케스트레이션·scheduler·REFUNDED 전이 | B 담당 | 기존 FAILED 복구 서비스 있음, 계약 보완 후 B 유지 |
| FE 환급 표시 | 오류 code가 아니라 확정 REFUNDED로만 표시 | FE main의 오류 단정 제거 확인 |
| 결과 이동 | FULFILLED이고 readingId가 있을 때만 | FE main 구현 확인 |
| 응답 유실 | 새 구매보다 기존 purchase/quote 조회 우선 | 실제 구매 조회 및 브라우저 재접속 복구 후속 |
| 잔액 | 현재 잔액은 GET /wallet, receipt 잔액은 과거 커밋 snapshot | 지갑 GET 구현 |
| 궁합 관계 매핑 | 엄마/아빠=FAMILY, 애인=LOVER, 직접 입력=OTHER | enum 사용 가능, 원문 전송 안 함 |

현재 `FailedPurchaseCompensationService`/scheduler는 PR #17에서 A가 작성했지만
이후 유지 책임은 B로 채택한다. 이번 문서 작업으로 코드를 옮기거나 제거하지 않았다.
`PURCHASE_DEBIT_MISMATCH`도 code만으로 환급 완료를 약속하지 않는다.
B의 즉시 보상 경로는 현재 반환 receipt를 검증하지 않으므로 보완 후 REFUNDED를 확정해야 한다.

## 2. Q-38: A가 수용한 B 조회 계약

- GET /api/v1/reading-purchases/{purchaseId}
- GET /api/v1/reading-purchases/by-quote/{quoteId}
- 사용자 UUID+구매 소유권 검사, 타인/없는 자원 동일 404 RESOURCE_NOT_FOUND.
- 기존 본인 구매 조회/replay는 quote 만료와 무관하다. 만료 검사는 최초 구매에만 적용한다.
- 성공 envelope는 기존 `{data, traceId}`를 유지한다. 새 GET DTO는 기존 구매 POST DTO와 별개다.

| 필드 | 계약 |
|---|---|
| purchaseId / quoteId | 기존 구매/견적 ID |
| status | CREATED / DEBITED / GENERATING / FULFILLED / FAILED / REFUNDED |
| readingId | FULFILLED일 때만 값, 그 외 null |
| compensationStatus | NOT_REQUIRED / PENDING / COMPLETED / REVIEW_REQUIRED |
| failureCode | 안전한 오류 코드 또는 null; provider 원문 금지 |
| nextAction | POLL / VIEW_READING / CONTACT_SUPPORT / NONE |
| nextRetryAt | 서버가 실제 예약한 재시도 시각 또는 null |
| updatedAt | 마지막 확정 상태 저장 시각 |

잔액은 이 DTO에 넣지 않는다. `REFUNDED`는 원거래와 연결된 보상 receipt 확인 후에만 가능하다.
새 조회 응답의 `REFUNDED`와 `COMPLETED`는 함께 반환한다. 모순된 조합은 FE가 환급 완료로 표시하지 않는다.
수능/일반 구매 모두 같은 조회 계약을 사용한다. OpenAPI에 없는 지금의 GET을 FE가 호출하지 않는다.

polling 기준은 B 권고를 A 구현 기준으로 수용한다: 최초 30초는 2초, 이후 5초 간격으로
시작부터 총 2분까지. 종료 시 실패/환급으로 단정하지 않고 수동 상태 조회를 제공한다.
FULFILLED/REFUNDED+COMPLETED는 종료, FAILED+PENDING은 서버 nextRetryAt 고려,
REVIEW_REQUIRED/CONTACT_SUPPORT는 자동 조회 종료 후 purchaseId와 문의 경로 제공이다.
이 시간은 운영 성능 보장/SLA가 아니며 FE에서 실제 동작을 확인해야 한다.

### B/FE에 재확인이 필요한 세부 조건

- B의 저장 제안은 quoteId+최초 키만 유지하므로, 선택 본문을 복원할 수 없으면 POST를 임의 재구성하지 않는다.
  현재 FE는 충전 복귀용 최소 인물 ID/상품 선택도 저장한다. 충전 복귀와 구매 응답 유실을 구분하고
  최종 저장 schema/만료/로그아웃 정리 정책을 FE+B가 맞춰야 한다. 생년 원문/이름/세션 토큰 저장은 금지다.
- by-quote 404/조회 실패만으로 새 키·새 구매를 자동 생성하지 않는다. FE에 원래 선택이 없다면 GET 확인 경로를 사용한다.
- 현재 fence의 상태 불명이 GENERATING에 남는 경우에도 REVIEW_REQUIRED/CONTACT_SUPPORT를 반환할지,
  차감 없는 FAILED의 NOT_REQUIRED/NONE 조합을 포함해 전체 status/compensationStatus/nextAction 조합을 B가 명시한다.
- 문의처, 안내 문구, 관리자 알림 수신자/재처리 권한/처리 기한은 아직 미정이다.

## 3. A receipt 읽기 포트: 수용과 보완

B의 CREATED 복구를 위해 읽기 전용 포트를 A가 제공한다. 내부 repository 조회는 이미 있으나
B가 사용할 application port/adapter는 아직 없다. 없는 receipt는 Optional.empty이며 새 지갑/원장을 만들지 않는다.
조회 시 quote 만료/현재 판매 상태로 이미 커밋된 receipt를 숨기지 않는다.

B 원안: findDebit(userId, quoteId), findCompensation(originalTransactionId).
A 보완안: 보상 조회도 `(userId, quoteId, originalTransactionId)`를 받아
사용자·견적·차감 원거래·역분개 연결을 검증한다. B 승인 전 공동 확정 서명으로 취급하지 않는다.
반환 receipt에는 검증용 소유자/견적/원거래/거래 ID/금액이 필요하며 잔액은 현재 값으로 사용하지 않는다.
반환 record의 최종 이름/필드와 불일치 시 오류 계약을 A 구현 PR 전에 B와 맞춘다.

## 4. Q-28: A가 정할 수 있는 필드 기준

A 측 개발 기준으로 `productName`(서버 표시명), `regularPrice`({currency, amount} 또는 null)을 선택한다.
기존 상품 `price`/견적 `charged`는 실제 판매·차감 금액이며 regularPrice로 차감하지 않는다.
확정 P-03 기준 사주+부적/수능운의 정가는 등껍질 20개, 판매가는 15개다.
확인되지 않은 정가를 FE가 더하거나 추정하지 않으며, 미확정 상품 regularPrice는 null이다.
견적 생성 당시 이름/정가도 snapshot으로 보존하는 방향이다.

이 필드는 10/11 A 로컬에서 구현했다. 일반/수능 견적 POST와 GET에 동일하게 추가하며 OpenAPI를 재생성한다.
구체적인 표시명 문구는 PD 확인 대상이다. A가 정가 정책 자체를 새로 만든 것이 아니다.
상품 조회는 비로그인 허용 방향을 유지한다. `/products?category=TOP_UP|FORTUNE`을 로컬 구현했고
FORTUNE의 fortuneType 필터/option/판매 종료 시각을 제공한다. 비활성 상품을 /about에 어떻게 표시할지는 후속 계약/구현이다.
구매 가능한 목록과 심사용 소개 목록의 판매 가능 여부를 혼동하지 않는다.

## 5. 아직 확정할 수 없는 것

| 항목 | 필요한 회신/행동 |
|---|---|
| Q-37 수능 구조화 결과 | B+PD가 type/items/primary/alternatives/id를 제공할지, 1차 TEXT 한정일지 결정. FE 요청만 있고 승인 없음 |
| 절기 경계 오류 통일 | B가 basic과 유료 구매의 오류 분류를 결정. 그 전 FE는 현재 두 code 처리, A가 B 코드를 임의 수정하지 않음 |
| Q-31 오늘의 운세 | B 구현/API_SPEC/OpenAPI 대기 |
| R-06/R-04 staging | 고정 FE/BE 도메인, 접근권한, 실제 세션/CSRF/프록시 설정과 기동 확인 필요. 주소/제공일 미확정 |
| R-09 키 | A는 문서 예제 키만 확인함. 팀 상점 테스트 클라이언트 키/담당 계정 권한과 SDK 종류 FE 인계 후 실제 시험 필요 |
| Q-33 | 실패/취소 문구·재결제 상품 유지·충전 동의 필수 여부/버전/서버 기록 PD+A 확정 필요 |
| Q-36 | 카카오만 고지, 미제공 고민 수집/전송 문구 제거 방향. 최종 정책/14세 확인·가입 동의는 PD+B |
| 신살 판매·부적 기준·질문 문구 | FE 문서의 PD 항목 그대로 PD+B 결정 필요 |
| 사업자/통신판매업/심사 | 미완성 푸터·CS 정보 및 신고/면제 적용·충전형 상품 계약은 사업자 담당/관할기관/PG 확인 필요. 법적 적합성 이번에 판단하지 않음 |

10/12 심사용 사이트, 10/13 계약 제안, 10/14 전후 심사 요청은 전달받은 목표/제안이다.
B도 완료일을 확약하지 않았으며 이 문서에서 확정 일정이나 배포 성공으로 바꾸지 않는다.

## 6. 현재 개발 위치

- origin/main f704145: FE PR #18 병합. basic/충전 주문 생성·지갑 adapter, 타입 생성,
  오류만으로 환급 단정 제거, FULFILLED+readingId 이동 조건이 들어왔다.
- origin/boyeon 1fcd9c0: 수능 견적/구매 adapter 추가. main 미병합 단계다.
- 실제 port 선택은 아직 mock이며 세션/인물/결제창/전체 구매 실제 연결 완료가 아니다.
  충전 adapter의 상품/confirm/완료 주문 조회는 계약 대기로 명시되어 있다.
- origin/main의 backend/docs는 PR #17 이후 변경 없음. B 조회·fencing·세션/인물은 현재 확인 범위에서 미구현이다.
- 로컬 A: 충전 상품 TOP_UP 조회, 승인 요청 대응 저장, 테스트 전용 토스 승인/조회 adapter까지 구현.
  main에는 아직 없고 실제 토스 승인·결과 영속화·지급·자동 대사는 미구현이다.
- 로컬 A의 이전 검증: PostgreSQL 353건 중 통과 350/실패 0/선택 제외 3, 토스 로컬 시험 40건 통과.
  이는 이전 실행 결과이며 이번 회신 검토에서 테스트/E2E를 다시 실행하지 않았다.

## 7. A 할 일 순서와 완료 판정

1. A receipt 조회 포트 보완 서명을 B에 전달하고 구현한다. 읽기만 수행, 타인/불일치 거절,
   견적 만료 후 재조회, 차감 커밋/B 저장 중단, 보상 커밋/B 갱신 실패를 PostgreSQL로 확인한다.
2. Q-28 상품명/정가/운세 카탈로그를 구현하고 추가형 migration·견적 snapshot·OpenAPI를 반영한다.
   B 견적 DTO와 FE 생성 타입을 함께 대조한다. /about 목록 정책은 PD/FE와 확정한다.
3. 충전 승인 실행 상태/점유/결과 영속화와 bounded 대사를 연결한다.
   외부 토스 대기는 DB 잠금 밖에서 처리하고 UNKNOWN은 새 결제 대신 원주문 조회로 확인한다.
4. 승인 확인 뒤 lot/원장/잔액/주문 CREDITED를 한 트랜잭션으로 지급한다.
   동시 confirm/worker/webhook, 응답 유실·지급 실패·중단에서 지급이 한 번인지 검사한다.
5. 충전 HTTP 처리 중/완료 DTO와 동의 계약을 FE에 전달한다. 동의 정책 미정 상태로 판매를 활성화하지 않는다.
6. 위와 병행해 고정 staging·테스트 상점 키·B 실제 인증을 준비한다. 준비 없이 URL/날짜를 약속하지 않는다.
7. B 조회/재개 PR과 A 기능 PR 병합 후 FE 실제 adapter를 연결하고
   로그인→충전→견적→구매→결과/복구 E2E, 타인 접근/중복/새로고침/늦은 결과를 함께 검증한다.

실행 lease와 자동 복구가 없는 지금은 WALLET_PURCHASES_ENABLED/WALLET_RECOVERY_ENABLED를 유지한다.
이번 작업은 회신 반영 문서 작업이며 B 상태 머신/FE 코드 수정, push/PR/배포를 하지 않았다.
