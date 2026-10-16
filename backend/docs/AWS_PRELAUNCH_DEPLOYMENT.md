# AWS 첫 배포 — PG 연결 전

대상: Java 21 / Spring Boot 백엔드. FE는 Amplify Hosting, BE는 Elastic Beanstalk Java SE / Corretto 21 배포 준비. AWS 자원 생성·업로드·실제 DB 변경은 이 문서 작성 단계에서 수행하지 않음.

## 범위

- `staging,prelaunch` 프로필로 로그인·인물 등록·기존 결과/무료 기능 연결 확인.
- PG 키 불필요. 결제·지갑 구매·선물 주문·관리자·생성 복구·부적 render 등 미준비 쓰기 기능 비활성.
- 활성화 시 기동 거절. PG가 정해진 이후 별도 배포 변경으로 해당 프로필 해제·결제 검증.
- 현재 main의 FE 직접결제 adapter와 BE의 API 호환은 별도 확인 대상. 이번 배포는 구매 완주 환경이 아님.
- 공유 환경의 fake Liner 금지. Liner API 키는 기동 시 필요하며, 무료 운세 등 실제 생성 요청에는 외부 API 비용 발생 가능.
- Spring 프로필 이름이 staging이어도 금융/개인정보 테스트용 기존 DB를 자동 생성하거나 초기화하지 않음.

## 업로드 ZIP 생성

저장소의 `backend` 폴더에서:

```powershell
.\gradlew.bat --no-daemon test --tests com.sajuppugi.PrelaunchDeploymentGuardTest --tests com.sajuppugi.TossPaymentConfigurationTest beanstalkPrelaunchBundle
```

산출물: `backend/build/distributions/sajuppugi-prelaunch.zip`.

ZIP 루트는 `app.jar`, `Procfile`, `.ebextensions/01-prelaunch.config`. 저장소 전체·소스·`.env`·키 파일을 업로드하지 않음. 이 작업 자체는 전체 회귀 테스트를 대체하지 않음.

`Procfile`은 `staging,prelaunch`를 명시하며, nginx와 앱의 포트는 환경 속성 `PORT=5000`으로 일치. readiness 경로는 `/actuator/health/readiness`.

## AWS에서 직접 설정할 항목

1. Java SE / Corretto 21의 지원되는 최신 Amazon Linux 플랫폼 선택. ZIP은 Java SE용이며 Docker/Tomcat/Amplify에 업로드하지 않음.
2. 환경의 비용·리전·인스턴스·로드밸런서 선택. 서울 리전 사용 여부는 팀이 확정. 단일 인스턴스에는 ALB health check 설정이 적용되지 않으므로 proxy 경유 readiness를 직접 확인.
3. 독립 staging PostgreSQL 준비. DB는 공개 인터넷 대신 앱 보안 그룹만 접근 허용. RDS를 쓴다면 앱 환경 삭제와 DB 삭제를 분리.
4. DB 연결 TLS와 서버 인증 설정. 예: `jdbc:postgresql://실제DB호스트:5432/DB명?sslmode=verify-full` 및 제공자 CA 신뢰 설정. CA 확인을 끄는 방식으로 해결하지 않음.
5. 아래 비밀 설정을 Secrets Manager/SSM 연동 또는 접근 통제된 환경 속성으로 등록. 실제 값은 Git·문서·채팅·FE에 복사하지 않음.
6. BE HTTPS 주소 준비. TLS 종료·인증서는 ALB/인증서 구성 등 선택한 방식으로 설정하며 이 ZIP은 HTTPS 자원을 생성하지 않음. 앱 5000 포트 직접 공개 금지.
7. Amplify의 FE 고정 HTTPS 도메인과 API proxy 연결. FE 수정은 FE 담당과 공동 진행.

| 환경 변수 | 값·의미 |
|---|---|
| `DB_URL` | staging PostgreSQL JDBC URL, TLS/CA 설정 |
| `DB_USERNAME`, `DB_PASSWORD` | 해당 DB 전용 계정·비밀번호 |
| `PERSON_ENCRYPTION_KEY` | 독립된 32바이트 난수의 Base64 |
| `GENERATION_CONTEXT_ENCRYPTION_KEY` | 별도로 만든 32바이트 난수의 Base64 |
| `KAKAO_REST_API_KEY`, `KAKAO_CLIENT_SECRET` | 서버용 카카오 설정 |
| `FRONTEND_BASE_URL` | 고정 FE HTTPS origin, 하위 경로/쿼리 제외 |
| `KAKAO_REDIRECT_URI` | 같은 FE origin + `/api/v1/auth/kakao/callback` |
| `LINER_API_KEY` | 실제 Liner 서버 키 |

암호화 키는 인스턴스 재시작·배포마다 다시 만들지 않음. 기존 DB를 쓴다면 기존 키를 유지하고 키 교체 절차는 별도 작업. PG 관련 키는 아직 등록하지 않음. 약관 동의 버전·인물 생성 동의 gate는 실제 FE 동의 화면과 서버 계약을 함께 확인한 후 적용하며 이 배포 작업에서 임의 변경하지 않음.

## FE·카카오 연결

- FE `API_PROXY_TARGET=https://실제BE주소`, `NEXT_PUBLIC_API_MODE=real` 설정 후 재빌드.
- 브라우저 요청은 FE `/api/v1` 경유. BE 직접 호출과 CORS 전체 허용으로 우회하지 않음.
- 카카오 Developers에 위 `KAKAO_REDIRECT_URI`와 정확히 같은 URI 등록.
- 콜백이 FE proxy를 경유하므로 세션 쿠키는 FE 도메인에서 저장. 세션·CSRF/Origin 검사는 유지.
- PG 미준비 안내·구매 버튼 노출은 FE 담당과 조정. BE 설정만으로 FE에서 결제창 버튼을 자동 숨기지는 않음.
- Amplify의 Next.js rewrites·쿠키·서버 환경 변수 반영은 실제 브라우저에서 검증. Amplify만으로 Java BE가 실행되지는 않음.

## 첫 기동·확인

- Flyway 자동 적용 전 DB snapshot/백업. 기존 migration 수정·삭제 없음.
- `/actuator/health/liveness`, `/actuator/health/readiness`: 200/UP, 상세 정보 비공개.
- 카카오 로그인 → 세션·CSRF 발급 → 인물 등록 → 새로고침 → 로그아웃.
- 다른 브라우저 계정으로 타인 인물·결과 접근 거절.
- 결제 adapter 없음, 구매/선물 활성화 거절, 공개 API 문서 비공개 확인.
- 외부 생성은 테스트 계정으로 최소 실행 후 호출량 확인. health=UP은 이 흐름 완료의 증거가 아님.
- 서버 재시작 후 로그인·저장 정보 유지 확인. 실제 값·쿠키·OAuth code·token·출생 정보가 프록시/ALB/CloudWatch/FE 관측에 남지 않는지 점검. request body 로깅 금지, callback query 마스킹/로그 수집 제외 구성 필요.
- DB snapshot을 별도 DB에 복원하고 동일 암호화 키로 조회 가능 확인. 복원 환경에 결제/worker를 켜지 않음.

## 알려진 제한

- AWS 계정/서비스·DB·도메인·비밀키·리전·비용 미설정. 실제 배포/HTTPS/로그인/복원 미검증.
- PG 직접 연결·테스트 결제·운영 판매·새 결제 구조 구현은 이번 작업 범위 밖.
- 현재 선물 cipher 자체가 local/test 전용. staging에서 이를 우회하거나 켜지 않음.
- 설정 guard는 prelaunch 프로필을 위한 보호이며, 기존 production의 모든 기동 조건을 바꾸지는 않음.
- 프록시가 검증하지 않은 forwarded header를 전역 신뢰하도록 설정하지 않음. TLS·cookie 실제 결과는 배포 후 확인.
- 개인정보처리방침의 Vercel/Railway 표기는 실제 Amplify/BE/DB 구성이 정해진 후 PD 수정 필요.

## 공식 참고

- [Java SE Procfile](https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/java-se-procfile.html)
- [nginx와 PORT 설정](https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/platforms-linux-extend.proxy.html)
- [Secrets Manager/SSM 환경 설정](https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/AWSHowTo.secrets.env-vars.html)
