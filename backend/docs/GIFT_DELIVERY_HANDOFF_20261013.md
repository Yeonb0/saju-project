# 선물 발송·재발급·목록 연결 안내

기준: 2026-10-13, `codex/gift-fulfillment`. A 구현이며 FE·B 코드와 공유 OpenAPI는 변경하지 않았다. 아래 API는 local/test 전용이고 기본 비활성이다. 실제 알림톡 발송이나 수신 콘텐츠까지 완성된 상태는 아니다.

후속 사용자 확정: 알림톡 재발송·알림톡을 통한 링크 재발급은 실제 구매 차감 시각부터 30일 미만까지다. 30일 이후 새 발송을 차단하고, 별도 전화번호 정리 worker는 초안의 전화번호와 마스킹 번호·발송 outbox를 지운다. 기존 90일 링크 권한은 폐기하지 않으며 메시지·금융 기록을 같이 삭제하지 않는다. `GIFT_PHONE_CLEANUP_ENABLED=false`가 기본값으로 실제 환경의 활성화는 별도다. 결과 불명인 기존 발송은 원문 전화번호 없이 작업 ID로 조회하며 새 발송은 하지 않는다. 기본 정리 주기는 1분, 오류는 5회 후 운영 검토이며 순간적·백업 포함 완전 삭제 보장은 아니다.

## 이번 구현

- 발송 작업 저장, worker 점유, 결과 조회, 제한 재시도와 검토 상태.
- 구매자 전용 목록, 발송 상태 조회, 수동 재발송·링크 재발급.
- 보관기간을 명시적으로 설정한 경우의 개인정보 정리와 암호문 키 버전 전환.
- 구매·취소와 발송 작업의 경쟁 차단, 구 링크 폐기, 개인정보 없는 감사 이벤트.

알림톡 채널은 카카오톡으로 결정됐다. 실제 발송 대행사의 API, 발신 프로필, 승인 템플릿은 아직 입력되지 않았다. 카카오 OAuth 키는 알림톡 대행사 API 설정을 대신하지 않는다. [카카오 공식 안내](https://kakaobusiness.gitbook.io/main/ad/infotalk)를 참고한다.

## FE API

공통 prefix는 `/api/v1`. 로그인 세션이 필요하고 POST는 CSRF와 `Idempotency-Key`가 필요하다. 소유자가 아니거나 없는 주문은 같은 404다.

| API | 응답·용도 |
|---|---|
| GET /gift-orders?size=20&cursor=... | 본인 주문 목록, 최신순 cursor 페이지 |
| GET /gift-orders/{orderId}/delivery | 발송 상태와 수동 요청 가능 여부 |
| POST /gift-orders/{orderId}/deliveries | 본문 없이 새 발송 작업 예약, 202 |
| POST /gift-orders/{orderId}/link-reissues | 본문 없이 구 링크 폐기·새 발송 작업 예약, 202 |

목록: `items`, `nextCursor`(없으면 null), `hasNext`. size는 1~50, 기본 20이다. cursor는 서버 값을 그대로 보내며 잘못된 값은 `INVALID_CURSOR`다.

목록 항목: `orderId`, `quoteId`, `productCode`, `productName`, `plannedShells`, `status`, `chargedShells`, `deliveryStatus`, `recipientPhoneMasked`, `createdAt`, `accessExpiresAt`. 미차감·미발송·미활성 값은 null일 수 있다. 주문 초안 상세의 `expiresAt`은 견적 기한이며 목록의 `accessExpiresAt`과 다르다. 삭제 처리된 번호는 `REDACTED`다.

발송 응답: `status`, `canResend`, `remainingResends`, `nextResendAt`. 원문 링크·token·전화번호·메시지는 반환하지 않는다. 수동 두 기능은 모두 token을 교체하고 구 링크를 폐기한다. 기존 선물 만료일은 연장하지 않는다. 직접 복사할 새 링크를 반환하는 기능은 없다.

| 발송 상태 | 화면 판단 |
|---|---|
| PENDING | 예약됨, 아직 발송 완료 아님 |
| IN_FLIGHT | worker 처리 중 |
| RETRY | 접수되지 않았음을 확인하고 재시도 대기 |
| UNKNOWN | 접수 또는 결과 불명, 조회 중; 수동 재발송 금지 |
| SENT | 대행사가 해당 작업의 전달 완료를 확인함; 콘텐츠 제공 완료 아님 |
| FAILED | 확정 실패; 조건을 만족하면 수동 요청 가능 |
| REVIEW_REQUIRED | 결과 불명 조회 한도 또는 모순된 결과, 고객센터 확인 필요 |
| CANCELED | 취소·만료·폐기 등으로 후속 발송 차단 |

구매의 ACTIVE, 알림톡의 SENT, 콘텐츠 최초 제공은 별개다. 202만 보고 발송 완료 화면을 띄우지 않는다. 발송 조회는 기록된 상태를 유지하므로 과거 SENT인 선물이 나중에 취소·만료될 수 있다. 금융 상태와 링크 만료도 함께 확인한다.

사용자가 확정한 제한: **선물 주문별로 재발송+재발급 합산 하루 3회, 1분 간격**. 날짜 경계는 Asia/Seoul이며 자정이 지나도 직전 요청 후 1분 제한은 유지한다. 같은 키·주문·명령 재전송은 횟수를 다시 쓰지 않는다. 다른 주문/명령에 같은 발송 키를 쓰면 409다. 구매·취소 키와 발송 키는 별도 저장 영역이다.

수동 요청은 ACTIVE·미만료·직전 발송 SENT/FAILED에서만 허용한다. 처리 중·결과 불명에는 막는다. 발송 adapter 또는 설정이 없으면 503 `GIFT_DELIVERY_UNAVAILABLE`, 사용할 수 없는 주문은 409 `GIFT_DELIVERY_NOT_ALLOWED`, 처리 중/횟수/간격 제한은 409 `GIFT_DELIVERY_LIMIT_EXCEEDED`다. FE는 GET의 `canResend`도 사용한다.

## 트랜잭션·중복·복구

1. 차감 확인·선물 권한·token hash·암호화 outbox·첫 발송 job을 같은 트랜잭션에 저장한다. 수동 재발급도 구 token 폐기·새 job·명령키·감사 이벤트를 함께 저장한다. 감사 저장 실패 시 함께 롤백한다.
2. worker는 주문 work와 job을 짧게 잠그고 180초 lease를 저장한다. 실제 발송/조회 호출은 DB 트랜잭션 밖이다. adapter의 전체 호출 제한은 lease보다 짧아야 한다.
3. 작업 UUID를 대행사 상관·멱등키로 사용한다. 접수 시작을 DB에 먼저 남긴다. 호출 직후 중단되거나 응답이 끊기면 같은 작업 결과부터 조회한다. 새 token이나 새 결제를 만들지 않는다.
4. 단순 ACCEPTED는 SENT가 아니다. 확정 미접수에만 최초 발송+최대 3회(1/5/30분) 재시도를 허용한다. 결과 불명 조회는 최대 5회이며 확인되지 않으면 REVIEW_REQUIRED다. 접수됐던 작업이 나중에 없다고 응답하는 모순도 검토로 보낸다.
5. lease가 바뀌거나 취소·만료·재발급된 작업의 늦은 완료는 현재 상태를 변경하지 못한다. 이미 대행사로 넘어간 메시지는 취소 후 도착할 수 있으나 폐기된 링크는 거절한다. 발송된 메시지를 회수할 수 있다고 보장하지 않는다.
6. 발송 실패·링크 만료는 자동 환급 사유로 처리하지 않는다. 기존 선물 취소/원장 반환은 별도 금융 상태와 receipt로 확인한다.

## 개인정보·암호문

- SENT/FAILED/CANCELED 확정 시 발송 outbox의 원문 token이 담긴 암호문을 비운다. 수동 요청은 보존 중인 암호화 초안에서 새 token을 만든다. 결과 불명 검토 자료는 즉시 삭제하지 않는다.
- 개인정보 정리는 기본 꺼져 있다. 후속 v2 구현은 임의 보관기간 설정 대신 실제 목적 종료를 적용한다. 상세는 `GIFT_RECIPIENT_HANDOFF.md`를 따른다.
- 정리 대상은 만료 초안·취소/반환/실패 확정 주문·미접수 90일 만료·비회원 최초 제공 후 90일 종료·연결 수신자 탈퇴다. 기한 내 처리 중 receipt와 ACTIVE 수신자 계정 보관은 보호한다. 금융 처리/검토 중 주문과 살아 있는 발송 lease도 보존한다. 링크 폐기, 발송 차단, 암호문 삭제, 마스킹 번호 정리를 한 트랜잭션으로 처리한다.
- 금융 원장·차감/반환 receipt·금액·요청 비교 hash는 남긴다. 중복 재요청이 삭제한 개인정보를 복원하지 않는다. 만료 정리는 자동 환급이 아니다. 장차 B 수신·재열람 기한이 추가되면 삭제 조건도 먼저 맞춰야 한다.
- 기존 `v1`을 읽으면서 선택적으로 `v2:keyId:...` AES-GCM으로 쓴다. keyId도 인증 정보에 묶는다. 주문·발송 암호문을 함께 재암호화하며 부분 실패는 전체 롤백한다.
- 키 전환 오류는 5회 제한 재시도 후 REVIEW_REQUIRED로 분리해 다른 주문의 전환을 막지 않는다. 검토 상태의 관리자 재처리 API는 후속이다.
- **GIFT_PAYLOAD_KEY는 기존 v1 복호화 및 안정적인 HMAC 비교 root로 계속 필요하다.** v2 전환이 root 교체나 키 유출 대응 완료를 의미하지 않는다. 이를 임의 교체하면 기존 요청 비교가 깨진다.
- 새 버전을 읽는 코드를 전체 인스턴스에 배포한 뒤 v2 쓰기를 켠다. 구 keyring 키는 DB·복구 작업·진행 중 요청·백업에서 필요 없음을 확인하기 전 제거하지 않는다. 실제 키 변경/백업 복원은 수행하지 않았다.

## 설정·마이그레이션

실제 `.env`는 수정하지 않았다. 예제 설정만 추가했다. 모든 flag는 기본 false다.

| 설정 | 조건 |
|---|---|
| GIFT_ORDERS_ENABLED / GIFT_PURCHASES_ENABLED | 기존 주문/구매 조건, WALLET_PURCHASES_ENABLED와 별도 암호화 키 필요 |
| GIFT_DELIVERY_ENABLED | 실제 GiftAlimtalkProvider adapter가 있어야 수동 요청/발송 가능 |
| GIFT_DELIVERY_WORKER_ENABLED | delivery와 adapter가 없으면 기동 거절 |
| GIFT_RETENTION_ENABLED | 목적 종료 조건으로 정리, 임의 추가 보관기간 없음 |
| GIFT_FULFILLMENT_REVIEW_ENABLED | 접수 후 24시간 미제공을 운영 검토로 기록, 자동 환급 아님 |
| GIFT_PAYLOAD_ACTIVE_KEY_ID / GIFT_PAYLOAD_KEYRING | 선택 v2 keyId와 JSON keyring, 각 키 32바이트 Base64 |
| GIFT_PAYLOAD_REENCRYPTION_ENABLED | 활성 v2 key가 있어야 worker 기동 |

local/test 밖 활성화 제한을 유지한다. 게시 전 최종 발송/키 전환/전화번호 정리 migration은 `V202610132500`, `V202610132600`, `V202610132700`이다. B 미병합 PR과 번호 중복을 피하도록 미게시 A 선물 번호만 재배치했다. 기존 공유·배포 migration은 수정하지 않았다. 발송 migration은 기존 outbox의 UUID·암호문·상태를 유지해 job을 만든다. 옛 선물 검증 DB를 새 번호와 섞지 않는다. B 미병합 migration은 A보다 앞 번호이므로 운영 배포 전에 통합된 migration 집합·배포 순서를 확인해야 한다. 이번에는 B 브랜치를 병합·배포하지 않았다.

전용 계약: `backend/docs/openapi/gift-operations-v1.json`, backend에서 `gradlew.bat exportGiftOpenApi`. 공유 계약·FE 생성 타입은 담당자 동기화 전까지 그대로다.

## 남은 연결

- 대행사 선택/API 계약, 발신 프로필·템플릿 승인, 실제 adapter와 sandbox 전달/조회 검증.
- 알림톡 webhook은 미구현이다. 대행사 서명·이벤트 중복/상관 검증 및 B 소유 CSRF 필터의 예외 합의가 필요하다. 이번에는 B 필터를 변경하지 않고 상태 조회 복구만 구현했다.
- A 내부 접수/제공/계정 연결/settlement는 후속 구현됐다. B 수신자 입력·생성·제공 callback 및 부적 파일 권한 연결과 공개 수신 API는 아직 없다.
- 목적별 파기 조건의 실제 B 연결·운영 확인, 메시지 금칙어, SMS 대체/직접 링크 공유 여부. 준비 확인 전 임의로 활성화하지 않는다.
- 관리자 선물 검토/재처리 권한·운영 알림은 별도 검증 범위다. 다른 사람 주문의 수동 재발송 API를 열어두지 않았다.
- FE는 위 API와 기존 견적→초안→purchase→상태 조회를 연결해야 한다. 실제 수신 콘텐츠·브라우저 E2E 완료로 안내하지 않는다.

## 검증 기록

PR 최종 검증: 번호 재배치한 새 PostgreSQL 17.11 분리 DB에서 `clean check bootJar exportGiftOpenApi` 성공. 전체 636건 중 624 통과·12 선택 제외·실패/오류 0, migration 43개 모두 성공이다. 추가 30일 경계·전화번호 정리 7건과 관리자 단건 2건이 포함된다. 아래 627건/42개는 앞선 발송 단계 기록이다.

분리 PostgreSQL 17.11 테스트 DB, 합성 회원/전화번호/키, fake provider로 전체 627건 중 615 통과, 선택 제외 12, 실패/오류 0을 확인했다. `check bootJar exportGiftOpenApi` 성공, migration 42개 모두 성공이다. delivery PostgreSQL suite 43건은 기존 lifecycle 12건과 추가 31건이며 cipher 단위 4건도 통과했다. provider 호출 시 DB 트랜잭션이 없는지, 동일 작업 경쟁, 응답 유실 조회, 늦은 응답 차단, 재발급/취소 rollback, 보관 정리, 키 전환/오류 격리를 검사했다. 상세는 `docs/OPERATIONS_CHECKLIST.md`의 최신 기록을 따른다.

실제 알림톡·PG·B 수신 콘텐츠·FE 브라우저·운영 배포·OS 강제 종료·부하·백업 복원은 수행하지 않았다. 저장 오류/lease 만료 주입을 실제 OS 종료 시험으로 표현하지 않는다.
