# 데모데이 와이어프레임 기준 백엔드 API 점검

기준일: 2026-10-15
원본: `데모데이 와이어프레임.pdf` 89쪽
비교 대상: 현재 controller, 공유/gift OpenAPI, `FUNCTIONAL_SPEC.md`, `API_SPEC.md`
제외: 승인 에셋이 필요한 실제 부적 bitmap 생성·저장·R2 연결

## 결론

로그인·인물·오늘의 운세·일반 운세/수능운 구매와 재조회·충전 주문·부적 보관함 기본 조회는 현재 API로 대응된다. 와이어프레임을 실제로 완성하려면 아래 API 또는 계약 보완이 필요하다.

| 우선순위 | 화면 | 현재 상태 | 필요한 조치 |
|---|---|---|---|
| P0 | 선물 수신 결과 72~82쪽 | access/profile만 있고 결과 HTTP API와 실제 worker 없음 | option별 공개 결과 snapshot, 202 polling, 최초 제공/취소 직렬화 |
| P0 | 부적만 선물 79~82쪽 | quote가 `SUNEUNG_CHECKLIST`를 잘못 포함 | `LETTER + TALISMAN`으로 수정하고 결과 projection 누출 테스트 |
| P0 | 결과 후 부적 추가 구매 32~34쪽 | API 문서 초안만 있고 controller 없음 | reading 소유권 기반 견적/구매/status, 단일 차감·단일 talisman |
| P0 | 보낸 선물 46~47쪽 | 목록에 수신자명·option·열람/취소 가능 상태가 없음 | owner-only 최소 projection 보강 |
| P1 | 등껍질 내역 44쪽 | DB query/use case는 있으나 사용자 controller/OpenAPI 없음 | `GET /wallet/transactions`, 전체/충전/사용 필터용 안전한 분류 |
| P1 | 수능 판매 종료 39쪽 | 종료 상품이 catalog 목록에서 사라짐 | `UPCOMING/AVAILABLE/ENDED` 반환, AVAILABLE만 견적 허용 |
| P1 | 일반 결과/부적 공유 31·34·57·78·82쪽 | 오늘의 운세 공유만 구현 | 비식별 share snapshot·공개 landing·철회/만료 API |
| P1 | 받은 선물 48쪽 | 내부 owner port만 있고 연결/목록 HTTP API 없음 | 로그인 claim/link와 받은 선물 cursor 목록 |
| P1 | 부적 창고 9~10쪽 | 현재 flat cursor 목록은 있으나 API 문서의 filter/group/thumbnail/share 예시는 실제 코드와 다름 | 문서를 현행화하고 실제 에셋 승인 뒤 thumbnail/share 계약 추가 |
| P2 | 알림톡 선물 46~55쪽 | 현재 1차 직접 링크 코드, provider 미연결 | 1차 출시 뒤 전화번호·delivery/outbox/webhook·재발송 추가 |
| 보류 | 여러 명 선물 61~63쪽 | 1명만 지원 | 정책/가격/부분 실패 확정 후 별도 batch 계약 |

## 선물 전달 단계

### 1차 퀵오픈

- 주문 입력: 수신자 표시 이름, 보내는 사람 이름, 메시지. 전화번호 없음.
- 구매 완료 뒤 구매자에게 `/g#token=...` 링크를 제한된 공개 창에서 제공한다.
- 구매자가 링크를 직접 복사/공유한다.
- 링크 오류는 주문 조회로 복구하고, 명시적 재발급은 기존 token을 폐기한다.

### 2차 카카오 알림톡

- 와이어프레임의 전화번호 입력, 발송 중/완료/실패, 다시 보내기를 추가한다.
- delivery 상태는 결제·선물 ACTIVE·콘텐츠 상태와 분리한다.
- 발송 실패는 자동 취소/환급하지 않고 직접 링크 복사 fallback을 제공한다.
- provider timeout/결과 불명에는 새 발송을 즉시 만들지 않고 기존 delivery lookup/reconciliation을 수행한다.
- 실제 구현/활성화 전에 대행사, 템플릿, 발신 프로필, webhook 서명, 전화번호 보관기간을 확정한다.

## 현재 API로 대응되는 화면

- 카카오 로그인, 현재 세션, 로그아웃
- 내 정보와 저장 인물 CRUD, 동의 저장
- 오늘의 운세와 24시간 비식별 공유
- 일반 운세 5종 질문/견적/구매/결과, 구매 처리 중·검토·환급 상태
- 수능운 typed guide: 요약, 시험일 흐름, 교시별 응원, 집중 팁, 음식, 준비물, 마음 관리, 부족한 기운
- 내 운세 기록의 fortuneType/person 필터와 cursor 조회
- 부적 목록/상세, PENDING/READY/FAILED, 소유자 다운로드 경계
- 충전 상품, 주문 생성/승인/조회, 처리 중·취소·상품 없음 상태
- 회원 탈퇴 요청 시 유료 등껍질 잔액 차단
- 선물 견적/주문/등껍질 구매/취소/직접 링크 조회·재발급

## 추가로 확인이 필요한 화면·정책

1. PDF에는 충전 상품 총 지급량이 `580개`와 `582개`로 섞여 있다. 현재 서버 계약은 유료 500 + 보너스 80 = **580개**다.
2. 환불 문구가 “7일 내 전액”과 “남은 유료 등껍질 환불”로 혼재한다. 현재 서버 정책은 고객센터 접수 기반 잔여 유료분 환불 계약이므로 PD 원고를 하나로 맞춰야 한다.
3. 로그인 화면이 카카오·네이버·구글 3종인 페이지와 카카오 단독 페이지로 섞여 있다. 현재 backend는 카카오만 구현돼 있다.
4. 회원 탈퇴 화면은 보낸 미개봉 선물을 즉시 열 수 없게 한다고 안내한다. 현재 탈퇴 API의 유료 잔액 차단은 구현돼 있지만 선물 token 차단이 같은 transaction에서 확정되는지는 추가 검증이 필요하다.
5. 일반 운세·부적 공유 랜딩은 카드 필드가 보이지만 만료·철회·탈퇴 후 처리 정책은 아직 `G-11` 확정이 필요하다.

## 구현 순서 제안

1. `TALISMAN_ONLY` 포함 콘텐츠 수정과 회귀 테스트
2. 공개 선물 immutable result snapshot + worker + `POST /public/gifts/result`
3. 보낸 선물 최소 projection 보강
4. 부적 추가 구매
5. BE-A의 wallet transactions와 catalog availability 연결
6. 받은 선물 연결/목록
7. 일반 결과·부적 공유 정책 확정 후 API
8. 퀵오픈 안정화 뒤 알림톡 2차 전환

각 feature는 PostgreSQL 중복·동시성·중단 복구·소유권 테스트, OpenAPI export, backend CI, 코드 리뷰를 마친 뒤 다음 feature branch로 분리한다.
