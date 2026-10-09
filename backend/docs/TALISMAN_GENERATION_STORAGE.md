# 부적 생성·보관 구현 계획

## 목표와 이번 범위

운세 구매가 부적 fulfillment 경계의 mock 없이도 완료될 수 있도록 부적의 조합 정보를 만들고 DB에 보관한다.

이번 구현은 다음을 포함한다.

- 수능과 일반 운세가 함께 사용하는 부적 생성 계약
- 계산 facts에서 보완할 오행을 고르는 정책
- 12지 동물과 서버 기본 문구 풀의 무작위 선택
- `readingId`를 멱등성 키로 사용하는 생성·저장
- 부적 메타데이터와 최초 소유권의 영속화
- 이미지 생성 전 `PENDING`, 렌더링 완료 후 `READY`, 실패 시 `FAILED`로 바꾸는 저장소 계약

원본 PNG 합성, WebP 썸네일, 공개 공유 이미지, R2 업로드와 signed URL은 이번 범위에 넣지 않는다. 디자인 에셋과 `F-06` 동기/비동기 결정이 준비되면 별도 렌더러가 저장소의 상태 전이 계약을 사용한다.

## 확정 구현 규칙

### 조합 정책

1. `fiveElements.missing`에 값이 있으면 표준 순서 `WOOD, FIRE, EARTH, METAL, WATER` 중 첫 번째 값을 사용한다.
2. 명시적인 누락 오행이 없으면 오행 개수가 가장 적은 값을 사용하며, 동률은 같은 표준 순서로 결정한다.
3. 동물은 `SecureRandom`으로 12지 전체에서 선택한다.
4. 문구는 `fortuneType × element` 문구 풀에서 선택한다. 현재 문구는 서버 기본 카탈로그 버전으로 관리하고, 전달받은 reading `contentVersion`을 부적에도 기록한다.
5. 설명은 보완 오행과 색을 사용해 결정적으로 만든다.

동물과 문구는 최초 생성 때만 무작위로 정하고 이후 같은 `readingId` 요청은 저장된 값을 반환한다. 따라서 네트워크 재시도나 중복 구매 요청으로 컬렉션이 다시 추첨되지 않는다.

### 데이터와 개인정보

`talismans`에는 owner ID, source reading ID, 운세 종류, 오행, 동물, 문구, 설명, 콘텐츠 버전, 상태와 향후 object key만 저장한다. 이름, 생년월일시, 성별과 계산 facts 원문은 저장하지 않는다.

`talisman_ownerships`는 부적과 사용자 사이의 획득 관계를 저장한다. 최초 본인 구매는 `PURCHASE`이며, 이후 선물 claim은 같은 테이블에 `GIFT` 소유권을 추가한다.

### 상태 전이

```text
PENDING -> READY
PENDING -> FAILED
FAILED  -> PENDING  (명시적인 재시도 예약)
```

- 구매 시 메타데이터와 소유권 저장이 성공하면 `PENDING` fulfillment를 반환한다.
- object key 세 개(원본, 썸네일, 공유)가 모두 있어야 `READY`로 전이할 수 있다.
- 실패 코드는 내부 운영 정보이며 사용자 응답에 그대로 노출하지 않는다.
- 이미 `READY`인 부적은 재시도나 실패 상태로 되돌리지 않는다.

## 구현 순서와 검증

1. 공용 `TalismanFulfillmentPort`와 도메인 모델, 조합 정책을 추가하고 정책 단위 테스트를 작성한다.
2. `TalismanRepository` 포트와 Flyway 테이블을 추가한다. DB 제약, 소유권, `readingId` 멱등성을 통합 테스트한다.
3. `TalismanCreationService`로 정책과 저장소를 연결하고 기존 수능·일반 운세 구매 서비스를 공용 포트로 전환한다.
4. 같은 reading 재요청, 개인정보 미저장, 상태 전이, 번들 구매 연결을 회귀 테스트한다.
5. 백엔드 전체 테스트를 실행한다.

## 후속 작업 경계

- `TalismanRendererPort`: 1080×1920 PNG, 360×640 WebP, 공유용 WebP 생성
- `TalismanObjectStoragePort`: private object 저장·삭제와 10분 signed URL
- 렌더 작업 재시도/lease/outbox
- `/talismans` 창고 조회, 상세, 다운로드 URL, 공유 및 선물 claim API
- PD 최종 문구 카탈로그와 이미지 에셋 연결
