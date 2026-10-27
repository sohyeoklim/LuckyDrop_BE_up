# LuckyDrop_up

LuckyDrop_up은 기존 LuckyDrop의 드로우 서비스를 바탕으로 실시간 상태 전달, 추첨 검증, 당첨 이후 처리와 운영 안정성을 확장한 프로젝트입니다. 이 저장소는 백엔드이며, [프런트엔드 저장소](https://github.com/sohyeoklim/LuckyDrop_FE_up)와 함께 사용합니다.

> 백엔드 기능은 `feat/mission`, API 회귀 테스트와 CI는 이를 기반으로 한 `codex/api-test-ci` 브랜치의 구현을 기준으로 작성했습니다. 프런트엔드의 확장 화면은 현재 로컬 작업 트리에 반영되어 있으며, 원격 저장소의 브랜치에 아직 반영되지 않았을 수 있습니다.

## 기존 LuckyDrop

[기존 LuckyDrop 백엔드](https://github.com/Stupid-LYL/Luckydrop_BE)와 [프런트엔드](https://github.com/Stupid-LYL/Luckydrop_FE)는 사용자가 티켓으로 상품 드로우에 응모하고 추첨 결과를 확인하는 서비스입니다. 회원가입과 로그인, 출석·미션을 통한 티켓 획득, 드로우 생성과 응모, 북마크, 당첨자 조회 및 관리 기능을 갖추고 있습니다.

LuckyDrop_up은 이 구현을 이어받아 **응모 이후의 경험과 여러 서버에서의 운영, 추첨 정보의 투명성, 목록 조회 방식**을 발전시키는 데 집중했습니다.

## 새롭게 발전시킨 내용

### 실시간 드로우 상태와 알림

- 드로우의 시작·추첨·종료 상태 변경을 SSE(Server-Sent Events)로 전달합니다. 상태 변경 이벤트는 Redis Pub/Sub을 거쳐 각 서버 인스턴스의 SSE 연결로 전달됩니다.
- 추첨 시작, 당첨·미당첨, 당첨 처리 상태 변경 알림을 저장하고 사용자별 SSE로 전달합니다. 알림 목록과 읽음 상태도 조회할 수 있습니다.
- 상태 변경과 자동 추첨 스케줄러에 Redis 기반 ShedLock을 적용해 여러 서버가 같은 작업을 중복 실행하는 상황을 줄였습니다. 상태 변경과 알림은 트랜잭션 커밋 이후 전송하도록 처리합니다.
- **현재 범위:** 사용자 알림 SSE는 알림을 생성한 서버의 연결에 직접 전달됩니다. Redis Pub/Sub을 통한 서버 간 전파는 드로우 상태 이벤트에 적용되어 있으므로, 다중 서버에서 사용자 알림의 실시간 전달을 보장하려면 추가 작업이 필요합니다. 저장된 알림은 목록 API로 조회할 수 있습니다.

### 추첨 검증 정보

- 드로우 생성 시 무작위 서버 시드와 SHA-256 해시를 생성해 해시를 사전에 고정합니다. 추첨이 끝나면 상세 응답에 시드, 참여자 스냅샷 해시, 추첨 시각과 알고리즘 버전을 공개합니다.
- `WEIGHTED_HMAC_SHA256_V1` 알고리즘은 드로우 ID와 사용자 ID로 HMAC 값을 만들고, 응모 횟수에 따른 가중치를 적용해 중복 없이 당첨자를 선정합니다.
- 기존 드로우처럼 생성 시점의 커밋이 없는 경우는 `LEGACY`로 구분합니다. 현재 응답은 참여자 스냅샷의 **해시**를 제공하며, 전체 참여자와 응모 횟수 목록을 공개하지는 않으므로 공개 정보만으로 당첨자 선정 전체를 독립적으로 재현할 수 있는 것은 아닙니다.

### 당첨 후 배송 처리

- 배송 대상 당첨자는 기존 프로필의 연락처와 주소를 사용하거나, 누락된 경우 추첨 후 3일 이내 배송 정보를 제출할 수 있습니다. 기한이 지나면 제출 대기 상태를 만료 처리합니다.
- 호스트는 자신이 생성한 드로우의 당첨자 정보를 조회하고, 배송 정보가 제출된 당첨자의 처리 상태·메모·송장번호를 갱신할 수 있습니다. 변경 내용은 당첨자 알림과 연결됩니다.

### 집계 기반 드로우 목록

- `draw_metrics`에 드로우별 북마크 수, 참여자 수, 응모 수를 유지하고, 북마크·참여자 수 정렬과 핫 배너 선정에 활용합니다.
- 북마크와 응모 처리 시 MySQL의 원자적 증감 쿼리로 집계값을 갱신합니다. 목록의 카드 정보는 필요한 데이터를 모아서 조회합니다.
- 기존 데이터에 대한 집계 테이블 생성과 초기 집계 SQL, 목록 조회를 비교하는 k6 스크립트를 포함합니다. 저장된 결과만으로 일반적인 성능 개선 수치를 단정하지 않습니다.

### 계정과 미션 보상

- Google OAuth 로그인을 추가했습니다. 이미 같은 이메일로 가입한 계정은 기존 방식으로 로그인한 뒤 Google 계정을 직접 연결하며, 새 Google 사용자는 추가 프로필 입력을 진행합니다.
- 미션 목록에서 서버에 저장된 진행 상황과 완료 여부를 조회합니다. 첫 응모, 일일 응모, 첫 북마크, 프로필 완성 및 초대 조건 달성에 따라 서버가 티켓 보상을 처리합니다. 출석 체크와 연속 출석 보상도 미션 상태와 연결됩니다.
- 미션의 사용자·미션·기간별 기록과 티켓 원장의 멱등성 키를 사용해 반복 지급을 방지하도록 구성했습니다. 광고 미션은 목록에 표시되지만, 현재 외부 광고 시청 검증과 보상 지급 API가 연결된 상태는 아닙니다.
- 티켓 잔액과 내역은 인증된 사용자 기준으로 조회하고, 관리자 티켓 조정과 드로우 생성·추첨에 역할별 접근 제한을 적용했습니다.

### 프런트엔드 연동 작업

현재 로컬 프런트엔드 작업 트리에는 알림 UI, 드로우 상태 이벤트 수신, 미션 조회, Google 로그인·온보딩, 당첨 배송 화면 및 목록 무한 스크롤 개선이 포함되어 있습니다. 이 README의 백엔드 기능과 프런트엔드 원격 브랜치의 반영 여부는 각각 확인해 주세요.

## 구성

| 영역 | 기술 |
| --- | --- |
| 백엔드 | Java 21, Spring Boot 3.5.9, Gradle, Spring Data JPA |
| 인증 | Spring Security, JWT, Google OAuth 2.0 / OpenID Connect |
| 데이터와 운영 | MySQL, Redis Pub/Sub, ShedLock |
| API 문서와 부하 테스트 | springdoc OpenAPI, k6 |
| API 회귀 테스트와 CI | Playwright, GitHub Actions, Docker Compose |
| 프런트엔드 | React, TypeScript, Vite, TanStack Query |

```text
React 클라이언트
    ├── HTTP API / SSE ──> Spring Boot
    │                          ├── MySQL: 회원, 드로우, 티켓, 미션, 알림
    │                          └── Redis: 상태 이벤트 Pub/Sub, 스케줄러 잠금, 인증 정보
    └── Google 로그인 ───────> Spring Security OAuth 2.0
```

## 로컬 실행

### 1. 준비 사항

- JDK 21, MySQL, Redis
- Google OAuth 클라이언트 ID와 시크릿. OAuth 로그인 설정이 있어야 현재 보안 설정을 포함한 서버를 시작할 수 있습니다.
- 이메일 인증을 사용할 SMTP 계정 정보
- 프런트엔드 실행 시 Node.js와 npm

MySQL에 빈 데이터베이스를 준비하고, Google OAuth 클라이언트의 승인된 리디렉션 URI에 `http://localhost:8080/login/oauth2/code/google`을 등록합니다. 로컬 프런트엔드 기본 주소는 `http://localhost:5173`입니다.

### 2. 백엔드 설정

기본 프로필은 `dev`입니다. `src/main/resources/application-dev.yml`은 Git에서 제외되어 있으므로 직접 생성합니다. 아래는 로컬 설정 예시이며 환경에 맞게 변경합니다.

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/luckydrop?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  data:
    redis:
      host: localhost
      port: 6379
  mail:
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: update
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope: openid,profile,email

file:
  upload-dir: ./uploads

app:
  frontend-url: http://localhost:5173
```

`DB_USERNAME`, `DB_PASSWORD`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `JWT_SECRET`을 실행 환경에 설정합니다. `JWT_SECRET`은 HS256에 사용할 충분히 긴 무작위 문자열을 사용하고, 설정 파일이나 비밀값은 저장소에 커밋하지 않습니다. 메일 전송 설정은 현재 Gmail SMTP를 사용합니다.

```powershell
# Windows PowerShell, 백엔드 저장소 루트
.\gradlew.bat bootRun
```

macOS/Linux에서는 `./gradlew bootRun`을 사용합니다. 기본 포트는 `8080`이며 실행 후 [Swagger UI](http://localhost:8080/swagger-ui.html)에서 API를 확인할 수 있습니다.

### 3. 프런트엔드 실행

[프런트엔드 저장소](https://github.com/sohyeoklim/LuckyDrop_FE_up)를 별도로 준비한 뒤 해당 디렉터리에서 실행합니다.

```bash
npm install
npm run dev
```

현재 로컬 프런트엔드의 일반 API 클라이언트는 `http://localhost:8080`을 사용하며, 일부 SSE 및 Google 로그인 경로는 `VITE_API_BASE_URL`도 참조합니다. 백엔드 CORS 허용 출처는 현재 `http://localhost:5173`으로 설정되어 있습니다. 다른 주소에서 실행할 때는 관련 설정을 함께 확인해야 합니다.

### 기존 데이터베이스의 집계 초기화

기존 LuckyDrop 데이터베이스를 업그레이드할 때는 집계 기반 조회로 전환하기 전에 [`V001__create_draw_metrics.sql`](src/main/resources/db/migration/V001__create_draw_metrics.sql)을 대상 MySQL 데이터베이스에 적용해 기존 북마크·응모 데이터를 집계합니다. 이 프로젝트에는 Flyway 실행 의존성이 없으므로 해당 SQL이 자동 적용된다고 가정하지 마세요. 실제 적용 전 대상 데이터베이스와 스키마를 확인하고 백업합니다.

## 주요 API

상세 요청·응답과 나머지 API는 Swagger UI에서 확인할 수 있습니다. 인증이 필요한 API는 로그인 후 JWT를 사용합니다.

| 기능 | 메서드와 경로 |
| --- | --- |
| 드로우 목록 / 상세 | `GET /api/draws`, `GET /api/draws/{id}` |
| 응모 | `POST /api/draws/{drawId}/entry` (`Idempotency-Key` 헤더 필요) |
| 드로우 상태 SSE | `GET /api/draws/events` |
| 알림 목록 / SSE | `GET /api/notifications`, `GET /api/notifications/stream` |
| 알림 읽음 처리 | `PATCH /api/notifications/{notificationId}/read`, `PATCH /api/notifications/read-all` |
| 내 미션 / 출석 | `GET /api/missions`, `POST /api/missions/attendance/check-in` |
| 내 티켓 잔액 / 내역 | `GET /api/ticket/wallet/me`, `GET /api/ticket/ledger/me` |
| 당첨자 배송 정보 | `GET /api/draws/{drawId}/my-delivery`, `POST /api/draws/{drawId}/winners/{winnerId}/delivery-address` |
| 호스트 당첨 처리 | `GET /api/host/draws/{drawId}/winners`, `PATCH /api/host/draws/{drawId}/winners/{winnerId}/fulfillment` |
| Google 계정 연결 / 추가 정보 | `POST /api/user/google/link`, `POST /api/user/onboarding` |

추첨 검증 정보는 별도 API가 아닌 드로우 상세 응답의 `verification` 필드에 포함됩니다.

## 테스트 및 부하 측정

### API 회귀 테스트 자동화

수동으로 확인하던 응모·티켓 기능을 Playwright API 테스트로 작성하고, GitHub Actions에서 코드 변경 시 반복 검증하도록 구성했습니다. HTTP 응답뿐 아니라 잔액, 응모 내역, 티켓 원장을 함께 조회해 정상 처리와 실패 시 데이터 일관성을 확인합니다.

| 케이스 | 입력 및 조건 | 검증 내용 |
| --- | --- | --- |
| 정상 응모 | 잔액 10장, 회당 비용 2장, 3회 응모 | HTTP 200, 잔액 4장, 응모 3회, 사용 원장 -6장 1건 |
| 티켓 부족 | 잔액 10장, 회당 비용 2장, 6회 응모 | HTTP 400 및 티켓 부족 메시지, 요청 전후 잔액·응모 내역·원장 유지 |
| 중복 요청 | 같은 드로우에 동일한 `Idempotency-Key`와 본문으로 순차 재전송 | 첫 요청 200, 재요청 400 및 중복 메시지, 추가 차감·응모·원장 기록 없음 |

각 테스트는 전용 DB에 새 사용자·호스트·드로우를 준비하고 실제 로그인 API의 쿠키를 사용합니다. 첫 응모 및 당일 응모 미션은 이미 보상을 받은 상태로 구성해 응모 비용 검증에 보상이 섞이지 않도록 했습니다. 사전 데이터 구성은 회원가입이나 보상 지급 기능 자체의 검증을 의미하지 않습니다.

중복 요청 테스트에서는 상태 코드와 오류 메시지를 함께 확인합니다. 첫 응모 후 잔액도 부족해지므로, HTTP 400만 확인하면 티켓 부족 오류를 중복 방지 성공으로 오인할 수 있기 때문입니다.

### GitHub Actions CI

워크플로우가 포함된 브랜치의 push와 PR 생성·업데이트를 계기로 GitHub의 임시 실행 환경에서 테스트합니다. 로컬 백엔드 실행 여부와 무관하게 CI가 자체 서버와 DB를 준비합니다.

```text
push / pull request
  → Java 21·Node.js 24 및 테스트 의존성 준비
  → Docker Compose로 전용 MySQL·Redis 실행 및 상태 확인
  → Spring Boot 실행 JAR 빌드
  → api-test 프로필로 서버 실행 및 HTTP 응답 대기
  → Playwright API 테스트 3개 실행
  → 성공·실패 리포트와 서버·컨테이너 로그 저장
  → 테스트 환경 정리
```

- 서버와 DB 시간대를 `Asia/Seoul`로 맞춰 일일 미션의 날짜 기준을 통일합니다.
- CI 실행마다 새 DB를 준비하고, 테스트마다 새 데이터를 생성해 이전 실행 결과에 의존하지 않습니다.
- 테스트 실패는 CI 실패로 반영되며, HTML 리포트와 로그는 실패 시에도 Artifacts에 7일간 보관합니다.
- 테스트 전용 설정을 사용하므로 실제 서비스의 DB·메일·OAuth 비밀값은 필요하지 않습니다.
- 현재 CI는 API 테스트를 실행합니다. UI, 동시 요청 경쟁 조건, 외부 인증·메일 연동 및 별도 Gradle JUnit 테스트 실행은 포함하지 않습니다.

설정 및 코드: [워크플로우](.github/workflows/api-tests.yml) · [API 테스트](api-tests/tests/entry.spec.js) · [테스트 데이터 준비](api-tests/helpers/seed.js) · [실행·학습 안내](api-tests/README.md)

### CI 실패 감지 실습

정상 응모의 예상 잔액을 의도적으로 `4`에서 `5`로 변경해 push하고, GitHub Actions에서 `Expected: 5 / Received: 4`, `1 failed / 2 passed`와 종료 코드 1을 확인했습니다. 이는 서비스 결함 발견 사례가 아니라, 잘못된 예상값을 테스트가 감지하고 CI 실패로 전달하는지 검증한 실습입니다.

| 단계 | 실행 기록 |
| --- | --- |
| 최초 자동 실행 성공 | [테스트 3개 통과](https://github.com/sohyeoklim/LuckyDrop_BE_up/actions/runs/40472120724) |
| 의도적인 예상값 변경 | [잔액 불일치 감지 및 CI 실패](https://github.com/sohyeoklim/LuckyDrop_BE_up/actions/runs/40473462051) |
| 예상값 4로 복구 | [테스트 3개 재통과 및 CI 정상 복구](https://github.com/sohyeoklim/LuckyDrop_BE_up/actions/runs/40473903386) |

### 로컬 API 테스트 실행

JDK 21, Node.js 24, 실행 중인 Docker Desktop이 필요합니다. 저장소 루트에서 다음 명령으로 테스트 환경을 시작합니다.

```powershell
docker compose -f compose.api-test.yml up -d --wait
.\gradlew.bat bootRun --args="--spring.profiles.active=api-test"
```

서버가 준비되면 새 터미널에서 실행합니다.

```powershell
cd api-tests
npm.cmd ci
npm.cmd test
npm.cmd run report
```

테스트 서버는 `18080`, MySQL은 `13306`, Redis는 `16379` 포트를 사용합니다. API 테스트이므로 브라우저 바이너리 설치는 필요하지 않습니다. 로컬에서는 조사할 수 있도록 테스트 데이터를 남기며, 재실행 시 새로운 데이터를 생성합니다. 일일 미션 조건 때문에 서버 시간대를 `Asia/Seoul`로 맞춰야 하며, 자정을 가로지르는 실행에는 날짜 경계 처리가 추가로 필요합니다.

### Gradle 테스트

```powershell
.\gradlew.bat test
```

현재 테스트에는 Spring 애플리케이션 컨텍스트 로딩 검사가 포함되어 있으므로 실행 환경의 데이터베이스, Redis와 애플리케이션 설정이 필요할 수 있습니다.

### k6 부하 측정

목록 조회의 북마크 정렬 경로는 k6 설치 후 다음처럼 측정할 수 있습니다. 비교할 때는 같은 데이터와 부하 조건을 사용합니다.

```powershell
k6 run -e BASE_URL=http://localhost:8080 -e SORT=BOOKMARK k6/bookmark-list-comparison.js
```

`VUS`, `DURATION`, `PAGE_SIZE`, `PAGE_COUNT`를 환경 변수로 조절할 수 있으며, 인증이 필요한 실험에서는 `AUTH_TOKEN`을 전달합니다.
