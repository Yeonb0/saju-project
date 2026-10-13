# 선물 구매·취소 구현과 다음 연결 범위

후속 발송·목록·개인정보 정리·키 버전 구현 계약은 [GIFT_DELIVERY_HANDOFF_20261013.md](GIFT_DELIVERY_HANDOFF_20261013.md)를 함께 따른다. 실제 알림톡 adapter와 B 수신 콘텐츠는 여전히 미연결이다.

기준: main `bb3169c`에서 이어가는 `codex/gift-fulfillment`. FE 코드와 공유 OpenAPI는 변경하지 않는다. 이전 선물 브랜치의 견적·암호화 주문·차감 복구를 보존해 통합했다. 실제 판매/발송/배포 기능을 켠 작업이 아니다.

## 구현 범위

- 1차 수신자 1명, 수능 선물만 지원한다. `TALISMAN_ONLY` 10개, `READING_WITH_TALISMAN` 15개이며 원화 직접 결제하지 않는다. 두 상품은 기본 비활성이다.
- 견적은 30분이며 등껍질을 차감/예약하지 않는다. 기존 자금 안내 서비스의 잔액·부족량·충전 추천을 사용한다.
- 주문 초안은 quoteId, recipientName, recipientPhone, senderName, message를 받는다. 생년정보나 talismanType은 받지 않는다. 새 초안의 charged는 null이다.
- 같은 사용자/주문 의도의 키·본문은 기존 주문을 재사용하고 다른 본문은 409다. 전화번호·메시지는 AES-GCM 암호화, 요청 비교는 HMAC이며 응답에는 마스킹 번호만 있다. 이름 40 code point, 메시지 200 code point, 010 숫자 11자리 및 제어문자 검증을 한다. 금칙어 정책은 아직 미구현이다.
- 명시적인 구매 명령에서만 차감한다. 차감 원장에 기록된 수량·시각을 확인하고, 선물 권한·token hash·암호화된 발송 의도를 함께 저장한다. ACTIVE는 이 금융/권한 처리가 끝났다는 뜻이지 알림톡 성공이나 콘텐츠 생성 완료가 아니다.
- 실제 차감 시각 기준 7일 이내, 최초 콘텐츠 제공 전인 ACTIVE 선물은 취소 가능하다. 현금이 아닌 원 거래의 유료/보너스 등껍질을 원 지급분·원 유효기간으로 반환한다. 이미 만료된 지급분의 기한을 연장하지 않는다.
- 취소 예약 시 링크 폐기·발송 차단·취소 의도를 함께 저장한다. 지갑 반환의 durable receipt가 확인되면 CANCELED다. 반환 후 주문 저장이 실패해도 같은 원 거래로 재개하며 이중 반환하지 않는다.
- 구매/취소 명령키는 영속 저장하며 같은 키를 다른 주문 또는 다른 명령으로 재사용하면 409다. 동일 주문의 새 키도 중복 차감/반환을 만들지 않는다.
- 32바이트 난수 token 검증 port는 ACTIVE·비폐기·90일 기한을 확인한다. 공개 수신 HTTP API는 아직 없다. 임의 부적 ID 접근은 연결 전까지 404로 차단한다.

## 실제 API

공통 prefix `/api/v1`. 로그인 세션이 필요하며 POST는 CSRF를 요구한다. 신규 주문·구매·취소는 `Idempotency-Key`를 사용한다. 타인/없는 리소스는 같은 404다.

| API | 용도 |
|---|---|
| POST /gift-quotes | 견적 발급, fortuneType=SUNEUNG 및 지원 option |
| GET /gift-quotes/{quoteId} | 본인 견적과 현재 자금 안내 |
| POST /gift-orders | 주문 초안 생성/동일 요청 재전송 |
| GET /gift-orders/{orderId} | 메타데이터와 현재 금융 상태·실제 charged |
| POST /gift-orders/{orderId}/purchase | 초안의 구매 의도 등록·차감 재개, 본문 없음 |
| GET /gift-orders/{orderId}/purchase | 구매/취소 금융 상태 조회 |
| POST /gift-orders/{orderId}/cancel | 미제공·7일 이내 선물의 등껍질 반환, 본문 없음 |

구매·취소 HTTP POST는 PENDING/CANCEL_PENDING이면 202, 그 외에는 200이다. 200만으로 발송/콘텐츠/반환 성공을 판단하지 않는다.

구매 상태 응답: orderId, status, giftId, chargedShells, returnedShells, approvedAt, nextAction. 미확정 값은 null이며 null을 0으로 바꾸지 않는다. chargedShells는 원래 차감량이며 반환 후에도 유지한다. 현재 잔액은 GET /wallet로 다시 읽는다.

초안 응답의 expiresAt은 원 견적 기한이지 선물 링크 만료일이 아니다. 구매 복구는 이 값이 지났어도 이미 저장된 차감 receipt로 재개할 수 있다.

| 상태 | 뜻 |
|---|---|
| CREATED | 주문 메타데이터만 저장, 미차감 |
| PENDING | 구매 의도 등록·차감/권한 저장 재개 중 |
| ACTIVE | 차감 확인 및 token/outbox 저장 완료, 실제 발송/콘텐츠 완료 아님 |
| CANCEL_PENDING | 취소 예약·링크 폐기 완료, 등껍질 반환 확정 전 |
| CANCELED | 이 취소 의도의 원 거래 반환 receipt·완료 상태 저장 확인 |
| REFUNDED | 활성화 전 기존 차감에 대한 반환 receipt 확인, 발송 없음 |
| FAILED | 차감 전 확정된 판매 종료·견적 만료·잔액 부족 등 |
| REVIEW_REQUIRED | 원장 불일치 또는 제한 재시도 소진, 자동으로 완료/실패 단정 금지 |

처리 중 nextAction=POLL, 검토는 CONTACT_SUPPORT, 기타 NONE이다. ACTIVE에 VIEW_READING을 반환하지 않는다. 기존 구매/취소를 새 견적으로 대체하지 않는다.

## 트랜잭션·복구

1. 구매 의도/명령키를 먼저 커밋한다. 이후 work 행을 잠그고 독립 지갑 트랜잭션으로 차감한다. 외부 PG·알림톡·Liner 호출은 이 흐름에 없다.
2. 차감이 살아남고 권한 저장이 롤백되면 다음 재개에서 기존 차감 receipt를 읽는다. 금액 검증·token·발송 의도는 한 트랜잭션이다. 초안만 있는 주문은 worker가 구매하지 않는다.
3. 취소는 work 잠금 아래 7일/미제공 조건을 확인하고 token/outbox를 차단한다. 이 예약 커밋이 끝난 뒤 지갑을 반환하고 별도 완료 트랜잭션에서 receipt를 확인한다. 늦은 구매 재개는 ACTIVE로 되돌리지 않는다.
4. DB 원 거래/명령키 중복 방지로 여러 worker가 경쟁해도 차감·반환은 한 번이다. 금융 흐름에는 외부 발송이 없다. 후속 발송 worker에는 별도 180초 lease와 늦은 완료 차단, 결과 불명 조회를 추가했다. 실제 provider adapter는 미연결이다.
5. 구매/반환 오류는 각각 최대 5회, 30초부터 최대 600초 지연으로 재개한다. 한도 뒤 검토 상태로 남기고 링크를 다시 살리지 않는다. 권한 있는 검토 재처리 절차는 후속이다.
6. GIFT_RECOVERY_ENABLED는 기본 false다. 켜면 같은 worker가 명시적인 구매 의도와 취소 의도만 재개한다. 기능 설정 없이 worker만 켜는 조합은 지원하지 않는다.

## 환경·마이그레이션

- GIFT_ORDERS_ENABLED, GIFT_PURCHASES_ENABLED, GIFT_RECOVERY_ENABLED 모두 기본 false.
- 구매를 켜려면 orders와 WALLET_PURCHASES_ENABLED도 필요하다. 개인정보 암호화용 GIFT_PAYLOAD_KEY는 토스 키와 별개인 32바이트 Base64 키다. 실제 키는 채팅·Git에 넣지 않는다.
- 현재 cipher는 local/test만 허용하고 staging/production/prod에서 활성화를 거절한다. 개인정보 정리와 키 버전 전환 worker는 추가했지만 기본 꺼져 있으며 실제 보관기간·대행사·수신 연결이 준비되기 전 운영 활성화를 허용하지 않는다.
- 게시 전 최종 Flyway 번호는 202610132100/2200/2300/2400(견적/초안/차감/취소)과 2500/2600/2700(발송/키 전환/전화번호 정리)이다. B 미병합 PR의 131300/131400과 중복된 번호를 피하기 위해 미게시 A 선물 migration만 재배치했다. 옛 선물 검증 DB의 131300~132000 번호와 섞지 말고 별도의 빈 검증 DB를 사용한다. 이미 공유·배포된 migration과 사용자 DB는 수정하지 않았다.
- backend 전용 계약은 `docs/openapi/gift-operations-v1.json`, 생성 명령은 backend에서 `gradlew.bat exportGiftOpenApi`다. 공유 `docs/openapi/api-v1.json`·FE 생성 타입은 변경하지 않았다. FE 연결 시 담당자가 함께 동기화한다.

## 다음 구현과 팀 결정

- B 수신자 정보·콘텐츠 생성 연결: 최초 제공 성공 시점과 취소를 같은 선물 잠금/상태 전이로 조정해야 한다. token.verify만 호출하고 별도로 콘텐츠를 내주면 취소와 경쟁할 수 있으므로 아직 공개하지 않는다.
- 90일 내 수신 정보 제출, 24시간 최초 생성 유예, 최초 제공 뒤 90일 재열람, 회원 계정 연결은 정책만 있고 아직 실행 경로가 없다.
- 부적/reading 연결, 권한 있는 파일 조회, 이미지 저장/보관함은 B와 연결한다. verifyTalismanAccess는 연결 전까지 차단한다.
- 알림톡 발송 port·worker·결과 불명 조회·자동 재시도·수동 재발송/재발급·구 token 폐기는 후속 구현됐다. 수동 두 기능은 주문별 합산 일 3회·1분 제한이다. 실제 provider·템플릿·발신 프로필과 서명 webhook은 남았다. adapter가 없으므로 실제 발송은 하지 않는다.
- 개인정보 삭제와 암호화 키 버전 전환은 기본 비활성으로 구현했다. 실제 보관기간, 키 전환 운영 절차, 금칙어, 발송 실패 링크 직접 공유 여부는 남았다. 실제 알림톡/스테이징은 이 항목을 확정한 뒤 활성화한다.
- 아직 취소/반환 가능성이 있는 선물 차감은 충전 현금 환불에서 미정산 구매로 보수적으로 차단된다. 선물 최초 제공·반환의 settlement port를 연결하기 전 이를 완료된 운세 구매처럼 취급하지 않는다.
- 구매자 전용 선물 목록과 발송·삭제·키 전환 감사 이벤트는 후속 구현됐다. 공개 수신 HTTP·관리자 검토 재처리·운영 알림은 남았다. 수능 1명 외 일반 운세/여러 명 선물은 이번 범위 밖이다.
- FE는 견적→초안→purchase→동일 주문 상태 조회로 연결한다. 기존 API_SPEC의 선물 원화 confirm·refunds 초안을 사용하지 않는다. 아직 완성된 수신 링크/콘텐츠를 사용자에게 약속하지 않는다.

## 검증

아래 수치는 구매·취소 단계의 기록이다. 발송 등 후속 전체 검증의 최신 수치는 OPERATIONS_CHECKLIST 최신 기록을 따른다.

합성 사용자/전화번호/암호화 키와 별도 PostgreSQL 17.11 DB로 검증했다. 최종 migration 소스로 전체 580건 중 568 통과, 선택 제외 12, 실패/오류 0과 check/bootJar/export를 확인했다. 최종 200/202 JSON 계약 보완 뒤 선물/견적 51건 모두 통과 및 bootJar/export를 다시 확인했다. migration 40개 모두 성공, lifecycle PostgreSQL 12건에 기존 grant/token/outbox 보존 업그레이드가 포함된다. 기본 H2 계약 기동도 확인했지만 금융 근거는 PostgreSQL이다. 상세는 OPERATIONS_CHECKLIST의 이번 기록이다.

검사 범위: 동일 키/다른 주문·다른 명령, 이중 차감/반환, 동시 취소, 원 지급분/만료일 유지, 최초 제공/7일 초과 거절, 저장 실패 rollback, 반환 후 완료 저장 실패 재개, 5회 소진, token 변조/만료/폐기·임의 자산 차단, 실제 세션 쿠키/CSRF/소유권, 기존 grant와 암호화 outbox를 보존하는 migration 업그레이드.

실제 알림톡·B 수신 콘텐츠·FE 브라우저·PG·OS 프로세스 강제 종료·부하·백업 복원은 이번 검증에 포함하지 않는다. DB 예외 주입을 OS 종료 시험으로 표시하지 않는다.
