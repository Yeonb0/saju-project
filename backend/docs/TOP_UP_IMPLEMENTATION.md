# A 충전 구현과 검증 경계

## 구현

- GET /api/v1/products?category=TOP_UP: 로그인 없이 현재 판매 가능한 충전 상품의 서버 가격과 paidAmount/bonusAmount/creditedAmount를 반환한다. 판매 차단 상태에서는 items=[]다.
- 카탈로그 HTTP는 현재 TOP_UP만 지원한다. category 누락, FORTUNE/GIFT 및 fortuneType 조합은 400이며 전체 상품 카탈로그 구현 완료를 의미하지 않는다. 주문 DTO의 paidShellAmount 등과 필드명이 다르므로 FE adapter에서 구분한다.
- POST /api/v1/top-up-orders: 인증 UUID, CSRF, Idempotency-Key와 상품 코드로 주문을 생성한다.
- GET /api/v1/top-up-orders/{orderId}: 본인 주문 snapshot과 상태를 조회한다. 타인/없는 주문은 같은 404다.
- 가격/지급량은 CatalogUseCase의 판매 가능 상품에서 가져온다. 클라이언트 금액/수량을 저장하지 않는다.
- paidShellAmount/bonusShellAmount/creditedShellAmount와 amount/currency/orderName을 반환한다.
- PAYMENT_PENDING의 총 지급량은 예정량이다. 원장/lot/잔액을 변경하지 않으며 walletBalance도 반환하지 않는다.
- 실제 판매 상품 활성화는 하지 않았다. 현재 비활성 상품을 성공시키는 개발용 우회는 없다.

## 중복과 트랜잭션

top_up_orders의 사용자+키 SHA-256 unique가 동시 요청을 조정한다.
동일 상품은 같은 주문 snapshot을 반환한다. 가격/판매 상태가 바뀐 후에도 이미 생성된 주문을 재사용한다.
다른 상품은 409 IDEMPOTENCY_KEY_REUSED다. 키 scope는 충전 주문 생성이며 512자 제한이다.
생성의 INSERT는 5초 제한 독립 트랜잭션이다. 경쟁에서 진 INSERT의 롤백 후 새 트랜잭션으로 승자를 조회한다.
DB 저장 직후 HTTP 응답이 유실돼도 동일 키 재시도로 저장된 주문을 재사용한다.
이 단계에는 외부 호출/지갑 잠금/실제 자금 이동이 없다.

## 후속 개발

1. 저장 주문/사용자/금액과 paymentKey 대응 검증 및 승인 명령의 영속 중복 방지.
2. 토스 서버 adapter와 승인 결과 불명 조회/재처리. timeout을 FAILED로 확정하지 않는다.
3. 승인 확인 뒤 지급 lot/원장/잔액/주문 CREDITED를 한 짧은 트랜잭션으로 저장한다.
4. 승인 성공 후 서버 중단, 지급 실패, 동시 재승인/worker/webhook의 중복 지급 방지를 PostgreSQL에서 검증한다.
5. 테스트 키를 서버 환경으로 구성하고 실제 토스 테스트 승인/조회와 FE 연결을 별도로 검증한다.

현재 confirm/webhook/cancel/refund는 제공하지 않으며 TopUpUseCase 전체 구현이 완료된 것은 아니다.
토스 키나 실제 결제는 이번 단계에 사용하지 않았다. 지급 만료는 최신 확정 정책을 따라야 하며
ERD의 이전 180일 설명을 그대로 구현하면 안 된다(PENDING_DECISIONS/PRD의 보너스 1년과 차이가 있다).
요청/주문 기록 보존과 rate limit, 운영 알림도 후속이다.

## 직접 확인과 다음 순서 (2026-10-10)

로컬 백엔드 실행 후 Postman에서 GET http://localhost:8080/api/v1/products?category=TOP_UP을 보낸다.
이 조회는 인증과 CSRF 없이 가능하며 외부 PG를 호출하지 않는다. 현재 기본 판매 차단 상태의
정상 응답은 data.items=[]다. 빈 목록을 해결하려고 개발/운영 DB의 active를 임의로 바꾸지 않는다.
자동 테스트는 전용 테스트 DB에서만 상품을 활성화하고 테스트 종료 시 롤백해 6종 가격과 지급량을 확인한다.
상품 조회 값은 예약 가격이 아니며 주문 생성 시 현재 판매 상태를 다시 검사하고 주문 snapshot을 저장한다.

진행 순서는 상품 조회 -> 승인 요청의 영속 기록/검증 -> 토스 승인/조회 adapter ->
승인 확인 후 원자적 지급 -> 중복/응답 유실/중단 복구 PostgreSQL 테스트 -> 실제 테스트키 E2E다.
외부 토스 호출은 지갑 잠금 밖에서 하고, 최종 지급 lot/원장/잔액/CREDITED는 함께 커밋해야 한다.
토스와 DB는 한 번에 롤백할 수 없으므로 승인 결과 불명은 조회로 확인한다.

현재 확보한 키는 문서 예제용이며 본인 사업자 상점 키 확보/실제 승인 성공을 의미하지 않는다.
이번 상품 조회 단계에는 키가 필요 없고 키 설정이나 실제 결제 시험도 수행하지 않았다.
FE의 실제 adapter와 B의 구매 중단 복구 합의는 별도 작업이다.

## B 합의

충전 주문 개발은 B의 생성 상태 머신을 바꾸지 않는다. 구매 상태 조회/중단 재개/환급 완료 판정은
A_B_PURCHASE_HANDOFF.md의 제안을 B와 확정한 다음 연결한다.

## 테스트

TopUpOrderApiTest는 실제 DB와 보안 필터/HTTP를 사용하며 CatalogUseCase만 테스트 상품으로 대체한다.
snapshot/반복 요청/판매 종료 후 재전송, 키 충돌, 없는 상품, 소유권, 미인증/CSRF/UUID principal,
동시 요청의 주문 한 건, 사용자별 키 격리/해시 저장, 원장 미변경을 검사한다.
실제 토스 승인, 실제 상품 판매 활성화, OS 강제 종료, 다중 프로세스 부하는 검증하지 않는다.
실행별 실제 결과는 docs/OPERATIONS_CHECKLIST.md 9장에 기록한다.
