# FE 10/11 요청: BE-A 범위와 진행 결정

원본: 사용자 제공 `뿌기사주 팀 요청서 (FE, 10 11).md`. 원본의 main f704145 점검과 A 로컬 구현을 구분한다.
이 문서는 회신 초안이며 팀에 전송하거나 배포한 상태가 아니다.

## 1. 담당 구분

| 요청 | A 책임 | 다른 담당/협의 |
|---|---|---|
| Q-17 충전 승인/지급/주문 조회 | 승인 요청 검증, PG 조회, 원장/lot/잔액/주문 원자적 지급, 결과 불명 복구 | FE PG 복귀 및 완료 화면, B 로그인/CSRF |
| Q-28 상품 목록/견적 이름/정가 | 서버 카탈로그, 가격 snapshot, HTTP/OpenAPI | PD 표시명 최종 문구, FE adapter |
| R-09 테스트 키 | 서버 secret 환경 설정 및 팀 상점 키 확인 | FE client 키 설정, 상점 계정 관리자 |
| R-01/Q-25 토스 상담 | 기술 연동 및 확인 질문 정리 | 사업자/PG 담당자가 상담·상품 분류·수단 확인, PD 정책 원고 |
| Q-20 선물 | 견적/주문/지급·token·발송 복구 | PD 선물 정책, B 콘텐츠/부적, FE 화면 |
| Q-18/작은 오류 계약 | 필수 헤더, 오류 응답 스키마/코드와 실제 응답 일치 | details 세부 의미는 기존 COMMON 계약 확인 후 구현 |
| Q-38 f | A 원장 receipt 조회 port, B 보상 오케스트레이션 인계 | B 구매 상태 조회/lease/fencing 구현; 안전한 소유자 인자 최종 확인 |
| Q-38 g/푸터 | 실제 문의 채널/사업자 정보가 서버·화면과 일치하도록 점검 | 사업자/PD 실제 정보 및 문구 확정 |
| R-06 | BE 배포 환경/DB/마이그레이션·readiness 확인 | A+B+FE 고정 주소/운영 주체/기동 일정 확정 |

로그인·세션·인물·ReadingSubjectPort, 구매 조회/생성 재개, 수능 결과 구조, 오늘의 운세는 B 범위다.
질문 칸 대응, 결제 실패/동의 문구, 환불/이용 정책과 푸터 원고는 PD 범위다. A가 대신 완료 날짜를 약속하지 않는다.

## 2. 사용자와 정한 순서

1. 상품 목록·견적 표시 정보.
2. 충전 승인·지급·복구 및 주문 조회 확장.
3. 로그인/인물 준비 후 FE 실제 연결 테스트.
4. 선물 API.

사용자가 추천 순서에 동의했고 토스 테스트 키 준비를 알렸다. 팀 상점용인지 문서 예제용인지는 미확인이다.
키 값은 문서/메시지/로그/Git에 넣지 않는다. 현재 상담 결과, 문의 이메일, 고정 배포 주소는 확인되지 않았다.
10/12 사이트·10/14 심사 일정은 FE 목표이며 A 완료/배포 보장 날짜가 아니다.

## 3. 이번 Q-28 구현을 FE에 전달할 내용

- 비로그인 `GET /api/v1/products?category=TOP_UP` 및 `?category=FORTUNE&fortuneType=LOVE`.
- 현재 판매 가능한 상품만 `data.items`로 반환. 없는 상품은 구매 버튼을 제공하지 않는다.
- 운세: code, category, fortuneType, **option**, productName, price, regularPrice, active, saleEndsAt.
- 충전: 기존 paidAmount/bonusAmount/creditedAmount 유지, productName/regularPrice/nullable 운세 필드 추가.
- 재화 표시명은 **등껍질**이다. 충전 상품명은 `등껍질 100개 충전` 형태이며 TURTLE_SHELL 등 기술 식별자는 유지한다.
- 목록 option은 견적 요청 productOption에 대응한다. 실제 차감은 charged/price, regularPrice로 결제하지 않는다.
- 일반/수능 견적 POST 및 GET: **productName**, **regularPrice** 추가. 정가는 Money 또는 null, 부적 포함 상품 20개/판매가 15개.
- 새 견적은 이름·정가까지 snapshot. 기존/구 버전 견적은 코드 이름/정가 null로 안전하게 표시한다.
- 상품명은 현재 카테고리 명칭을 사용한 서버 기본값이다. PD 최종 문구 확인 시 카탈로그 변경으로 반영한다.
- 수능 상품 DB 등록 누락을 보완하되 비활성으로 등록. 충전도 계속 비활성이다. 심사용 소개 목록/활성화는 별도 확인한다.
- 로컬 코드/계약 변경이며 main 병합과 배포 전까지 FE 실제 서버에서 제공된다고 볼 수 없다.

## 4. 다음 충전 작업의 완료 기준

10/11 업데이트: 아래 승인·지급·lease·제한된 조회 복구와 공통 오류 계약은 로컬 구현했다.
FE 연결 필수 계약은 FE_TOP_UP_DELIVERY_20261011.md에 정리했다. 실제 PG/브라우저 E2E는 별도이며
Q-20 선물은 아직 미구현이다. 전체 FE 요청 해결로 표시하지 않는다.

- FE는 paymentKey/orderId/amount를 넘기고 서버는 소유권·주문 금액·승인 증거를 검증한다.
- PG 호출 대기 중 지갑/주문 DB 잠금을 잡지 않는다. 준비 저장, 외부 호출, 지급 저장은 별도 경계다.
- 지급 트랜잭션 안에서 원장·lot·잔액·CREDITED를 함께 저장한다. 일부 실패면 전부 롤백한다.
- 같은 주문의 반복 승인/조회/worker 실행에도 지급 1회. timeout은 실패가 아니라 결과 불명으로 조회/복구한다.
- CREDITED 이전 예정 지급량은 완료 표시 금지. processing과 지급 후 잔액은 의미/스키마를 맞춰 제공한다.
- 재시작·동시 요청·PG 승인 후 DB 실패·응답 유실 테스트를 전용 PostgreSQL에서 검증한다.

실제 PG 승인/지급·브라우저 E2E·staging 배포·심사 제출은 이번 Q-28 구현 완료와 별개로 남아 있다.

## 5. 이번 검증 결과

전용 PostgreSQL 17.11(55432/sajuppugi_test) 전체 회귀 357개 중 354개 통과, 실패/오류 0, 의도적 제외 3.
check/bootJar/exportOpenApi 성공. 별도 OpenAPI task 4개 모두 통과했고 생성 JSON의 Money/null 및 option/null 분기를 확인했다.
기능 로컬 커밋: `0a5a777` (main 미병합).
상품 필터/표시 값, 견적 snapshot, 기존 권한·잔액·원장, OpenAPI nullable 금액/옵션을 검증했다.
기록은 docs/OPERATIONS_CHECKLIST.md에 유지한다. 실제 PG 호출/FE 브라우저 E2E/운영 배포는 수행하지 않았다.
