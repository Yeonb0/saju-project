# FE 전달: BE-A 완료 항목과 연결 확인

기준: 2026-10-11, main `8fd90e8` / [구현 PR #19](https://github.com/Yeonb0/saju-project/pull/19).
재화 표시명은 **등껍질**이다. 코드 병합은 서버 배포나 실제 결제 검증 완료를 의미하지 않는다.
이 문서는 FE 10/11 요청서의 A 1~4번과 이미 완료된 A 공통 계약만 안내한다.

## 요청별 회신

| FE 요청 | A 회신 | FE가 진행할 것 |
|---|---|---|
| 1. Q-17 충전 승인·주문 조회 | 구현 및 main 병합 완료. 승인 요청, 처리 중 여부, 지급 수량·지급 후 잔액 제공 | 승인·조회 adapter와 완료 화면 연결 |
| 2. Q-28 상품 목록·견적 상품명·정가 | 구현 및 main 병합 완료 | 서버 상품 코드·옵션·가격으로 목록·견적 화면 연결 |
| 3. R-09 테스트 클라이언트 키 | 사용자가 FE에 직접 전달 완료. 원문은 이 문서에 넣지 않음 | 로컬 환경 설정 및 서버 시크릿과 동일 상점·키 세트인지 확인 |
| 4. R-01/Q-25 상담 결과 | 아직 상담 전. 개발·테스트 우선 진행으로 확정 | 상품 분류·결제수단·신고 면제 관련 내용을 확정된 사실로 표시하지 않음 |
| Q-18 및 작은 계약 차이 | 필수 Idempotency-Key, 공통 오류 응답, ApiError.details 구현 및 main 병합 완료 | HTTP 오류는 code로 분기하고 details의 존재를 완료 증거로 사용하지 않음 |
| Q-38 f A receipt/책임 분리 | A 원장 조회·복구 포트 구현 및 main 병합 완료, B 오케스트레이션 소유 수락 | B 구매 상태·nextAction 계약과 연결. A 포트 완료만으로 B 연결 완료로 보지 않음 |

5번 Q-20 선물은 이번 전달·PR 범위에서 제외한다. 별도 개발 브랜치의 선물 코드는 main에 포함되지 않았다.
충전 동의 필수 여부·문구·버전 및 서버 기록, 문의 채널·푸터·정책은 별도 확정/후속 항목이며 완료로 표시하지 않는다.

## FE 연결 계약

### 상품과 견적

- 비로그인 `GET /api/v1/products?category=TOP_UP` 또는 `?category=FORTUNE&fortuneType=SUNEUNG`.
- 응답은 `data.items`. 현재 판매 가능한 상품만 반환하므로 빈 목록에 임의 상품을 보충하지 않는다.
- 목록의 `option`은 견적 요청의 `productOption`에 대응한다. `productName`, `price`, `regularPrice`, `saleEndsAt`을 사용한다.
- 일반 운세·수능 견적의 `productName`과 `regularPrice`는 견적 발급 당시 snapshot이다. 기존 견적의 정가는 null일 수 있다.
- `regularPrice`는 할인 전 표시용이다. 실제 결제·차감 금액은 서버의 주문/견적 가격을 사용하고 FE에서 재계산하지 않는다.
- 상품 기본 비활성 상태는 유지한다. API 구현 완료와 테스트 환경 상품 활성화는 별개다.

### 충전

1. `POST /api/v1/top-up-orders`: 본문 `{ "productCode": "상품 코드" }`, 생성용 `Idempotency-Key`.
2. 반환된 `orderId`, `orderName`, `amount`, `currency`로 토스 결제창을 연다.
3. `POST /api/v1/top-up-orders/{orderId}/confirm`: 본문 `paymentKey`, `orderId`, `amount`와 승인용 `Idempotency-Key`를 보낸다. `amount`는 문자열이 아닌 양의 정수 JSON 숫자다.
4. 승인 재전송에는 같은 키와 같은 본문을 유지한다. 생성용과 승인용 키는 별도 명령의 키다.
5. 응답 유실·새로고침·처리 중에는 기존 주문 ID로 `GET /api/v1/top-up-orders/{orderId}`를 조회한다. 새 주문이나 재결제를 자동 실행하지 않는다.

쓰기 API는 로그인과 CSRF가 필요하며, 타인의 주문은 404다. 로그인·세션·CSRF 구현은 B 범위다.

| 응답 | FE 처리 |
|---|---|
| CREDITED / VIEW_WALLET | 완료 화면. grantedShellAmount와 walletBalance 사용 |
| PAYMENT_PENDING / POLL | 처리 중 안내 후 기존 주문 조회 |
| PAYMENT_PENDING / CONTACT_SUPPORT | 자동 확인 한도 초과. 주문 ID로 문의, 결제 실패/환불 완료로 단정하지 않음 |
| FAILED 또는 CANCELED / RETRY_PAYMENT | 확인된 거절/취소. PD 문구와 사용자 선택에 따라 다시 결제 |
| PAYMENT_PENDING / NONE | 주문 생성만 완료, 승인 실행 전 |

**충전 완료는 CREDITED일 때만 표시한다.** HTTP 200, PG 복귀, 예정 지급량만으로 완료 처리하지 않는다.
`paidShellAmount`, `bonusShellAmount`, `creditedShellAmount`는 예정량이다. `grantedShellAmount`와 `walletBalance`는 CREDITED 이전 null이다.
`walletBalance`는 지급 당시 snapshot이므로 현재 잔액은 `GET /api/v1/wallet`로 갱신한다.
승인 응답 202 또는 `processing=true`는 미완료다. 주문의 `failureCode`와 HTTP 오류의 `code`는 구분한다.

## 키와 실제 테스트 준비

- 클라이언트 키는 FE 전달 완료지만, 실제 결제창·승인·지급의 통합 테스트는 아직 하지 않았다.
- 현재 BE adapter의 허용 종류는 `test_gsk_` 시크릿이며, FE는 같은 상점·세트의 `test_gck_` 클라이언트 키를 사용한다. 개별 연동 키와 섞지 않는다.
- 시크릿은 서버의 `TOSS_SECRET_KEY`에만 설정한다. 클라이언트 코드·채팅·문서·PR·로그에는 넣지 않는다. 최초 문서 작성 때는 비밀 설정을 읽지 않았으며, 이후 사용자 요청으로 아래와 같이 존재/형식만 비출력 확인했다.
- 전달된 키가 문서 예제용인지 팀 상점용인지는 별도로 확인해야 한다. 키 전달 자체는 실제 상점 연결이나 계약 승인 증거가 아니다.
- 실제 키 설정·실제 토스 시험 전에는 사용자와 안전한 로컬 설정을 확인한다. 현재 기본 adapter 비활성과 운영 활성화 차단은 유지한다.

현재 확인한 main의 FE `topUp.ts`에는 `listTopUpProducts`, `confirm`, `getOrder`가 계약 부재를 이유로 오류를 던지는 자리표시자로 남아 있다.
BE 계약은 PR #19로 제공됐으므로 FE 담당자가 이 세 메서드를 실제 adapter로 연결해야 한다. 다른 FE 작업 브랜치의 진행 상태는 확인하지 않았다.
계약 원본은 `docs/openapi/api-v1.json`, 상세 충전 안내는 `FE_TOP_UP_DELIVERY_20261011.md`다.

## 함께 확인할 실제 결제 테스트

전용 테스트 사용자·테스트 DB·테스트 키만 사용하고 주문 ID로 결과를 대조한다.

1. 정상 결제: CREDITED, 지급 수량, 지갑 잔액, 지급 원장이 일치하고 지급이 한 번인지 확인.
2. 승인 재전송·새로고침: 동일 주문으로 재개되고 추가 지급·자동 새 주문이 없는지 확인.
3. 응답 지연·유실: 처리 중 안내와 기존 주문 조회로 수렴하는지 확인. 고의 지연은 통제된 테스트 서버에서 재현하고 실제 토스에 과도한 요청을 보내지 않는다.
4. 취소·거절·금액 불일치: 완료 화면을 표시하지 않고 예정량/null을 실제 지급량으로 바꾸지 않는지 확인.
5. 타인 주문·CSRF·로그인: 접근이 거절되고 키·paymentKey·원문 오류가 로그/분석/화면에 노출되지 않는지 확인.

테스트 환경의 BE 기동·상품 활성화·같은 키 세트 설정, B 로그인/CSRF 및 FE adapter가 준비돼야 실제 브라우저 시험이 가능하다.
PG 상담·승인·운영 배포는 이 테스트와 별도다. 상담 결과와 운영 준비 완료 날짜를 임의로 약속하지 않는다.

## 검증 범위

기존 PR #19에 PostgreSQL 중복 지급·동시 요청·DB 실패 롤백·승인 증거·복구·권한 검사 결과가 기록돼 있다.
이번 문서 PR 기준 main `8fd90e8`을 별도 PostgreSQL 17.11 DB에서 재검증했다.
`check bootJar --rerun-tasks` 성공: 총 383건 중 380건 통과, 실패/오류 0, 선택 실행 제외 3건. migration 18개 모두 성공했다.
선물 브랜치의 413건 검증 수치를 이번 main 완료 근거에 섞지 않는다. 운영 체크리스트에도 같은 결과를 기록했다.
실제 토스 결제, FE 브라우저 통합 시험, staging/운영 배포는 수행하지 않았다.

### 키 저장 후 FE 제외 재검증 (2026-10-11, PR #21)

사용자가 서버 키 저장을 알린 뒤 `b81076c`에서 재검증했다. `.env`의 시크릿 존재/지원 테스트 키 형식만 boolean으로 확인했으며 값은 출력하거나 커밋하지 않았다. adapter는 disabled, 자동 복구는 false를 유지했다. 이 확인은 토스에서 해당 키로 인증에 성공했다는 의미가 아니다.

- 다른 브랜치 migration이 있는 기존 테스트 DB를 보존하고 새 `sajuppugi_test_pr21_20261011` (55432, PostgreSQL 17.11)에서 전체 `check bootJar --rerun-tasks`를 실행했다. 383건 중 380 통과, 실패/오류 0, 선택 실행 제외 3. migration 18개 모두 성공했다.
- 충전/토스 관련 97건 모두 통과했다. 가짜 PG와 실제 DB로 정확한 지급·원장·잔액, 재전송/동시 요청의 중복 지급 방지, 금액/권한/CSRF 거절, 응답 유실/lease/지급 실패 롤백 뒤 조회 복구, 확인 한도 초과, 거절/취소의 지급 없음을 확인했다.
- 실제 local 서버에서 health/readiness 200/UP, 비활성 상품 조회 200/items=[], 미로그인 지갑/주문 조회 401, CSRF 없는 생성/승인 POST 403을 확인했다. OpenAPI의 주문 생성/승인/조회 경로도 확인했다.
- local 기동으로 기존 개발 DB(5432)에 미적용 migration 8개가 적용되어 현재 18개 모두 성공 상태다. 충전 상품 6종은 비활성, 주문/지급 receipt는 0건이며 실제 결제를 생성하지 않았다. 최종 확인에서 최초 백그라운드 local 서버가 8080에서 실행 중이다.

PG 호출 중에는 DB 잠금을 잡지 않고, 검증된 승인 뒤 지급분·원장·잔액·receipt·주문 CREDITED를 함께 저장한다. 재전송은 추가 지급하지 않으며, 결과 불명은 기존 주문 조회로 확인한다. 자동 복구는 local에서 꺼져 있고 테스트에서만 제어된 시각과 poll 호출로 검사했다.

남은 항목은 실제 토스 키 인증·승인·조회/상점·FE 키 세트 일치, B 로그인·CSRF 발급, FE 결제창과 완료/취소 화면, 실제 프로세스 종료 복구다. 인증 사용자 주입 기반 자동 테스트는 실제 로그인 검증이 아니다. 이 결과를 실제 결제 완료·FE 연결 완료·운영 준비 완료로 표시하지 않는다.
