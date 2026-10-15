# 견적번호 기반 고객센터 조회

## API와 권한

`GET /api/v1/admin/purchase-quotes/{quoteId}`는 운영자 DB grant와 ACTIVE 회원 상태가 있는 세션만 사용할 수 있다. `ADMIN_OPERATIONS_ENABLED` 기본 비활성은 유지한다. 일반 사용자 역할 문자열로는 접근할 수 없다. 정확한 UUID를 받으며 없는 견적은 404, 잘못된 UUID는 400이다.

조회와 감사는 같은 REPEATABLE_READ 트랜잭션에 있다. 감사 저장이 실패하면 결과를 응답하지 않는다. 원장 변경, 생성 재개, 반환 또는 현금 환불 명령은 없다.

## 응답 의미

- 견적: quoteId, buyerId, productCode, category, priceCurrency, quotedAmount, createdAt, expiresAt. 만료된 견적도 문의 조회가 가능하다. 현재 상품 가격 대신 당시 스냅샷을 표시한다.
- boundReferenceId: A 견적의 내부 연결 참조. BE-B의 운세 purchaseId라고 단정하지 않는다.
- giftOrderId: 해당 견적으로 만든 선물 주문이 있을 때만 제공한다.
- financialState: `NO_DEBIT_RECEIPT`, `DEBIT_COMMITTED`, `RETURN_COMMITTED`. A의 영속 차감/반환 receipt를 기준으로 하며 생성 성공·상품 제공·PG 승인/현금 환불 상태가 아니다.
- debitTransactionId/debitedShells/debitedAt, returnTransactionId/returnedShells/returnedAt: 존재하는 확정 원장만 표시한다. 차감 전 또는 반환 전 필드는 null이다. 금액은 서버 원장에서 읽으며 현재 지갑 잔액이나 과거 receipt 잔액을 현재 잔액으로 보여주지 않는다.

context hash, 요청/멱등 hash, 개인정보/결과 본문, token/URL/암호문/키는 반환하지 않는다. 감사의 action은 `ADMIN_PURCHASE_QUOTE_VIEW`, targetType은 `PURCHASE_QUOTE`, targetId는 quoteId, reasonCode는 `SUPPORT_VIEW`다.

## 범위와 통합

A 소유 purchase_quotes, wallet_purchase_debits/compensations, wallet_transactions, gift_order_drafts만 읽는다. BE-B의 reading_purchases/readings나 생성 테이블을 직접 조회하지 않는다. purchaseId 기반 생성 상태·결과 연결은 B의 public port 계약 후 별도 구현해야 한다.

관리자 전용 OpenAPI만 갱신한다. 공유 FE 계약·생성 타입·공개 구매 endpoint와 DB migration은 바꾸지 않는다. PR #33을 기반으로 한 별도 후속 브랜치이며 #33 이후 병합한다.

## 검증

분리 PostgreSQL 17 테스트 DB에서 고객센터 suite 최종 16건(신규 5 + 상속 lifecycle 11) 전부 통과했다. 관리자 OpenAPI 검사·export와 bootJar도 성공했다. 실제 CS 접수·B purchaseId 통합·외부 API·운영 배포는 확인하지 않았다.

## BE-B 후속 계약

purchaseId 기반 조회는 B가 `findSupportPurchase(UUID purchaseId)` 형태의 public read port로 구매 ID, 소유자 ID, quoteId, 안전한 상태·실패 코드·시각만 반환하도록 합의한 후 A 관리자 orchestration에서 연결한다. 운영자 권한/감사는 A에서 처리하고 B는 결과·생년정보·생성 context·외부 URL을 반환하지 않는다. 생성 재개/환급 명령과 합치지 않는다. port 이름/필드/구현은 아직 합의·구현되지 않았다.
