# 선물 관리자 조회·유지보수 재개

## 최신 main 직접 링크 호환 보완

PR #35·#36·#38을 포함한 main `84f87f0`과 통합했다. 이는 Q-54의 제품 정책 확정을 대신하지 않는다. 기존 알림톡 주문과 현재 main의 직접 링크 주문을 함께 운영 조회할 수 있도록 보완했다.

- `deliveryMode`는 기존 전화번호/발송 outbox가 있는 주문이면 `ALIMTALK_LEGACY`, 그 밖은 `DIRECT_LINK`다. 신규 주문에는 발송 작업이 없고 `deliveryState=null`, `deliveries=[]`가 정상이다.
- `tokenVersion`, `linkIssuedAt`, `linkRevealUntil`, `linkRetrievable`만 추가했다. `linkRetrievable`은 현재 구매자가 링크 원문을 조회할 수 있는지를 뜻하며 발송/수신/콘텐츠 생성 성공을 뜻하지 않는다. 관리자에게 원문 URL·token·암호문·hash를 제공하지 않는다.
- 전화번호가 없는 신규 주문은 전화번호 정리 후보·실패 검토·PHONE_CLEANUP 재개에서 제외한다. 과거 알림톡 주문의 기존 30일 정리와 제한 재개는 유지한다.
- 새 `token_ciphertext`도 기존 draft/outbox와 같은 트랜잭션에서 키 전환한다. token hash/version/만료/원장을 바꾸지 않는다. 암호문 인증 실패면 전체 전환을 rollback하고 기존 5회 실패/제한 관리자 재개를 적용한다.
- 취소 의도 저장·목적 종료 개인정보 파기 시 token 암호문도 NULL 처리한다. 24시간 원문 공개기간 종료는 암호문만 정리하고 유효한 bearer hash 권한을 폐기하지 않는다. 재발급은 취소/최초 제공과 같은 work→token 잠금 순서를 사용한다.
- 재발급 기간·횟수·간격·수신 접수 이후 재발급 정책은 새로 정하지 않았다. Q-54 합의가 별도로 필요하다. 실제 외부 adapter와 공개 수신/생성 E2E는 이번 범위가 아니다.

### 이미 PR #35 migration을 적용한 DB

공유된 `V202610140100`을 수정하거나 번호를 바꾸지 않았다. `V202610141000`만 먼저 적용한 DB에서는 기본 Flyway 검증이 실패한다. 배포 전 적용 이력을 확인하고 별도의 migration 단계에서 `outOfOrder=true`로 누락된 관리자 migration을 한 번 적용한 뒤 기본 설정으로 복귀해야 한다. 애플리케이션 기본 out-of-order 설정은 켜지 않았다.

`AdminGiftMigrationUpgradeTest`는 #33 migration이 없는 main schema를 먼저 만든 뒤 기본 검증 거절, 명시적 out-of-order 적용 1건, 이후 기본 검증 성공과 직접 링크 column 보존을 확인한다. 운영 DB에 이 절차를 실행한 것은 아니다.

## 범위

선물 주문·발송·접수 지연·개인정보 정리·암호문 전환 검토 상태를 개인정보 없이 조회한다. 실패 5회로 자동 대상에서 제외된 유지보수만 제한적으로 다시 연다. 관리자 화면은 만들지 않았으며 권한 있는 세션으로 Postman 등에서 사용할 수 있다.

FE·B 인증/회원/인물/운세 소스·공유 OpenAPI·실제 .env·개발 DB는 변경하지 않았다. 관리자 계약은 `backend/docs/openapi/admin-operations-v1.json`이며 `exportAdminOpenApi`로 생성한다.

## API

| 메서드·경로 | 기능 |
|---|---|
| `GET /api/v1/admin/gift-orders` | UUID 오름차순 cursor 목록. buyerId·after·limit·reviewOnly |
| `GET /api/v1/admin/gift-orders/{orderId}` | 주문/접수/현재 발송/정리 상태, 최근 발송 20건·이벤트 50건, 작업별 관리자 재개 횟수 |
| `POST /api/v1/admin/gift-orders/{orderId}/maintenance-reopens` | 실패 한도를 소진한 유지보수 재개. CSRF·Idempotency-Key·kind·reasonCode 필요 |

목록 기본 20건, 최대 50건이다. nextCursor가 있을 때 after로 사용한다. reviewOnly는 구매/현재 발송/접수의 REVIEW_REQUIRED, worker 미표시 상태라도 접수 후 24시간이 지난 ACCEPTED, 아직 파기되지 않은 개인정보의 실패 5회, 암호문 전환 REVIEW_REQUIRED를 포함한다. 과거 발송의 전체 이력을 반환하는 API는 아니다.

응답에는 주문·구매자·견적·차감·작업 식별자와 상태/시각만 포함한다. 전화번호(마스킹 번호 포함), 이름·메시지·생년정보, token·URL·암호문·요청 hash·lease token·키 값은 반환하지 않는다. 완료 콘텐츠의 실제 내용이나 B 생성 작업 상태를 조회하는 API도 아니다.

## 권한과 감사

- 조회: `ADMIN_OPERATIONS_ENABLED=true`, DB의 활성 운영자 grant와 ACTIVE 회원 상태가 필요하다. 클라이언트 ADMIN 역할 문자열로 권한을 얻을 수 없다.
- 변경: `ADMIN_RECOVERY_ENABLED=true`, `can_recover=true`, 기존 세션·CSRF 조건이 추가로 필요하다.
- 운영자 생성/권한 부여 API는 추가하지 않았다. 기존 운영 절차를 따른다.
- 조회도 같은 DB 트랜잭션에 감사한다. 감사 저장에 실패하면 응답하지 않는다. 재개는 권한 행·회원 행을 잠그고 감사/재개 의도를 함께 저장한다.
- 목록 감사의 targetId는 구매자 필터가 있으면 구매자, 없으면 조회 운영자다. 상세/재개 targetId는 선물 주문번호다. action으로 조회 종류를 구분한다.
- 재개 사유는 `SYSTEM_RECOVERY` 또는 `CUSTOMER_INQUIRY`다. 사유에 개인정보나 자유 입력 원문을 넣지 않는다.

## 재개 요청

```json
{
  "kind": "PHONE_CLEANUP",
  "reasonCode": "SYSTEM_RECOVERY"
}
```

| kind | 대상·추가 조건 | 재개 결과 |
|---|---|---|
| PHONE_CLEANUP | 전화번호 미파기·실패 5회, GIFT_PHONE_CLEANUP_ENABLED=true | 실패/다음 시도 시각 초기화 |
| PRIVATE_CLEANUP | 전체 개인정보 미파기·실패 5회, GIFT_RETENTION_ENABLED=true | 실패/다음 시도 시각 초기화 |
| PAYLOAD_REENCRYPTION | 현재 활성 keyId의 REVIEW_REQUIRED·실패 5회, GIFT_PAYLOAD_REENCRYPTION_ENABLED=true 및 유효 v2 활성 키 | 소진된 전환 work 제거, 기존 worker가 다시 후보 판정 |

`GIFT_PURCHASES_ENABLED`와 필요한 기존 주문/지갑/암호화 설정도 준비되어야 한다. 실제 키는 서버에서 안전하게 설정하며 채팅·문서로 공유하지 않는다. 모든 관련 flag의 기존 기본 false는 유지했다.

HTTP 200과 `state=REOPENED`는 **재시도 기회를 다시 열었다는 뜻**이다. 전화번호 삭제·전체 삭제·키 전환의 완료가 아니다. 기존 worker가 별도로 실행하고 주문 상세의 파기 시각·실패 횟수·cipherState를 확인한다. REOPENED는 과거 요청의 결과이며 replay 시 현재 worker 상태를 대신하지 않는다.

동일 운영자·요청 키·주문·kind·reasonCode는 같은 requestId/요청 시각을 반환한다. 동일 키의 대상/종류/사유 변경은 409이며 replay는 재시도 횟수를 다시 초기화하지 않는다. 정상 작업·이미 파기된 대상·비활성 worker·현재 활성 키와 다른 전환 work는 재개할 수 없다.

관리자 재개는 **주문별·kind별 누적 3회**, 운영자 전체 합산이다. 고객의 일 3회 재발송/재발급과 다른 한도다. 새 키나 다른 운영자로 우회할 수 없고 기존 worker의 매 주기 최대 5회 한도도 유지한다. 3회 소진 뒤 원인 수정과 추가 복구 정책 검토가 필요하며 자동 무한 재개는 없다.

## 처리 경계

- 선물 work → draft → 운영자 권한/회원 순서로 잠근다. 동일 주문의 동시 재개와 worker 정리는 직렬화한다.
- 재개 의도 INSERT·요청 대응·한도 검사·감사·재시도 초기화는 같은 짧은 DB 트랜잭션이다. 대상 검사/감사가 실패하면 모두 롤백한다.
- 재개 자체는 외부 호출·발송·생성·복호화·파일 업로드를 하지 않는다. 개인정보·지갑·원장·gift token·최초 제공 기록을 재작성하지 않는다.
- 기존 worker가 30일/90일/처리 중 receipt/회원 보관/발송 lease 등 목적·안전 조건을 다시 검사한다. 재개 API가 파기 조건을 우회하지 않는다.
- 원인 수정 전 단순 반복 재개를 운영 해결로 간주하지 않는다. 키 전환 완료 후에도 root/이전 keyring 키와 백업 의존성 검토가 필요하다.

## 검증과 남은 것

PostgreSQL 17 분리 DB에서 최종 `check bootJar exportAdminOpenApi` 성공. 전체 689건 중 677 통과·12 선택 제외·실패/오류 0, migration 47개 모두 success=true. 신규 관리자 suite 25건은 신규 13건과 기존 lifecycle 상속 12건이다.

권한/CSRF·응답 최소화·cursor/검토 필터·중복/다른 입력 충돌·동시 재개·전 운영자 합산 한도·감사 실패 rollback·권한 철회·비활성 worker 거절·목적 종료 보호·재개 후 전화번호 파기/replay 무복원·전환 재개 무암호문 변경·잔액/반환 불변을 확인했다.

실제 알림톡/PG·FE/B E2E·운영 활성화·OS 강제 종료·부하·백업 복원은 수행하지 않았다. B 데이터/파일 파기를 수정한 것이 아니다.

아직 제공하지 않는 관리 작업: 발송 결과 불명 건의 관리자 재조회/재발송, 생성 지연 건의 B 작업 재개/실패 확정, 구매·취소 검토 건의 금융 복구, 토큰 강제 재발급, 임의 원장 조정. 이번 조회 API로 상태를 확인할 수 있으나 강제로 성공 처리할 수 없다. 운세 purchaseId/quoteId 고객센터 검색과 실제 운영 알림 연결도 별도 남은 범위다.
