# PR 전 A/B/FE 연결 점검

확인일: 2026-10-09. 로컬 코드 `2b09ab7`, 원격 fetch 후 main `381787b`,
BE-B `origin/feat/saju-calculation` `81410c3`, FE `origin/boyeon` `0ab2472` 기준.
원격 B 브랜치와 main 사이 backend 차이는 없었다. FE 추가 1개 커밋은 목업 결과 구분,
구매 목업/충전 후 잔액 조회 보완이며 실제 API adapter 구현은 아니다.
원본 요청은 사용자가 전달한 Q-33~36 문서와 `frontend/docs/TEAM-QUESTIONS.md`다.
로컬 A 변경은 아직 원격 main에 없으므로 아래 '구현'은 배포/FE 반영 완료를 의미하지 않는다.

## 결론

- A 지갑 구현은 B가 사용하는 `WalletPurchasePort`를 구현한다. 인터페이스 연결은 있다.
- 실제 인증/인물 adapter는 없고 지갑 구매/복구는 기본 비활성이다. 실사용 연결 완료는 아니다.
- FE는 mock port만 선택하며 real 모드는 명시적으로 오류를 던진다.
- 최초 질문이 모두 해결된 것은 아니다. Q-34 대부분 구현, Q-35 부분 해결, Q-33/Q-36 결정 대기다.
- 운영 사용 승인 목적의 PR은 아직 부적절하다. 제한 사항을 적은 Draft 리뷰는 가능하나,
  이 점검에서는 push/PR/배포를 수행하지 않았다.

## 우선 수정할 연결 문제

### 1. 생성 오류를 환급 완료로 오인 (높음, A/B/FE)

`frontend/src/lib/reading/generation.ts:34`는 READING_GENERATION_FAILED 또는
CALCULATION_FAILED 오류만으로 refunded=true를 만든다. GenerationScene은 이를 받아
'사용한 등껍질은 돌려드렸습니다'를 표시한다. 최신 원격 FE에도 같은 코드가 있다.

B GeneralReadingService/SuneungReadingService는 생성 실패 후 복구 호출이 실패해도
READING_GENERATION_FAILED를 반환한다. 따라서 오류 code는 환급 완료 증거가 아니다.
새 FAILED worker도 실패/재시도/한도 초과 상태를 가질 수 있고 기본 비활성이다.
FE는 확인 전 환급 완료 문구를 숨기고, 서버의 소유자 구매 상태 조회로 REFUNDED를 확인해야 한다.
현재 전용 purchaseId 상태 GET API가 없어 A/B가 그 계약부터 정해야 한다.

B 즉시 보상 메서드는 wallet.compensate 반환 receipt를 검사하지 않고 markRefunded를 호출한다
(GeneralReadingService:221, SuneungReadingService:183).
현재 실제 A 구현은 정상 receipt를 반환하거나 예외를 던지지만 null/잘못된 receipt를 주는
adapter까지 안전하게 거절하는 보장은 즉시 경로에 없다. 새 worker는 이를 검사한다.
즉시 경로와 worker의 완료 판정도 함께 맞출 필요가 있다.

### 2. HTTP 재전송과 중단 복구가 미완료 (높음, A/B)

B debitKey는 원본 Idempotency-Key를 저장하지 않고 `reading-purchase:{purchaseId}`로 바꾼다.
A 내부 원장 중복 방지와 클라이언트 요청의 키+본문 일치 보장은 다른 문제다.
같은 HTTP 키로 서로 다른 quote를 보내는 경우를 거절하는 영속 계약은 아직 없다.

B는 견적 만료 검사를 기존 FULFILLED 구매 조회보다 먼저 한다. 완료 후 만료된 견적으로
재전송하면 기존 성공 대신 QUOTE_EXPIRED가 될 수 있다. 재전송 순서와 상태 조회를 정해야 한다.
DEBITED/GENERATING 구매는 재전송해도 상태만 반환하고 생성을 재개하지 않는다.
CREATED에서 차감만 커밋된 중단도 자동 회수 대상이 아니다.
FAILED worker는 이 상태들을 해결하지 않는다. 생성 lease/늦은 완료 차단 설계 없이
시간이 오래 걸렸다는 이유로 환급하면 환급 후 결과 제공 위험이 생긴다.

### 3. FE 구매 모델은 처리 중 응답을 표현하지 못함 (높음, B/FE)

FE ReadingPurchaseResult의 readingId:string, balance:number는 필수지만 B는 처리 중
readingId=null/balance=null을 반환할 수 있고 완료 재조회에서도 balance=null을 반환한다.
BE 응답에는 FE의 refunded 필드도 없다. 단순 필드 복사는 불가능하다.
ShellCheckout:168은 FAILED/REFUNDED 외 성공 응답을 onPurchased로 넘기므로,
adapter/화면에서 FULFILLED만 결과 이동, 나머지는 조회 대기로 구분해야 한다.
잔액이 없다고 0으로 채우거나 오류만으로 refunded=true를 만들어서는 안 된다.

### 4. 수능 sections는 FE의 구조화 sections가 아님 (중간, B/FE)

SuneungReadingController:157은 List<GeneratedSection>을 반환한다.
GenerationModels:88의 실제 항목은 `{key, content, sourceFactKeys}`이고 key는
EXAM_PERIODS/MEAL/PREPARATION 등이다. FE는 type/title과 교시별 items,
음식 primary/alternatives, 체크리스트 items/id 등을 기대한다.
배열이라는 사실만으로 PERIOD_GUIDANCE/FOOD_RECOMMENDATION/CHECKLIST 계약이 맞지는 않는다.
본문 문자열을 FE에서 임의 파싱하지 말고 B의 구조화 DTO 또는 텍스트 표시 축소 계약을 확정한다.

### 5. 인물/상품/결과 메타데이터 추가 계약 (중간, A/B/FE)

FE FortuneSelection에는 questionKey/relationType/productOption이 없지만 일반 B API에는 필요하다.
상품 코드에서 종류/옵션을 매핑하는 adapter와 선택 질문/관계 데이터 전달 경로가 필요하다.
FE FortuneQuote.productName을 채울 서버 metadata와 GET /products HTTP도 아직 없다.
FE Reading.birthTimeUnknown은 현재 결과 DTO에 없으며 원본 인물 정보를 다시 가져오는 것과
구매 당시 결과 snapshot에 기록하는 것은 다르므로 B와 원천을 정해야 한다.
subjectDisplayName -> personDisplayName, charged -> FE price, 일반 고정 필드 -> sections 변환도 필요하다.

## 최초 FE 질문별 판정

상태의 '구현'은 로컬 코드 기준이다. '결정 대기'는 개발자가 임의 확정하지 않는다.

| 질문 | 판정 | 현재 답과 남은 일 |
|---|---|---|
| Q-34.1 자금 필드 | 구현 | 일반 5종/수능에 walletBalance, balanceAfter, shortage, recommendedTopUp. 부족 시 balanceAfter=null, 추천 없으면 recommendedTopUp=null. 실제 FE adapter는 없음 |
| Q-34.2 차감량 통일 | 구현 | 모두 charged {currency, amount}. FE 모델 price로 변환 필요 |
| Q-34.3 견적 GET | 구현 | GET /api/v1/quotes/{quoteId}. 소유자 검사/만료 검사, 현재 자금 재계산. 원장 변경 없음 |
| Q-34.4 충전 지급량/지갑 | 부분 | GET /wallet의 paidBalance/bonusBalance/balance는 구현. 충전 주문 생성/승인/지급 HTTP와 완료 수량 응답은 없음 |
| Q-34.5 500+80 | 수정 | API_SPEC 500+80=580. 상품은 실제 판매 준비 전 비활성 |
| Q-35.1a 결과 화면 순서 | 부분 | B 고정 필드/명세 순서는 확인 가능. JSON 키 순서에 의존하지 않는 FE 명시 매핑 필요. PD 화면 순서 승인까지 확인한 것은 아님 |
| Q-35.1b section 모양/null | 코드 확인 | 일반 결과의 section 값은 content/sourceFactKeys. meta/counterpart는 별도 구조. 누락 section은 controller에서 null로 매핑 |
| Q-35.1c summary 요약 상자 | 부분 | summary 필드는 있음. PD 제목/요약 상자 표현 확정은 별도 |
| Q-35.1d 수능 section 배열 | 불일치 | 배열은 맞지만 FE 구조화 타입/items와 다름. 위 4번 계약 협의 필요 |
| Q-35.2 relationType | 대기 | FAMILY/FRIEND/LOVER/CRUSH/WORK_SCHOOL/OTHER enum 유지. 엄마/아빠/직접 입력 매핑 및 문자열 전송 계약 미확정 |
| Q-35.3 personId basic | 입력 구현/연결 대기 | {personId} 단독 지원. raw 생년정보와 혼합 금지. 실제 ReadingSubjectPort 구현체 없어서 real 호출은 503 |
| Q-35.4a 503/같은 키 재시도 | 부분 | 현재 의존성 누락이면 차감 전 503. debit가 null인 경로도 같은 code를 쓰므로 code 전체를 무차감으로 일반화 불가. HTTP 원본 키 계약/상태 조회 미완료 |
| Q-35.4b 차감 불일치/복구 안내 | 미완료 | 오류만으로 환급 완료 표시 금지. 실제 원장 복구와 상태 확인 필요. 복구 실패 시 안내/조회 계약 미완료 |
| Q-35.4c 절기 시간 오류 | 불일치 | basic(raw/personId)는 BIRTH_TIME_REQUIRED_AT_TERM. 구매는 같은 예외를 UNSUPPORTED_CALENDAR_DATE로 바꿈. 견적은 계산을 하지 않아 이 검사를 수행하지 않음 |
| Q-35.5 OpenAPI/스테이징 | 부분 | docs/openapi/api-v1.json 제공 및 export task 구현. staging 주소/제공일 확인 안 됨 |
| Q-33.1 토스 원문 대신 우리 문구 | 결정 대기 | PD 승인/검수 문구 없음. 안전한 PG 오류 매핑 구현도 후속 |
| Q-33.2 취소/실패 분류와 문구 | 결정 대기 | CANCELED만 취소로 볼지, 완료/확인 중 문구 포함 확정 필요 |
| Q-33.3 다시 결제 상품 유지 | 결정 대기 | URL 상품 코드 유지 제안에 대한 최종 결정 필요 |
| Q-33.4 필수 동의/서버 기록 | 결정 대기 | 필수 차단 여부/원고/동의 기록 범위 확정 후 A 구현 |
| Q-36.1 가입 동의 | 결정 대기 | PD 화면 위치, B 동의 version/time 기록 계약 필요 |
| Q-36.2 만 14세 | 결정 대기 | 서버 연령 검사인지 체크인지, 본인 정보 검증 범위 확정 필요 |
| Q-36.3 소셜 로그인 표기 | 결정 대기 | 심사용 카카오만 표기인지 초안 3종 유지인지 승인 필요 |
| Q-36.4 고민 수집/Liner | 결정 대기 | F-04 변경 확정 전 실제 수집/전송을 임의 추가하지 않음 |
| Q-36.5 정책 빈칸 | 결정 대기 | 사업자/시행일/CS/보호책임자/환불 산식과 기간/위탁사/보관 기간 확정본 필요 |

frontend TEAM-QUESTIONS는 여전히 Q-33~36 대기다. 이번 로컬 구현만으로 타 파트의
질문 상태를 해결로 덮어쓰지 않았다. FE 인계 시 구현된 답과 남은 질문을 함께 전달해야 한다.

## 트랜잭션과 실제 검증 범위

A 차감은 지급분/원장/잔액/영속 receipt를 같은 짧은 독립 DB 트랜잭션에 저장한다.
외부 Liner 호출은 그 밖에서 B가 수행한다. B 구매 상태 저장은 A 차감과 별도 커밋이다.
즉 돈 저장 직후 서버가 멈추면 구매 상태가 이전 단계에 남을 수 있다.
FAILED 복구는 purchase -> wallet -> lot 잠금 순서로 실행하고 역분개를 재사용하지만,
CREATED/DEBITED/GENERATING 중단 복구나 전체 외부 생성 E2E를 보장하지 않는다.

이번 재실행: H2 전용 test 프로필에서 아래 5개 suite 총 44건, 통과 44, 실패/오류/제외 0.
QuoteWalletApiTest 9, BasicSajuApiTest 10, GeneralReadingApiTest 6,
SuneungReadingApiTest 8, FailedPurchaseRecoveryTest 11.
일반/수능 API 테스트의 WalletPurchasePort/인물/Liner는 mock이다.
복구 suite는 실제 A 지갑 구현을 사용한다. 테스트 통과는 위 불일치 해결의 증거가 아니다.
이번에는 실제 PostgreSQL, FE 테스트/브라우저 E2E, 운영 배포/트래픽/PG/Liner live를 재검증하지 않았다.
기존 PostgreSQL 검증 기록은 OPERATIONS_CHECKLIST 9장에 별도로 남아 있다.

## PR 전 진행 권장 순서

1. A/B/FE: 환급 완료 판정과 소유자 구매 상태 조회/처리 중 응답/null 계약 확정.
2. A/B: 원본 HTTP 키+본문 영속 멱등성, 만료 후 완료 재조회, 중단 재개/lease/늦은 완료 차단.
3. B/FE/PD: 수능 구조화 결과, 일반 필드 순서/요약, relationType, 절기 오류 code 통일.
4. B: 인증 principal UUID, 세션/CSRF 발급/검증, 인물 adapter. A: 상품 HTTP/metadata 및 충전 승인/지급.
5. FE: OpenAPI 생성 타입과 실제 adapter. 충전->견적->구매->조회/복구 staging E2E.
6. PD/운영: Q-33/Q-36 확정본. A/B 상호 리뷰 후 기능 활성화 여부를 별도 판단.

점검 적용 항목: 6-1 인증/소유권, 6-2 돈/중복/커밋, 6-4 상태/복구,
6-6 기본 비활성/배포 범위, 6-8 상태 확인, 6-9 개인정보/정책 계약.
