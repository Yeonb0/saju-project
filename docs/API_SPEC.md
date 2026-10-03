# 뿌기사주 API 명세서

> 버전: Draft v0.2
> 작성일: 2026-10-01
> 최종 수정: 2026-10-03
> 상태: P0 정책 결정 전 초안
> 참조: [`PRD.md`](./PRD.md), [`FUNCTIONAL_SPEC.md`](./FUNCTIONAL_SPEC.md), [`PENDING_DECISIONS.md`](./PENDING_DECISIONS.md), [`COMMON_RESPONSE_AND_ERROR_CODES.md`](./COMMON_RESPONSE_AND_ERROR_CODES.md)

## 1. 기본 규약

### 구성

- Backend: Spring Boot 3 + PostgreSQL
- Public prefix: `/api/v1`
- Frontend는 Next.js `/api` 프록시를 통해 같은 출처로 요청한다.
- JSON 필드명은 `camelCase`, enum은 `UPPER_SNAKE_CASE`를 사용한다.
- 시간은 ISO 8601 UTC로 전송하고 날짜 의미는 `Asia/Seoul`로 계산한다.
- 외부 리소스 ID는 UUID 문자열이다.

### 인증

- 서버 세션 ID를 `HttpOnly + Secure + SameSite=Lax` 쿠키로 전달한다.
- 인증 필요 엔드포인트는 세션이 없으면 `401 AUTHENTICATION_REQUIRED`를 반환한다.
- 소유권이 없으면 존재 여부를 숨겨야 하는 리소스는 `404`, 그 외는 `403`을 사용한다.
- 상태 변경 요청은 CSRF 토큰 및 Origin 검증을 적용한다.
- 공개 선물 API는 세션 대신 URL 토큰을 bearer secret으로 사용한다.

### 요청 헤더

| 헤더 | 적용 | 설명 |
|---|---|---|
| `Content-Type: application/json` | JSON 요청 | 본문 형식 |
| `X-CSRF-Token` | 세션 기반 변경 요청 | CSRF 방어 |
| `Idempotency-Key` | 명시된 POST | UUID 권장 |
| `X-Request-Id` | 선택 | 클라이언트 추적 ID |

### 멱등성

- 서버는 `사용자/익명 주체 + 명령 종류 + Idempotency-Key + 요청 본문 지문`을 저장한다.
- 동일 키·동일 본문은 최초 응답을 반환한다.
- 동일 키·다른 본문은 `409 IDEMPOTENCY_KEY_REUSED`다.
- 처리 중인 동일 요청은 `409 IDEMPOTENCY_REQUEST_PROCESSING` 또는 기존 작업 상태를 반환한다.
- PG의 멱등성 보장과 별개로 내부 기록을 유지한다.

### 페이지네이션

```json
{
  "data": {
    "items": [],
    "nextCursor": null
  },
  "traceId": "01K..."
}
```

- `limit`: 기본 20, 최대 100
- `cursor`: 서버가 반환한 opaque cursor

### 공통 응답·오류

- 모든 JSON 성공 응답은 `{ "data": ..., "traceId": "..." }` envelope를 사용한다.
- `204 No Content`는 body를 반환하지 않는다.
- 이 문서의 endpoint별 JSON 예시는 별도 표기가 없으면 `data` 내부 payload만 나타낸다.
- 전체 응답 포맷, HTTP status, 예외 코드의 기준 원본은 [`COMMON_RESPONSE_AND_ERROR_CODES.md`](./COMMON_RESPONSE_AND_ERROR_CODES.md)다.
- 오류 응답은 다음 포맷을 사용한다.

```json
{
  "code": "INSUFFICIENT_BALANCE",
  "message": "등껍질이 부족합니다.",
  "traceId": "01K...",
  "fieldErrors": [
    { "field": "birthDate", "reason": "OUT_OF_RANGE" }
  ],
  "details": {
    "required": 55,
    "balance": 40,
    "shortage": 15,
    "currency": "TURTLE_SHELL"
  }
}
```

## 2. 공통 타입

```ts
type FortuneType =
  | "OVERALL"
  | "LOVE"
  | "WEALTH"
  | "COMPATIBILITY"
  | "SINSAL"
  | "SUNEUNG";

type ProductOption = "READING_ONLY" | "READING_WITH_TALISMAN";
type GiftOption = "TALISMAN_ONLY" | "READING_WITH_TALISMAN";
type CalendarType = "SOLAR" | "LUNAR";
type Gender = "MALE" | "FEMALE" | "UNSPECIFIED";
type RelationType = "FAMILY" | "FRIEND" | "LOVER" | "CRUSH" | "WORK_SCHOOL" | "OTHER";
type TalismanStatus = "PENDING" | "READY" | "FAILED";

type ReadingSectionType =
  | "TEXT"
  | "PERIOD_GUIDANCE"
  | "FOOD_RECOMMENDATION"
  | "CHECKLIST";
```

- 사주 원국, 간지, 십성, 12운성, 합충형파해, 대운·세운은 오픈소스 만세력 라이브러리와 서버 계산 엔진으로만 계산한다.
- 계산 엔진 출력은 서버 내부의 구조화된 JSON으로 정규화하고 Liner API에 전달한다. Liner는 이 JSON에 근거한 사주 해석·설명 문장만 생성한다.
- Liner에는 계산 결과, 상품·이벤트 맥락, 선택형 관심 항목, 허용 섹션을 전달한다. 원본 생년월일시와 자유 메모 등 문장 생성에 불필요한 개인정보는 전달하지 않는다.
- 요청에는 계산 결과에 없는 필드 목록과 허용 근거 키를 포함한다. Liner가 미제공 명리 정보를 계산·보완·추론하거나 허용 범위 밖의 섹션을 반환하면 검증 실패로 처리한다.
- 자유 메모는 저장·표시만 할 수 있으며 계산 엔진이나 Liner 입력으로 전달하지 않는다.

### 내부 Liner 생성 계약

Liner 연동은 외부에 공개되는 API가 아니다. 백엔드 내부 어댑터가 다음 형태의 입력을 구성한다.

```json
{
  "generationKey": "sha256:...",
  "calculationVersion": "manse-1.0.0",
  "generationVersion": "liner-reading-v1",
  "fortuneType": "SUNEUNG",
  "referenceDate": "2026-11-19",
  "interestKey": "EXAM_FOCUS",
  "facts": {
    "pillars": { "year": "...", "month": "...", "day": "...", "hour": "..." },
    "tenGods": ["..."],
    "twelveStages": ["..."],
    "interactions": ["..."],
    "luckCycles": ["..."]
  },
  "missingFields": [],
  "allowedSections": ["SUMMARY", "PERIODS", "MEAL", "PREPARATION"]
}
```

Liner 응답은 백엔드가 지정한 JSON 스키마를 따라야 한다.

```json
{
  "sections": [
    {
      "key": "SUMMARY",
      "content": "차분하게 순서를 지키면 집중력을 유지하는 데 도움이 돼요.",
      "sourceFactKeys": ["tenGods[0]", "luckCycles[0]"]
    }
  ],
  "omittedSections": []
}
```

- `facts`에는 계산 엔진이 확정한 값만 넣는다. 예시의 배열·객체는 실제 계산 스키마 확정 후 구체화한다.
- `generationKey`는 정규화된 인물정보, 상품 코드, 기준일/이벤트일, `calculationVersion`, `generationVersion`을 결합해 만든다.
- 같은 `generationKey`에 성공 스냅샷이 있으면 Liner를 다시 호출하지 않고 최초 결과를 반환한다.
- Liner 응답은 허용된 JSON 출력 스키마, 섹션 목록, 금지 표현, 근거 키를 검증한 뒤 저장한다.
- 각 생성 섹션의 `sourceFactKeys`는 요청 `facts`에 실제로 존재하는 경로만 참조할 수 있다.
- 응답에 입력으로 제공되지 않은 명리 정보가 포함되거나 필수 검증을 통과하지 못하면 저장하지 않고 제한 횟수만 재시도한다.

버전 필드의 의미는 다음과 같다.

- `calculationVersion`: 만세력 라이브러리와 서버 계산 규칙 버전
- `generationVersion`: Liner 프롬프트, 모델 설정, 입력·출력 스키마와 검증 규칙 버전
- `contentVersion`: 음식·행운 아이템·부적·고지 문구 등 비생성형 콘텐츠 카탈로그 버전

### PersonInput

```json
{
  "displayName": "민지",
  "birthDate": "2008-03-12",
  "calendarType": "SOLAR",
  "isLeapMonth": false,
  "birthTime": "13:25",
  "birthTimeUnknown": false,
  "gender": "FEMALE"
}
```

- `birthDate`: 1900-01-01~요청일(KST)
- `isLeapMonth`: `LUNAR`일 때만 의미가 있다.
- `birthTime`과 `birthTimeUnknown=true`를 함께 보낼 수 없다.

## 3. 인증·세션

### `GET /auth/kakao/authorize`

- 인증: 불필요
- Query: `returnTo`(내부 상대 경로만 허용)
- 응답: 카카오 OAuth로 302 이동
- 서버가 OAuth `state`와 returnTo를 세션성 저장소에 보관한다.

### `GET /auth/kakao/callback`

- 인증: 불필요
- 카카오 콜백 처리 후 세션 쿠키 발급, 검증된 내부 returnTo로 이동

### `GET /session`

```json
{
  "authenticated": true,
  "user": { "id": "uuid", "nickname": "윤진" },
  "expiresAt": "2026-10-31T00:00:00Z"
}
```

### `POST /auth/logout`

- 인증·CSRF 필요
- 현재 세션 즉시 폐기
- 응답: `204`

### `GET /sessions`

- 활성 기기 세션 목록. 토큰·세션 ID는 노출하지 않는다.

### `DELETE /sessions/{sessionId}`

- 특정 기기 로그아웃. 현재 세션 삭제도 허용한다.

## 4. 사용자·인물

### `GET /me`

```json
{
  "id": "uuid",
  "nickname": "윤진",
  "walletBalance": 50,
  "hasPrimaryPerson": true,
  "createdAt": "2026-10-01T03:00:00Z"
}
```

### `DELETE /me`

- 인증·CSRF 필요
- 유료 잔액이 있으면 `409 PAID_BALANCE_REMAINS`와 환불 안내 정보를 반환한다.
- 탈퇴 접수 성공: `202 Accepted`

### `GET /people`

- 본인과 저장한 사람 목록

### `POST /people`

- 인증·CSRF 필요
- 본인 또는 타인 `PersonInput` 생성
- 타인은 `relation`, `thirdPartyAuthorizationConfirmed=true` 필수
- 타인 최대 10명, 초과 시 `409 PERSON_LIMIT_EXCEEDED`

### `GET /people/{personId}`

- 소유자 전용

### `PATCH /people/{personId}`

- 전체 계산 입력이 바뀌면 이후 구매에만 반영한다. 기존 결과는 변경하지 않는다.

### `DELETE /people/{personId}`

- 기존 구매 결과는 보존하되 새 구매 대상 목록에서 제거한다.

## 5. 상품 카탈로그

### `GET /products`

Query: `category=TOP_UP|FORTUNE|GIFT`, `fortuneType`

```json
{
  "items": [
    {
      "code": "LOVE_READING_ONLY",
      "category": "FORTUNE",
      "fortuneType": "LOVE",
      "option": "READING_ONLY",
      "price": { "currency": "TURTLE_SHELL", "amount": 30 },
      "active": true,
      "saleEndsAt": null
    },
    {
      "code": "TURTLE_SHELL_100",
      "category": "TOP_UP",
      "price": { "currency": "KRW", "amount": 10000 },
      "paidAmount": 100,
      "bonusAmount": 14,
      "creditedAmount": 114,
      "active": true
    }
  ]
}
```

- 비활성 상품은 구매할 수 없다.
- 선물 상품 원화 가격은 `TBD(P-03A)`가 확정된 뒤 등록한다.

### `POST /quotes/fortune`

선택한 상품·인물·옵션의 서버 견적을 반환한다.

```json
{
  "productCode": "SUNEUNG_READING_WITH_TALISMAN",
  "price": { "currency": "TURTLE_SHELL", "amount": 55 },
  "walletBalance": 50,
  "shortage": 5,
  "recommendedTopUp": "TURTLE_SHELL_10",
  "expiresAt": "2026-10-01T03:10:00Z"
}
```

## 6. 지갑·충전

### `GET /wallet`

```json
{
  "currency": "TURTLE_SHELL",
  "balance": 50,
  "paidBalance": 40,
  "bonusBalance": 10,
  "expiring": [{ "amount": 10, "balanceType": "BONUS", "expiresAt": "2027-03-30T15:00:00Z" }]
}
```

### `GET /wallet/transactions`

- Query: `type`, `cursor`, `limit`
- 유형: `TOP_UP`, `SPEND`, `REFUND`, `ADJUSTMENT`, `EXPIRATION`
- 원장 행은 수정하지 않고 역분개 행을 추가한다.

### `POST /top-up-orders`

- 인증·CSRF·`Idempotency-Key` 필요

```json
{ "productCode": "TURTLE_SHELL_50" }
```

```json
{
  "orderId": "uuid",
  "orderName": "등껍질 50 + 보너스 6개",
  "amount": 5000,
  "currency": "KRW",
  "paidShellAmount": 50,
  "bonusShellAmount": 6,
  "creditedShellAmount": 56,
  "status": "PAYMENT_PENDING"
}
```

충전 상품은 다음 여섯 개를 제공한다.

| productCode | 유료 | 보너스 | 총 지급 | 가격 |
|---|---:|---:|---:|---:|
| `TURTLE_SHELL_10` | 10 | 0 | 10 | 1,000원 |
| `TURTLE_SHELL_30` | 30 | 3 | 33 | 3,000원 |
| `TURTLE_SHELL_50` | 50 | 6 | 56 | 5,000원 |
| `TURTLE_SHELL_100` | 100 | 14 | 114 | 10,000원 |
| `TURTLE_SHELL_300` | 300 | 46 | 346 | 30,000원 |
| `TURTLE_SHELL_500` | 500 | 82 | 582 | 50,000원 |

- 승인 완료 시 유료분과 보너스분을 별도 원장 행 또는 동일 거래의 구분 가능한 lot으로 기록한다.
- 차감 우선순위는 `TBD(P-02A)`다.

### `POST /top-up-orders/{orderId}/confirm`

- 인증·CSRF·`Idempotency-Key` 필요

```json
{
  "paymentKey": "provider-payment-key",
  "orderId": "uuid",
  "amount": 5000
}
```

- 서버 저장 주문 금액과 PG 금액을 비교한다.
- 클라이언트 금액을 신뢰하지 않는다.
- 성공 시 지급까지 완료하고 잔액을 반환한다.

### `GET /top-up-orders/{orderId}`

- 상태: `CREATED`, `PAYMENT_PENDING`, `PAID`, `CREDITED`, `FAILED`, `CANCELED`, `REFUNDED`

### `POST /webhooks/toss-payments`

- 공개 인터넷 엔드포인트지만 PG 서명·이벤트 ID를 검증한다.
- 웹훅 원문에 민감정보를 남기지 않는다.
- 이벤트 ID를 멱등 처리한다.

## 7. 오늘의 운세

### `POST /daily-fortunes`

- 인증 불필요
- rate limit 적용

```json
{ "person": { "...PersonInput": "..." }, "date": "2026-10-01" }
```

```json
{
  "date": "2026-10-01",
  "summary": "차분하게 순서를 지키면 좋은 날이에요.",
  "luckyColor": "초록",
  "luckyItem": "작은 노트",
  "luckyPlace": "창가",
  "action": "할 일을 세 줄로 적어보세요.",
  "calculationVersion": "manse-1.0.0",
  "generationVersion": "liner-daily-v1",
  "contentVersion": "daily-2026.10.1"
}
```

- 서버는 동일 입력·날짜·계산/생성 버전에 대해 최초 성공 스냅샷을 재사용한다.
- 원본 인물정보를 분석 도구에 전송하지 않는다.

## 8. 유료 운세 구매·결과

### `POST /reading-purchases`

- 인증·CSRF·`Idempotency-Key` 필요
- 잔액 차감은 이 명령 내부에서 수행한다.

```json
{
  "productCode": "LOVE_READING_WITH_TALISMAN",
  "personId": "uuid",
  "interestKey": "NEW_RELATIONSHIP",
  "memo": "새로운 인연",
  "talismanType": "RELATIONSHIP"
}
```

궁합은 `personId`, `counterpartPersonId`, `relation`을 사용한다.

성공 `201`:

```json
{
  "purchaseId": "uuid",
  "readingId": "uuid",
  "status": "FULFILLED",
  "calculationVersion": "manse-1.0.0",
  "generationVersion": "liner-reading-v1",
  "contentVersion": "love-2026.10.20",
  "charged": { "currency": "TURTLE_SHELL", "amount": 55 },
  "balance": 45,
  "talisman": { "id": "uuid", "status": "PENDING" }
}
```

- 계산 실패, Liner 호출 실패 또는 Liner 응답 검증 실패가 서버 재시도 후에도 해소되지 않으면 구매는 `FAILED`, 차감은 역분개하고 `refunded=true`를 반환한다.
- 동일한 `Idempotency-Key` 또는 `generationKey`의 재요청은 외부 호출을 중복 실행하지 않고 진행 중 상태나 최초 완료 결과를 반환한다.
- 부적 상태 처리 방식은 `TBD(F-06)`다.

### `GET /readings`

- 본인 구매 결과 목록, cursor pagination
- 필터: `fortuneType`, `personId`

### `GET /readings/{readingId}`

```json
{
  "id": "uuid",
  "fortuneType": "SUNEUNG",
  "productOption": "READING_WITH_TALISMAN",
  "personSnapshot": { "displayName": "민지", "birthTimeUnknown": false },
  "event": { "type": "SUNEUNG", "date": "2026-11-19" },
  "sections": [
    {
      "key": "SUMMARY",
      "type": "TEXT",
      "title": "한줄 요약",
      "content": "..."
    },
    {
      "key": "PERIODS",
      "type": "PERIOD_GUIDANCE",
      "title": "교시별 흐름",
      "items": [
        { "label": "1교시", "guidance": "...", "focusPoint": "..." }
      ]
    },
    {
      "key": "MEAL",
      "type": "FOOD_RECOMMENDATION",
      "title": "도시락·간식",
      "primary": { "name": "...", "reason": "..." },
      "alternatives": [{ "name": "...", "reason": "..." }]
    },
    {
      "key": "PREPARATION",
      "type": "CHECKLIST",
      "title": "준비물",
      "items": [
        { "id": "admission-ticket", "label": "수험표", "personalized": false },
        { "id": "lucky-item", "label": "초록색 손수건", "personalized": true }
      ]
    }
  ],
  "calculationVersion": "manse-1.0.0",
  "generationVersion": "liner-reading-v1",
  "contentVersion": "suneung-2026.10.20",
  "createdAt": "2026-10-20T01:02:03Z",
  "disclaimers": ["FOR_ENTERTAINMENT", "BIRTH_TIME_LIMITED"],
  "share": {
    "title": "뿌기가 전하는 나의 수능운",
    "description": "재미로 보는 운세 결과를 확인해 보세요.",
    "imageUrl": "https://cdn.../share-card.webp",
    "webUrl": "https://service.example/share/opaque-id"
  }
}
```

- 계산 원국·내부 시드·원본 생년정보는 필요한 범위를 넘어 노출하지 않는다.
- 체크 여부는 클라이언트 로컬 상태이며 API의 결과 스냅샷에는 저장하지 않는다.
- `share`는 카카오톡 공유하기에 사용하는 개인정보 없는 공개용 메타데이터다. 소유자 전용 reading URL이나 생년정보를 포함하지 않으며 결과 공유 범위는 `TBD(G-11)`에 따른다.
- `TBD(F-09)`: 오행분석을 노출하면 별도 구조화 섹션 타입을 추가한다.

### `POST /readings/{readingId}/shares`

- 인증·소유권·CSRF·`Idempotency-Key` 필요
- 카카오톡 공유하기에 사용할 비식별 공유 리소스와 템플릿 데이터를 생성한다.
- 공유 리소스에는 사용자가 선택한 공개 가능 결과 output·브랜드 이미지·서비스 진입 URL을 포함할 수 있으며 인적정보는 포함하지 않는다. 전체/일부 섹션 범위는 `TBD(G-11)`이다.

### `POST /readings/{readingId}/talisman-purchases`

- 인증·CSRF·`Idempotency-Key` 필요
- `READING_ONLY` 사주 상품을 구매한 소유자만 가능
- 15 등껍질을 서버에서 확인·차감

## 9. 부적

### `GET /talismans`

- 로그인 사용자의 구매·수신 부적 목록
- Query: `animal`, `type`, `cursor`
- 동일 동물·종류 그룹에 `count` 제공

### `GET /talismans/{talismanId}`

```json
{
  "id": "uuid",
  "type": "FOCUS",
  "animal": "RAT",
  "status": "READY",
  "thumbnailUrl": "https://cdn...",
  "description": "...",
  "acquiredAt": "2026-10-20T01:02:03Z",
  "source": "PURCHASE",
  "share": {
    "title": "뿌기가 전하는 행운 부적",
    "description": "부적을 확인해 보세요.",
    "imageUrl": "https://cdn.../talisman-share.webp",
    "webUrl": "https://service.example/share/opaque-id"
  }
}
```

- `share`는 카카오톡 공유하기용이며 개인정보와 비공개 원본 signed URL을 포함하지 않는다.

### `POST /talismans/{talismanId}/download-url`

- 인증·권한·CSRF 필요
- 10분 signed URL 반환

```json
{ "url": "https://...", "expiresAt": "2026-10-20T01:12:03Z" }
```

### `POST /talismans/{talismanId}/shares`

- 인증·권한·CSRF·`Idempotency-Key` 필요
- 개인정보와 비공개 원본 URL이 없는 카카오톡 공유용 리소스를 생성한다.

### `GET /shares/{shareId}`

- 인증 없이 접근 가능한 공유 랜딩 데이터다.
- 불투명 난수 ID를 사용하며 공개용 제목·설명·이미지·서비스 CTA만 반환한다.
- 회원 ID, reading/talisman 내부 ID와 생년정보는 반환하지 않는다. 공개할 결과 범위는 `TBD(G-11)`에 따른다.

### `POST /talismans/{talismanId}/claim`

- 로그인한 선물 수신자가 공개 토큰으로 열람한 부적을 계정에 연결한다.
- 같은 획득 건을 중복 연결하지 않는다.

## 10. 선물 견적·주문

### `POST /gift-quotes`

```json
{
  "fortuneType": "LOVE",
  "option": "TALISMAN_ONLY"
}
```

- 응답에는 원화 가격, 제공 콘텐츠, 판매 가능 여부, 견적 만료 시각을 포함한다.
- 응답의 `includedContents`에는 옵션별 제공물(`LETTER`, `TALISMAN`, `READING`, `SUNEUNG_CHECKLIST`)을 명시한다.
- 선물 옵션은 `TALISMAN_ONLY`와 `READING_WITH_TALISMAN`만 허용하며 `READING_ONLY`는 거부한다.
- 수능 `TALISMAN_ONLY`에는 편지·부적·수능 체크리스트가 포함되고 전체 수능 사주 결과는 포함되지 않는다.
- 가격은 `TBD(P-03A)` 확정 후 고정한다.

### `POST /gift-orders`

- 인증·CSRF·`Idempotency-Key` 필요

```json
{
  "quoteId": "uuid",
  "recipientName": "민지",
  "recipientPhone": "01012345678",
  "senderName": "윤진",
  "message": "긴장하지 말고 잘 보고 와!",
  "talismanType": "FOCUS"
}
```

- 수신자 생년정보를 받지 않는다.
- `recipientPhone`은 알림톡 발송을 위한 필수 입력이다. 저장·조회·로그에서는 암호화 또는 마스킹하며 보관기간은 `TBD(G-10A)`다.
- 메시지는 200자·금칙어·제어문자 검증을 수행한다.

### `POST /gift-orders/{orderId}/confirm`

- 인증·CSRF·`Idempotency-Key` 필요
- 토스페이먼츠 승인 후 금액 일치 검증
- 성공 시 gift와 토큰 링크를 활성화하고 알림톡 발송 작업을 멱등하게 생성한다.
- 비동기 발송에 필요한 원문 토큰 URL은 암호화된 전달 outbox에만 보관하고 발송 성공 또는 재시도 보관기간 종료 후 삭제한다. gift 테이블에는 토큰 해시만 유지한다.

```json
{
  "orderId": "uuid",
  "giftId": "uuid",
  "status": "ACTIVE",
  "delivery": {
    "channel": "KAKAO_ALIMTALK",
    "status": "PENDING",
    "recipientPhoneMasked": "010-****-5678"
  }
}
```

### `GET /gift-orders`

- 구매자의 보낸 선물 목록
- 원문 토큰과 전체 휴대전화번호를 반환하지 않는다.

### `GET /gift-orders/{orderId}`

- 구매자 소유 주문·선물 상태

### `POST /gift-orders/{orderId}/deliveries`

- 인증·CSRF·`Idempotency-Key` 필요
- 기존 유효 링크를 동일 수신자에게 알림톡으로 재발송하고 감사 로그를 기록한다.

### `POST /gift-orders/{orderId}/link-reissues`

- 인증·CSRF·`Idempotency-Key` 필요
- 기존 토큰을 폐기하고 새 토큰을 발급한 뒤 수신자에게 알림톡으로 발송한다.
- 응답에는 새 원문 토큰을 반환하지 않고 발송 상태와 마스킹된 번호만 반환한다.

### `POST /webhooks/alimtalk`

- 알림톡 대행사의 서명과 이벤트 ID를 검증하고 멱등 처리한다.
- 발송 성공·실패·수신 불가 상태를 선물 전달 기록에 반영한다.
- 원문 휴대전화번호와 선물 토큰을 로그에 남기지 않는다.

### `POST /gift-orders/{orderId}/refunds`

- 사용자 셀프 취소 허용 범위 또는 관리자 권한
- 수신자가 선물을 열어 콘텐츠 제공이 시작되기 전까지만 정책상 허용되는 전액 취소를 처리한다.
- 제공 개시 기준은 `TBD(G-09)`다.

## 11. 공개 선물 API

경로의 `{token}`은 로그·분석·에러 보고에서 마스킹한다.

### `GET /gifts/{token}`

```json
{
  "status": "UNCLAIMED",
  "recipientName": "민지",
  "senderName": "윤진",
  "fortuneType": "SUNEUNG",
  "option": "TALISMAN_ONLY",
  "message": "긴장하지 말고 잘 보고 와!",
  "recipientSubmissionRequired": true
}
```

- 메시지는 API로 전달하되 OG 메타데이터에는 사용하지 않는다.
- `TALISMAN_ONLY`인 수능 선물은 부적·편지·수능 체크리스트만 노출하고 전체 사주 섹션은 반환하지 않는다.
- 만료/폐기 토큰은 각각 410을 반환하고 선물 상세를 노출하지 않는다.

### `POST /gifts/{token}/recipient`

- `Idempotency-Key` 필요
- `PersonInput` 제출, 최초 1회만 허용
- `409 ALREADY_SUBMITTED`에는 기존 입력값을 반환하지 않는다.

### `GET /gifts/{token}/result`

```json
{
  "giftStatus": "ACTIVE",
  "contentKinds": ["LETTER", "TALISMAN", "SUNEUNG_CHECKLIST"],
  "talismanId": "public-opaque-id",
  "checklistId": "public-opaque-id",
  "expiresAt": "2027-02-27T14:59:59Z"
}
```

- 수신자 정보 제출과 결과 생성이 완료된 뒤 단건 선물 전체를 반환한다.
- `TALISMAN_ONLY`에는 `readingId`를 반환하지 않는다. `READING_WITH_TALISMAN`에는 `READING`과 `readingId`를 추가한다.

### `POST /gifts/{token}/talismans/{talismanId}/download-url`

- 유효 토큰과 공개 상태 확인 후 10분 signed URL 반환

## 12. 관리자 API

모든 요청은 관리자 세션·CSRF·역할 검사와 감사 사유를 요구한다.

| Method | Path | 역할 | 기능 |
|---|---|---|---|
| GET | `/admin/users/{id}` | ADMIN/CS | 마스킹 계정 상태 |
| GET | `/admin/orders/{id}` | ADMIN/CS | 주문·결제 상태 |
| GET | `/admin/wallets/{userId}/transactions` | ADMIN/CS | 원장 조회 |
| POST | `/admin/wallets/{userId}/adjustments` | ADMIN/제한 CS | 보상·조정 |
| POST | `/admin/refunds` | ADMIN/제한 CS | 전액·부분 환불 |
| POST | `/admin/gifts/{giftId}/revoke` | ADMIN/CS | 토큰 폐기 |
| POST | `/admin/gifts/{giftId}/link-reissues` | ADMIN/CS | 토큰 재발급 |
| POST | `/admin/gifts/{giftId}/deliveries` | ADMIN/CS | 알림톡 재발송 |
| POST | `/admin/gifts/{giftId}/recipient-reset` | ADMIN/CS | 입력 잠금 초기화 |
| POST | `/admin/content-versions/{version}/deploy` | ADMIN | 콘텐츠 배포 |
| POST | `/admin/content-versions/{version}/rollback` | ADMIN | 롤백 |
| GET | `/admin/audit-logs` | ADMIN | 감사 로그 |

- 관리자 API로 사용자의 생년정보를 수정할 수 없다.

## 13. 상태 머신

### 충전 주문

`CREATED → PAYMENT_PENDING → PAID → CREDITED`

종료/예외: `FAILED`, `CANCELED`, `REFUNDED`

### 본인 구매

`CREATED → DEBITED → GENERATING → FULFILLED`

실패: `FAILED → REFUNDED`

### 선물 주문·선물

`CREATED → PAYMENT_PENDING → PAID → ACTIVE → COMPLETED`

예외: `CANCELED`, `REFUNDED`, `EXPIRED`

### 알림톡 전달

`PENDING → SENT | FAILED`

알림톡 전달 실패는 결제·선물 활성 상태와 별도로 기록한다. 자동 재시도 횟수와 SMS 대체 여부는 `TBD(G-10)`다.

### 수신 상태

`UNCLAIMED → RECIPIENT_SUBMITTED → ACTIVE → COMPLETED → EXPIRED`

링크 재발급: 기존 토큰 `REVOKED`

## 14. 보안·개인정보

- 선물 토큰은 32바이트 이상 난수 Base64URL로 만들고 gift의 영구 저장값은 해시만 유지한다. 알림톡 발송용 원문 URL은 암호화된 outbox에 한시 저장 후 삭제한다.
- 알림톡 수신 휴대전화번호는 암호화 저장하고 조회 화면·로그·Sentry·분석 데이터에서 마스킹한다.
- 비밀번호·PG 시크릿·OAuth 시크릿·토큰 원문을 로그에 기록하지 않는다.
- URL 경로 토큰은 액세스 로그·Sentry에서 마스킹한다.
- R2 원본은 비공개이며 signed URL은 10분 만료다.
- 관리자 개인정보 조회와 모든 금전성 변경을 감사 로그에 남긴다.
- PostHog에는 실제 인적정보·결과 본문·메시지를 전송하지 않는다.
- 공개 API에 rate limit과 abuse 방어를 적용한다.

## 15. OpenAPI·호환성

- Springdoc으로 OpenAPI JSON을 생성한다.
- 프론트는 `openapi-typescript`로 타입을 생성하며 수동 API 타입을 만들지 않는다.
- v1에서 필드 삭제·의미 변경·enum 재사용을 금지한다.
- 새 enum 값은 프론트의 unknown 처리 전략과 함께 배포한다.
- P0 `TBD`가 해결되기 전 선물 가격·제공 개시·환불 관련 스키마는 draft로 표시한다.

## 16. 확정 전 TBD

| 결정 | 영향 API |
|---|---|
| 선물 원화 가격 | `/products`, `/gift-quotes`, 주문 승인 |
| 미사용 링크 만료 | 공개 gift 조회·410 정책 |
| 선물 제공 개시 기준 | refund 요청·응답·취소 가능 상태 |
| 보너스/유료 등껍질 차감 순서 | 지갑 원장·환불 가능 잔액 계산 |
| 알림톡 대행사·템플릿·재시도·SMS 대체 | 선물 주문 승인·전달 상태·웹훅 |
| 알림톡 전화번호 보관기간 | 선물 주문 데이터·삭제 작업·관리자 조회 |
| 신살 결과·부적 구성 | 상품 카탈로그·Liner 스키마·결과 응답 |
| 개인 결과 공유 범위·만료·철회 | `/shares`, 카카오 공유 템플릿, 공개 랜딩 |
| 자시·역법·띠 기준 | person 검증·reading 생성·talisman 동물 |
| 부적 동기/비동기 | 구매 응답·talisman 상태·폴링 |
| CSRF·세션 초과 | 인증 필터·세션 API |
