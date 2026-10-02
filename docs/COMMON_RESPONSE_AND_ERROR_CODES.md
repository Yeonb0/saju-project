# 뿌기사주 공통 응답 포맷·예외처리 코드

> 버전: Draft v0.1
> 작성일: 2026-10-02
> 적용 범위: `/api/v1` JSON API
> 참조: [`API_SPEC.md`](./API_SPEC.md), [`ERD.md`](./ERD.md)

## 1. 기본 원칙

- HTTP status는 전송 결과를, `code`는 프론트가 분기할 업무 원인을 나타낸다.
- 프론트는 오류 `message` 문구를 비교하지 않고 안정적인 `code`로 처리한다.
- 성공과 실패 모두 `traceId`를 제공한다. 사용자는 CS 문의에 이 값을 전달할 수 있다.
- 서버 내부 예외명, SQL, stack trace, PG·Liner·알림톡 원문 응답, 개인정보는 응답하지 않는다.
- 같은 원인의 오류 코드는 엔드포인트마다 새로 만들지 않는다.
- 복구 가능한 오류에는 프론트가 안전하게 사용할 최소한의 `details`만 제공한다.
- 본 문서가 공통 포맷과 오류 코드의 기준 원본이다. API별 문서는 데이터 payload와 추가 오류만 정의한다.

## 2. 성공 응답

### 2.1 단일 데이터

```json
{
  "data": {
    "id": "0199d6df-67aa-7be4-84b4-728413dfa09f",
    "status": "READY"
  },
  "traceId": "01K6WJ8W3Y6E8KDQH5PZTJ4G9B"
}
```

```ts
type ApiResponse<T> = {
  data: T;
  traceId: string;
};
```

### 2.2 목록·cursor pagination

```json
{
  "data": {
    "items": [],
    "nextCursor": null
  },
  "traceId": "01K6WJ8W3Y6E8KDQH5PZTJ4G9B"
}
```

```ts
type CursorPage<T> = {
  items: T[];
  nextCursor: string | null;
};
```

- `limit` 기본값은 20, 최댓값은 100이다.
- `cursor`는 의미를 노출하지 않는 opaque 문자열이다.
- 마지막 페이지는 `nextCursor: null`이다.
- 전체 개수가 실제 UX에 꼭 필요한 API가 아니면 `totalCount`를 계산하지 않는다.

### 2.3 생성·비동기 접수

- 동기 생성 완료: `201 Created`와 완성된 리소스를 반환한다.
- 비동기 작업 접수: `202 Accepted`와 작업 상태·조회 가능한 리소스 ID를 반환한다.

```json
{
  "data": {
    "id": "0199d6df-67aa-7be4-84b4-728413dfa09f",
    "status": "PENDING"
  },
  "traceId": "01K6WJ8W3Y6E8KDQH5PZTJ4G9B"
}
```

### 2.4 본문 없는 성공

`204 No Content`는 body를 반환하지 않는다. 탈퇴·삭제·로그아웃처럼 클라이언트가 후속 payload를 필요로 하지 않는 경우에만 사용한다.

### 2.5 HTTP status 기준

| HTTP | 사용 기준 |
|---:|---|
| 200 | 조회·명령 성공, 기존 멱등 응답 재사용 |
| 201 | 리소스 생성 완료 |
| 202 | 외부 작업 또는 생성 작업 접수 |
| 204 | 반환할 body가 없는 성공 |

## 3. 오류 응답

```json
{
  "code": "INSUFFICIENT_BALANCE",
  "message": "등껍질이 부족합니다.",
  "traceId": "01K6WJ8W3Y6E8KDQH5PZTJ4G9B",
  "fieldErrors": [],
  "details": {
    "required": 55,
    "balance": 40,
    "shortage": 15,
    "currency": "TURTLE_SHELL"
  }
}
```

```ts
type ApiError = {
  code: ErrorCode;
  message: string;
  traceId: string;
  fieldErrors: FieldError[];
  details?: Record<string, string | number | boolean | null>;
};

type FieldError = {
  field: string;
  reason: string;
};
```

### 필드 규칙

| 필드 | 필수 | 규칙 |
|---|---|---|
| `code` | 예 | `UPPER_SNAKE_CASE`, v1에서 의미 변경 금지 |
| `message` | 예 | 사용자에게 표시 가능한 한국어 기본 문구. 계약 조건으로 사용 금지 |
| `traceId` | 예 | 서버 요청 추적 ID. 비밀값이나 리소스 ID가 아님 |
| `fieldErrors` | 예 | 검증 오류가 아니면 빈 배열 |
| `details` | 아니요 | 화면 복구에 필요한 안전한 값만 허용 |

- `fieldErrors.field`는 JSON field path를 사용한다. 예: `birthDate`, `counterpart.birthTime`.
- `reason`은 `REQUIRED`, `INVALID_FORMAT`, `OUT_OF_RANGE`, `NOT_ALLOWED`, `TOO_LONG`, `MISMATCH` 같은 안정 enum이다.
- 입력 원문, 생년정보, 휴대전화번호, 메시지, 토큰은 `fieldErrors`나 `details`에 되돌려 보내지 않는다.
- 알 수 없는 오류에서도 `traceId`는 반환하고 `message`는 일반화한다.

### validation 예시

```json
{
  "code": "VALIDATION_FAILED",
  "message": "입력값을 확인해 주세요.",
  "traceId": "01K6WJ8W3Y6E8KDQH5PZTJ4G9B",
  "fieldErrors": [
    { "field": "birthDate", "reason": "OUT_OF_RANGE" },
    { "field": "birthTime", "reason": "MISMATCH" }
  ]
}
```

## 4. 오류 코드 목록

`재시도`는 동일 요청을 그대로 반복해도 되는지를 뜻한다. 멱등성 명령은 재시도할 때 반드시 같은 `Idempotency-Key`를 사용한다.

### 4.1 공통 요청

| HTTP | code | 의미 | 재시도 |
|---:|---|---|---|
| 400 | `INVALID_REQUEST` | 요청 구조 또는 파라미터가 올바르지 않음 | 수정 후 |
| 400 | `MALFORMED_JSON` | JSON 구문을 읽을 수 없음 | 수정 후 |
| 400 | `VALIDATION_FAILED` | 하나 이상의 필드 검증 실패 | 수정 후 |
| 400 | `INVALID_CURSOR` | cursor가 유효하지 않음 | 처음부터 |
| 404 | `RESOURCE_NOT_FOUND` | 리소스 없음 또는 존재를 숨겨야 함 | 아니요 |
| 405 | `METHOD_NOT_ALLOWED` | 지원하지 않는 HTTP method | 아니요 |
| 406 | `NOT_ACCEPTABLE` | 지원하지 않는 응답 media type | 수정 후 |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | 요청 Content-Type 미지원 | 수정 후 |
| 409 | `INVALID_STATE` | 현재 상태에서 수행할 수 없는 명령 | 상태 확인 |
| 429 | `RATE_LIMITED` | 호출 한도 초과 | `Retry-After` 후 |
| 500 | `INTERNAL_SERVER_ERROR` | 분류되지 않은 서버 오류 | 제한적으로 |
| 503 | `SERVICE_UNAVAILABLE` | 점검·일시 장애 | 이후 |

### 4.2 인증·권한·세션

| HTTP | code | 의미 | 프론트 처리 |
|---:|---|---|---|
| 401 | `AUTHENTICATION_REQUIRED` | 로그인 필요 | 로그인 후 원 경로 복귀 |
| 401 | `SESSION_EXPIRED` | 세션 만료·폐기 | 쿠키 정리 후 로그인 |
| 401 | `KAKAO_OAUTH_FAILED` | 카카오 OAuth 실패 | 재로그인 안내 |
| 403 | `FORBIDDEN` | 권한 없음 | 접근 불가 안내 |
| 403 | `CSRF_FAILED` | CSRF token 또는 Origin 검증 실패 | 페이지 새로고침 후 재시도 |
| 409 | `SESSION_LIMIT_EXCEEDED` | 동시 로그인 한도 초과 | `TBD(I-02)` 확정 후 UI 적용 |

### 4.3 멱등성·동시성

| HTTP | code | 의미 | 처리 |
|---:|---|---|---|
| 400 | `IDEMPOTENCY_KEY_REQUIRED` | 필수 명령에 키 누락 | 새 키로 최초 요청 |
| 409 | `IDEMPOTENCY_KEY_REUSED` | 같은 키에 다른 본문 사용 | 새 키 필요 |
| 409 | `IDEMPOTENCY_REQUEST_PROCESSING` | 같은 요청이 아직 처리 중 | 같은 키로 polling/재시도 |
| 409 | `CONCURRENT_MODIFICATION` | 낙관적 잠금 충돌 | 최신 상태 조회 후 재시도 |

### 4.4 인물·입력

| HTTP | code | 의미 |
|---:|---|---|
| 404 | `PERSON_NOT_FOUND` | 인물 없음 또는 소유권 없음 |
| 409 | `PERSON_LIMIT_EXCEEDED` | 저장 가능한 타인 10명 초과 |
| 409 | `PRIMARY_PERSON_ALREADY_EXISTS` | 본인 프로필 중복 생성 |
| 422 | `UNSUPPORTED_BIRTH_DATA` | 엔진 지원 범위 밖의 생년·달력·시간 |
| 422 | `THIRD_PARTY_AUTHORIZATION_REQUIRED` | 타인 정보 입력 권한 확인 누락 |
| 422 | `COMPATIBILITY_PERSON_REQUIRED` | 궁합 상대 정보 누락 |

### 4.5 상품·견적

| HTTP | code | 의미 |
|---:|---|---|
| 404 | `PRODUCT_NOT_FOUND` | 상품 코드 없음 |
| 409 | `QUOTE_EXPIRED` | 견적 만료 |
| 409 | `PRICE_CHANGED` | 견적 이후 가격·구성 변경 |
| 422 | `PRODUCT_NOT_AVAILABLE` | 판매 전·종료·비활성 상품 |
| 422 | `INVALID_PRODUCT_OPTION` | 상품에 허용되지 않는 옵션 |
| 422 | `EVENT_DATE_NOT_ALLOWED` | 수능 판매·이벤트 날짜 규칙 불일치 |

### 4.6 등껍질 지갑

| HTTP | code | 의미 | `details` 허용값 |
|---:|---|---|---|
| 409 | `INSUFFICIENT_BALANCE` | 구매에 필요한 등껍질 부족 | `required`, `balance`, `shortage`, `currency` |
| 409 | `WALLET_TRANSACTION_CONFLICT` | 이미 처리됐거나 상충하는 원장 명령 | `transactionId` |
| 409 | `WALLET_BALANCE_MISMATCH` | 집계 잔액과 원장 불일치 | 없음, 운영 알림 필수 |
| 422 | `PAID_BALANCE_REMAINS` | 탈퇴 전 유료 잔액 처리 필요 | `paidBalance` |

`WALLET_BALANCE_MISMATCH`는 사용자 재시도 대상이 아니며 결제 최우선 장애로 알린다.

### 4.7 PG 결제·충전·환불

| HTTP | code | 의미 | 재시도 |
|---:|---|---|---|
| 400 | `PAYMENT_AMOUNT_MISMATCH` | 클라이언트/PG/서버 주문 금액 불일치 | 아니요 |
| 404 | `PAYMENT_ORDER_NOT_FOUND` | 주문 없음 또는 소유권 없음 | 아니요 |
| 409 | `PAYMENT_ALREADY_PROCESSED` | 승인·취소가 이미 처리됨 | 최초 결과 조회 |
| 409 | `PAYMENT_NOT_COMPLETED` | 후속 명령에 필요한 결제 미완료 | 상태 확인 |
| 409 | `REFUND_NOT_ALLOWED` | 정책상 환불 불가 | 아니요 |
| 422 | `PAYMENT_REJECTED` | 카드·결제수단이 PG에서 거절됨 | 수단 변경 |
| 502 | `PAYMENT_PROVIDER_ERROR` | 토스 통신·응답 오류 | 같은 키로 제한 재시도 |
| 502 | `PAYMENT_CONFIRM_FAILED` | 승인 확정 실패 | 상태 조회 후 재시도 |
| 502 | `REFUND_PROVIDER_ERROR` | PG 취소 실패 | 운영 재처리 |

PG 오류 원문 코드는 서버 로그의 안전한 매핑값으로만 남기고 클라이언트에는 계약된 코드와 일반화한 문구를 반환한다.

### 4.8 운세 계산·문장 생성

| HTTP | code | 의미 | 처리 |
|---:|---|---|---|
| 404 | `READING_NOT_FOUND` | 결과 없음 또는 소유권 없음 | 목록 복귀 |
| 409 | `READING_ALREADY_FULFILLED` | 이미 완성된 구매를 다시 생성 요청 | 기존 결과 반환 |
| 422 | `CALCULATION_INPUT_UNSUPPORTED` | 계산 엔진이 입력 조합을 지원하지 않음 | 입력 수정 |
| 500 | `CALCULATION_FAILED` | 결정론적 만세력 계산 실패 | 환급·운영 알림 |
| 500 | `READING_GENERATION_FAILED` | 재시도 후 최종 결과 생성 실패 | 환급·재시도 안내 |
| 502 | `AI_PROVIDER_ERROR` | Liner 호출 오류 | 서버 제한 재시도 |
| 502 | `AI_RESPONSE_INVALID` | Liner 출력 schema·근거 검증 실패 | 서버 제한 재시도 |

- `AI_RESPONSE_INVALID`에는 AI가 계산 JSON에 없는 명리 정보를 생성했거나 허용하지 않은 섹션을 반환한 경우가 포함된다.
- 최종 `READING_GENERATION_FAILED`가 되면 등껍질 구매는 보상 거래로 복구한다.
- 이미 성공한 `generationKey`가 있으면 외부 호출 없이 최초 스냅샷을 반환한다.

### 4.9 부적·파일 저장소

| HTTP | code | 의미 |
|---:|---|---|
| 404 | `TALISMAN_NOT_FOUND` | 부적 없음 또는 권한 없음 |
| 409 | `TALISMAN_NOT_READY` | 아직 생성·업로드 중 |
| 409 | `TALISMAN_ALREADY_CLAIMED` | 같은 획득 건을 이미 계정에 연결함 |
| 500 | `TALISMAN_GENERATION_FAILED` | 이미지 합성 최종 실패 |
| 502 | `STORAGE_PROVIDER_ERROR` | R2 업로드·signed URL 발급 실패 |

### 4.10 선물·토큰·알림톡

| HTTP | code | 의미 | 노출 정보 |
|---:|---|---|---|
| 404 | `GIFT_NOT_FOUND` | 토큰/선물 없음 | 선물 상세 미노출 |
| 409 | `GIFT_ALREADY_OPENED` | 제공 개시 후 취소 등 허용되지 않는 명령 | 상태만 |
| 409 | `ALREADY_SUBMITTED` | 수신자 정보가 이미 잠김 | 기존 입력값 미반환 |
| 409 | `DELIVERY_ALREADY_SENT` | 동일 전달이 이미 성공 | 기존 전달 상태 |
| 410 | `GIFT_EXPIRED` | 링크 만료 | 선물 상세 미노출 |
| 410 | `GIFT_REVOKED` | 재발급·관리자 처리로 토큰 폐기 | 선물 상세 미노출 |
| 422 | `RECIPIENT_PHONE_INVALID` | 알림톡 수신 번호 형식 오류 | 전화번호 원문 미반환 |
| 502 | `NOTIFICATION_PROVIDER_ERROR` | 알림톡 대행사 통신 오류 | provider 원문 미노출 |
| 502 | `ALIMTALK_DELIVERY_FAILED` | 알림톡 최종 발송 실패 | 마스킹 번호·상태만 |

알림톡 발송 실패는 결제 성공이나 선물 활성화를 자동 취소하지 않는다. 재시도·SMS 대체 정책은 `TBD(G-10)` 확정 후 적용한다.

### 4.11 카카오톡 공유

| HTTP | code | 의미 |
|---:|---|---|
| 404 | `SHARE_NOT_FOUND` | 공개 공유 리소스 없음 |
| 410 | `SHARE_EXPIRED` | 공유 링크 만료 |
| 410 | `SHARE_REVOKED` | 사용자가 공유를 철회함 |
| 422 | `SHARE_SCOPE_NOT_ALLOWED` | 개인정보 또는 허용되지 않은 섹션 요청 |

`SHARE_EXPIRED`, `SHARE_REVOKED`의 사용 시점은 공유 정책 `TBD(G-11)` 확정 후 활성화한다.

### 4.12 관리자

| HTTP | code | 의미 |
|---:|---|---|
| 403 | `ADMIN_ROLE_REQUIRED` | ADMIN 권한 필요 |
| 403 | `CS_ROLE_REQUIRED` | CS 이상 권한 필요 |
| 400 | `AUDIT_REASON_REQUIRED` | 민감 작업의 사유 누락 |
| 409 | `ADJUSTMENT_ALREADY_APPLIED` | 같은 잔액 조정 중복 |
| 422 | `DIRECT_PERSON_EDIT_NOT_ALLOWED` | 운영자의 생년정보 직접 수정 시도 |

## 5. Spring Boot 예외 처리 규칙

### 예외 계층

```text
RuntimeException
└── BusinessException
    ├── AuthenticationException
    ├── AuthorizationException
    ├── ValidationException
    ├── ResourceNotFoundException
    ├── ConflictException
    ├── ExternalProviderException
    └── InternalProcessingException
```

- 도메인·application service는 `BusinessException(ErrorCode, safeDetails)`만 던진다.
- controller마다 try/catch하지 않고 `@RestControllerAdvice` 한 곳에서 응답으로 변환한다.
- Bean Validation 오류는 `VALIDATION_FAILED`와 `fieldErrors`로 변환한다.
- unreadable JSON은 `MALFORMED_JSON`, 잘못된 enum/타입은 `VALIDATION_FAILED`로 통일한다.
- 예상하지 못한 예외는 `INTERNAL_SERVER_ERROR`로 변환하고 원본 예외는 Sentry에만 전송한다.
- 외부 provider adapter가 받은 오류는 내부 provider code로 분류한 뒤 공개 코드로 매핑한다.

### 권장 `ErrorCode` 속성

```java
public enum ErrorCode {
    INSUFFICIENT_BALANCE(HttpStatus.CONFLICT, "등껍질이 부족합니다.", LogLevel.INFO),
    PAYMENT_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY, "결제 처리 중 오류가 발생했습니다.", LogLevel.ERROR),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "잠시 후 다시 시도해 주세요.", LogLevel.ERROR);

    private final HttpStatus status;
    private final String defaultMessage;
    private final LogLevel logLevel;
}
```

실제 구현에서는 사용자 노출 문구와 국제화를 고려해 message key로 분리할 수 있다. `code` 자체는 변경하지 않는다.

## 6. traceId·로그·모니터링

- 유효한 `X-Request-Id`가 들어오면 상관관계 ID로 사용할 수 있지만, 길이·문자셋을 제한한다. 없거나 유효하지 않으면 서버가 새 ULID/UUID를 생성한다.
- 응답 body의 `traceId`와 `X-Trace-Id` header에 같은 값을 넣는다.
- MDC에 `traceId`, 내부 user UUID, endpoint, status, error code를 넣는다.
- 다음 값은 로그·Sentry breadcrumb에서 제거한다.
  - 세션·CSRF·OAuth·선물 토큰 원문
  - 이름, 생년월일시, 성별, 휴대전화번호
  - 자유 메모, 선물 메시지, 사주 결과 본문
  - PG key와 카드·결제수단 원문
- `WALLET_BALANCE_MISMATCH`, 중복 차감 징후, 결제 승인 불일치, 결과 생성 실패 급증, 알림톡 실패 급증은 즉시 운영 알림 대상이다.

## 7. 프론트 처리 계약

| 분류 | 처리 |
|---|---|
| `401` | 로그인 modal/페이지 → 성공 후 원 경로 복귀 |
| `403 CSRF_FAILED` | CSRF 갱신 또는 새로고침 후 명령 1회 재시도 |
| `409 INSUFFICIENT_BALANCE` | 부족분과 추천 충전 상품 modal 표시 |
| `409 IDEMPOTENCY_REQUEST_PROCESSING` | 중복 결제 버튼을 풀지 않고 상태 조회 |
| `410 GIFT_*` | 상세 없이 만료·폐기 전용 화면 |
| `422 VALIDATION/PRODUCT` | 해당 입력 또는 상품 선택 화면으로 복귀 |
| `429` | `Retry-After` 동안 CTA 비활성화 |
| `5xx/502` | 일반 오류 안내 + traceId + 안전한 재시도 |

프론트는 알 수 없는 `code`를 받을 수 있다고 가정하고 HTTP status 기반 fallback을 제공한다.

## 8. 멱등 응답 보관

- 결제·충전·재화 차감·운세 구매·부적 추가 구매·선물 주문·환불은 `Idempotency-Key`가 필수다.
- 동일 actor·operation·key·본문이면 최초 HTTP status와 안전한 응답을 반환한다.
- 같은 key에 본문이 다르면 `IDEMPOTENCY_KEY_REUSED`다.
- 저장 응답에는 signed URL, 세션, 선물 token 원문처럼 재사용하면 안 되는 값을 넣지 않는다. 필요한 경우 리소스 ID만 저장하고 재조회 시 새 URL을 발급한다.
- 처리 중 worker가 중단되면 lease/timeout 후 상태를 대사한다. 단순히 같은 명령을 다시 실행해 중복 결제·지급하지 않는다.

## 9. API 문서 작성 규칙

- [`API_SPEC.md`](./API_SPEC.md)의 각 JSON 예시는 별도 표기가 없으면 `data` 내부 payload만 나타낸다.
- 실제 HTTP 응답에서는 이 문서의 성공 envelope를 적용한다.
- 오류 예시는 이 문서의 전체 오류 envelope를 그대로 사용한다.
- 새 업무 오류가 필요하면 기존 code로 표현할 수 없는지 먼저 확인하고, 이 문서와 OpenAPI enum을 함께 갱신한다.
- v1에서 기존 code의 의미를 바꾸거나 제거하지 않는다. 더 이상 쓰지 않는 code는 deprecated로 남긴다.
