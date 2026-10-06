# Coupon API

[![CI](https://github.com/jeonyongwook/coupon-api/actions/workflows/ci.yml/badge.svg)](https://github.com/jeonyongwook/coupon-api/actions/workflows/ci.yml)

고객사(B2B)의 주문을 받아 **발행처에서 쿠폰 핀(PIN)을 비동기로 발급받아 전달**하는 쿠폰 발행 API 서버입니다.
발행처는 하나가 아니라 **여러 곳**이며, 쿠폰 상품마다 발행처가 정해져 있습니다. 발행처별 연동은 `IssuerClient` 구현체로 나눕니다.

주문 접수(동기)와 쿠폰 발행(비동기)을 분리하고, 발행처 응답 지연·타임아웃·중복 요청·서버 다중 실행 같은
**실제 운영에서 문제가 되는 상황을 코드와 테스트로 다룬 것**이 이 프로젝트의 중심입니다.

## 한눈에 보기

| 운영에서 생기는 문제 | 선택한 해법 | 확인 방법 |
|---|---|---|
| 네트워크 재시도로 같은 주문이 두 번 접수됨 | `customerTrxId` 멱등 처리, 동시 요청은 DB 유니크 제약이 최종 보증하고 위반 시 새 트랜잭션에서 재조회 | `OrderServiceTest` (동시 8스레드 → 주문 1건) |
| 서버를 여러 대 띄우면 같은 건을 중복 발행 | `SELECT ... FOR UPDATE SKIP LOCKED`로 짧은 트랜잭션에서 선점(READY → PROCESSING), 발행처 호출 중에는 DB 락을 쥐지 않음 | `ClaimConcurrencyMariaDbTest` (실제 MariaDB, 4개 커넥션 동시 선점) |
| 발행처 지연·타임아웃 | 스레드가 실제로 시작한 시점부터 타임아웃 계산, 원본 future는 살려 두어 늦게 온 성공 응답도 `UNUSED`로 반영(핀 유실 방지) | `CouponIssueTimeoutTest` (가짜 발행처로 지연 응답·큐 대기 시나리오 재현) |
| 발행처마다 연동 방식이 다름 | `IssuerClient` 인터페이스 + `issuerSeq`로 구현체 선택, 상품의 발행처 상품 코드·유효일수를 요청에 반영 | `CouponIssueTimeoutTest` (발행처별 라우팅, 한 발행처 오류가 다른 발행처 건에 번지지 않음) |
| 응답 순서가 뒤바뀌어 확정된 결과가 뒤집힘 | `OrderDetail` 엔티티가 상태 전이를 스스로 가드 | `OrderDetailTest`, `CouponIssueResultWriterTest` |
| 외부 호출 대기 중 DB 커넥션 점유 | 배치 전체에 `@Transactional`을 걸지 않고 선점·결과 반영만 짧은 트랜잭션으로 분리 | 코드 구조 (`Claimer` / `ResultWriter`) |

## 기술 스택

| 구분 | 사용 기술 |
|---|---|
| Language / Framework | Java 17, Spring Boot 3.4 (Web, Data JPA, Validation) |
| DB | MariaDB 10.6+ (운영/로컬), H2 인메모리 (테스트) |
| Build / CI | Gradle, GitHub Actions |
| 문서 | springdoc-openapi (Swagger UI) |
| 테스트 | JUnit 5, AssertJ, MockMvc, Testcontainers(MariaDB) (통합 테스트 28개 + 단위 테스트 4개) |

## 전체 흐름

```mermaid
sequenceDiagram
    participant C as 고객사
    participant A as API 서버
    participant DB as MariaDB
    participant B as 발행 배치 (3초 주기)
    participant I as 발행처

    C->>A: POST /orders (X-API-KEY)
    A->>DB: 주문 + 상세(수량만큼) READY로 저장
    A-->>C: 202 Accepted (trxId)

    loop 3초마다
        B->>DB: READY 건 선점 (SKIP LOCKED) → PROCESSING
        B->>I: 핀 발행 요청 (발행처별 IssuerClient, 스레드풀 병렬, 건별 타임아웃)
        I-->>B: 핀 / 지연 / 실패
        B->>DB: UNUSED(성공) 또는 ISSUE_FAIL(실패) 반영
        B->>DB: 모두 끝난 주문 COMPLETED 전환
    end

    C->>A: GET /orders/{trxId}
    A-->>C: 진행 상황 + 발행된 핀
```

> 실제 발행처 연동은 아직 없고 가상(mock)입니다. `MockIssuerClient`가 모든 발행처를 대신해 0.2~2초의 임의 지연 뒤 핀을 만들어 줍니다.
> 발행처 연동은 `IssuerClient` 인터페이스 뒤로 분리되어 있어, 실제 어댑터를 빈으로 추가하면(`supports(issuerSeq)`로 담당 발행처 선언)
> 나머지 구조(선점·타임아웃·지연 응답 반영)는 바꾸지 않고 그대로 쓸 수 있습니다.

### 상태 전이

```mermaid
stateDiagram-v2
    direction LR
    [*] --> READY: 주문 접수
    READY --> PROCESSING: 배치가 선점
    PROCESSING --> UNUSED: 발행 성공
    PROCESSING --> ISSUE_FAIL: 실패 / 타임아웃
    ISSUE_FAIL --> UNUSED: 타임아웃 뒤 늦게 성공 응답 도착
    PROCESSING --> READY: 요청 미전송(스레드풀 포화) / 서버 장애로 멈춤
```

주문(`Order`)은 `READY → COMPLETED`이며, **모든 상세의 발행 시도가 끝나면**(성공/실패 무관) COMPLETED가 됩니다.
실패 건 수는 조회 API의 `failedCount`로 확인합니다.

## 핵심 설계 결정

### 1. 멱등한 주문 접수 (`OrderService`, `OrderRegistrar`)
네트워크 재시도로 같은 요청이 두 번 와도 주문이 두 번 만들어지면 안 됩니다.
- 같은 고객사가 같은 `customerTrxId`로 **같은 내용**을 다시 보내면 처음 접수된 `trxId`를 그대로 돌려줍니다.
- **다른 내용**이면 `E003(409)`으로 거절합니다.
- "조회 후 저장" 사이에 동일 요청이 끼어드는 경쟁 상태는 DB 유니크 제약(`customerSeq + customerTrxId`)이 최종 보증합니다.
  제약 위반이 나면 **새 트랜잭션에서 기존 주문을 다시 조회**해 멱등 응답을 줍니다.
  (같은 트랜잭션에서 예외를 잡고 계속하면 rollback-only 상태 때문에 커밋이 실패하므로 `OrderRegistrar`를 별도 빈으로 분리했습니다.)
- 동시 8개 스레드가 같은 요청을 보내도 주문은 1건만 생기는 것을 `OrderServiceTest`로 검증합니다.

### 2. 중복 발행 방지: 선점(claim) 방식 (`CouponIssueClaimer`)
처음에는 `fixedDelay`로 "이전 회차가 끝나기 전에는 다음 회차가 시작되지 않는다"는 사실에 기대고 있었는데,
서버를 2대 이상 띄우면 이 보장이 깨져 같은 건에 핀을 중복 요청하게 됩니다.
- 짧은 트랜잭션에서 `SELECT ... FOR UPDATE SKIP LOCKED`로 READY 건을 잠그고 같은 트랜잭션에서 `PROCESSING`으로 바꿔 커밋합니다.
- 커밋 이후엔 다른 인스턴스·다른 회차의 조회 조건(READY)에 걸리지 않으므로, **발행처 호출처럼 오래 걸리는 구간에는 DB 락을 쥐고 있지 않습니다.**
- 선점한 서버가 죽어 `PROCESSING`으로 멈춘 건은 `STUCK_PROCESSING_SEC`(기본 60초) 뒤에 READY로 복구합니다.

### 3. 다중 발행처 연동 구조 (`IssuerClient`, `IssuerClientRegistry`)
쿠폰 상품(`Coupon`)마다 발행처(`Issuer`)가 정해져 있고, 발행처마다 API 규격이 다릅니다.
- 배치는 선점한 상세마다 주문 → 쿠폰 상품을 따라가 **발행처, 발행처 상품 코드, 유효일수**를 한 번에 조회하고(`findIssueTargets`),
  `IssuerClientRegistry`가 `issuerSeq`를 담당하는 `IssuerClient`를 고릅니다. 구현체는 `@Order` 순서로 평가되므로, 특정 발행처 전용 어댑터가 범용 mock보다 먼저 선택됩니다.
- 쿠폰의 유효기간은 상품에 정의된 `validDays`로 계산합니다.
- 발행처 클라이언트를 찾지 못하거나 호출이 예외를 던지면 **그 건만** `ISSUE_FAIL`이 되고, 같은 회차의 다른 발행처 건에는 영향을 주지 않습니다.
- 현재 구현체는 `MockIssuerClient` 하나입니다. 지연 범위는 `app.issuer.mock.min-latency-ms` / `max-latency-ms`로 조절합니다.

### 4. 발행처 지연 대비: 타임아웃 + 지연 응답 반영 (`CouponIssueBatch`)
- 건별 타임아웃은 "큐에 제출한 시점"이 아니라 **스레드가 실제로 처리를 시작한 시점**부터 계산합니다.
  (제출 시점 기준이면 큐 대기가 긴 건들이 한꺼번에 타임아웃 나는 문제가 생깁니다.)
- `orTimeout()`은 원본 future를 실패로 완료시켜 버려 이후 응답을 받을 방법이 없어지므로,
  원본은 살려 두고 **별도 stage에만** 타임아웃을 겁니다. 타임아웃으로 `ISSUE_FAIL` 처리된 뒤 발행처 응답이 뒤늦게 성공으로 오면 자동으로 `UNUSED`로 반영해 **발급된 핀이 유실되지 않게** 합니다.
- 스레드풀이 포화되어 요청을 아예 보내지 못한 건은 실패가 아니라 READY로 되돌려 다음 회차에 재시도합니다.

### 5. 상태 전이 가드 (`OrderDetail`)
순서가 뒤바뀌어 도착해도 확정된 결과를 뒤집지 않도록 **엔티티의 전이 메서드가 스스로 가드**합니다(허용되지 않는 전이면 상태를 바꾸지 않고 `false`).
이미 `UNUSED`인 건은 실패로 덮어쓰지 않고, 성공이 중복 반영돼도 핀이 바뀌지 않습니다.
규칙이 서비스 코드의 if문이 아니라 도메인 객체에 있어, 스프링·DB 없이 `OrderDetailTest`로 검증할 수 있습니다.
`CouponIssueResultWriter`는 전이 결과에 따라 로그를 남기고 짧은 트랜잭션으로 저장하는 역할만 합니다.

### 6. 트랜잭션 경계
배치 메서드 전체에 `@Transactional`을 걸지 않습니다. 외부 호출을 기다리는 동안 DB 커넥션을 점유하게 되기 때문입니다.
트랜잭션은 선점(`Claimer`)과 결과 반영(`ResultWriter`)에서만 짧게 쓰며, 같은 클래스 내부 호출은 프록시를 우회하므로 별도 빈으로 분리했습니다.
한 건의 반영 실패가 나머지 건의 반영을 막지 않도록 건별로 예외를 흡수합니다.

### 7. 대량 INSERT 성능
주문 1건에 최대 1,000개의 상세가 생성됩니다. `IDENTITY` 채번은 Hibernate의 insert 배치를 막으므로 `OrderDetail`은
`SEQUENCE(allocationSize=50)`을 쓰고, `hibernate.jdbc.batch_size=50`, `rewriteBatchedStatements=true`를 함께 설정했습니다.
주문 1건(상세 1,000건) 접수 시간을 로컬 Docker의 MariaDB 10.11에서 측정했습니다
(`./gradlew benchmark`, 워밍업 2회 후 5회 측정).

| 설정 | 중앙값 | 최소~최대 |
|---|---|---|
| JDBC 배치 OFF | 539ms | 489~584ms |
| JDBC 배치 ON (`batch_size=50`, `order_inserts`, `rewriteBatchedStatements=true`) | 90ms | 59~123ms |

배치 설정으로 약 6배 빨라졌습니다. 측정 시간에는 인증·상품 조회가 함께 포함되어 있고 로컬 환경의 값이므로,
절대 수치보다 설정 전후의 상대 비교로 봐 주세요. (비교 대상은 IDENTITY와 SEQUENCE가 아니라 JDBC 배치 설정 ON/OFF입니다.)

### 8. 보안
- 고객 식별자(`customerKey`)는 본문에 평문으로 오가므로, 별도 시크릿(`X-API-KEY`)을 함께 검증합니다.
- 시크릿은 **SHA-256 해시로만 저장**하고 상수 시간 비교(`MessageDigest.isEqual`)로 검증합니다.
- 존재하지 않는 고객사와 틀린 키를 **같은 401(E010)** 으로 응답해 유효한 고객키를 추측할 수 없게 했습니다.
- 다른 고객사의 주문 조회는 "존재하지 않음"과 같은 404로 응답합니다.
- 단일 SHA-256 해시는 **서버가 발급하는 충분히 긴 랜덤 키**를 전제로 한 선택입니다. 키 자체의 엔트로피가 높아 사전 대입 공격 부담이 낮고,
  API 요청마다 검증하므로 bcrypt 같은 느린 해시는 오히려 부담이 됩니다. 사람이 정하는 비밀번호에는 맞지 않는 방식입니다.
- 기본 설정은 안전한 쪽입니다. 데모 고객사 시드와 DB 기본 접속 정보, 테이블 자동 생성은 `local` 프로파일에서만 켜지고,
  그 외에는 DB 접속 환경변수가 없으면 기동하지 않으며 스키마는 검증만 합니다.

### 의도적으로 두지 않은 것: 발행처 간 격리
발행 스레드풀은 모든 발행처가 공유합니다. 한 발행처가 느려지면 그 건들이 스레드를 오래 잡아 다른 발행처 건의 처리도 늦어질 수 있습니다.
발행처별 스레드풀 분리, 서킷 브레이커, 발행처 단위 발행 중지 플래그(issuer 테이블)는 **운영자가 개입해 발행처를 제어하는 환경을 전제로 하지 않아 만들지 않았습니다.**
이 영향은 `ISSUE_TIMEOUT_SEC`(느린 건을 일정 시간 뒤 끊음)와 스레드풀 크기로 줄입니다.

## API

Swagger UI: `http://localhost:8087/swagger-ui.html`

### 주문 접수 — `POST /api/v1/orders`

| 항목 | 설명 |
|---|---|
| Header | `X-API-KEY`: 고객사 시크릿 키 |
| Body | `customerKey`(≤20), `customerTrxId`(≤30, 고객사별 유니크), `customerGoodsCode`(≤30), `quantity`(1~1000), `msgSubject`/`msgAddContent`(선택, ≤100) |
| 성공 | `202 Accepted` |

```bash
curl -i -X POST http://localhost:8087/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: demo-secret-key-1234" \
  -d '{"customerKey":"DEMO_CUSTOMER","customerTrxId":"ORDER-0001","customerGoodsCode":"GOODS001","quantity":3}'
```
```json
{ "resCode": "0000", "resMsg": "SUCCESS", "trxId": "TX-3F9A..." }
```

### 주문 조회 — `GET /api/v1/orders/{trxId}?customerKey=...`

```bash
curl -i "http://localhost:8087/api/v1/orders/TX-3F9A...?customerKey=DEMO_CUSTOMER" \
  -H "X-API-KEY: demo-secret-key-1234"
```
```json
{
  "resCode": "0000", "resMsg": "SUCCESS",
  "trxId": "TX-3F9A...", "customerTrxId": "ORDER-0001",
  "status": "COMPLETED", "quantity": 3,
  "issuedCount": 3, "failedCount": 0, "pendingCount": 0,
  "items": [
    { "orderDetailSeq": 1, "status": "UNUSED", "pin": "PIN-1A2B3C4D5E6F",
      "validStartDate": "2026-10-06", "validEndDate": "2026-11-05" }
  ]
}
```

### 에러 코드

| 코드 | HTTP | 의미 |
|---|---|---|
| E001 | 400 | 요청 값 오류 (유효성 검증 실패, 깨진 JSON) |
| E002 | 400 | 존재하지 않거나 판매 중지된 상품 코드 |
| E003 | 409 | 같은 `customerTrxId`로 **다른 내용**의 주문 |
| E004 | 403 | 정지/삭제된 고객사 |
| E005 | 404 | 주문 없음 (타 고객사 주문 포함) |
| E009 | 409 | 그 밖의 데이터 충돌 |
| E010 | 401 | API 키 누락/불일치, 존재하지 않는 고객사 |
| E999 | 500 | 서버 오류 |

## 실행 방법

필요한 것: JDK 17, Docker

```bash
# 1. DB 실행 (MariaDB 10.11)
docker compose up -d

# 2. 서버 실행 (local 프로파일로 실행되어 테이블과 시드 데이터가 자동 생성됩니다)
./gradlew bootRun        # Windows: gradlew.bat bootRun
```

`./gradlew bootRun`은 `SPRING_PROFILES_ACTIVE`를 따로 지정하지 않으면 `local` 프로파일을 씁니다
(기본 DB 접속 정보 `root/system`, `ddl-auto=update`, 시드 데이터 on).

시드 데이터(local 전용): 고객사 `DEMO_CUSTOMER` / 키 `demo-secret-key-1234`,
상품 `GOODS001`(발행처 A, 유효 30일)과 `GOODS002`(발행처 B, 유효 7일).

local이 아닌 환경에서는 기본값이 없으므로 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 반드시 지정해야 합니다.
`DB_URL`에는 대량 INSERT 배치 효과를 위해 `rewriteBatchedStatements=true`를 포함해 주세요.
스키마는 `DDL_AUTO=validate`(기본)로 검증만 하므로, 운영 DB의 스키마 생성은 별도 마이그레이션이 필요합니다(아직 미구현).

### 배치 설정 (`system_config` 테이블, 그룹 `BATCH_COUPON_ISSUE`)

| 키 | 기본값 | 설명 |
|---|---|---|
| `ISSUE_TRY_COUNT` | 10 | 배치 1회차에 선점할 최대 건수 |
| `ISSUE_TIMEOUT_SEC` | 5 | 발행처 응답 대기 타임아웃(초) |
| `CORE_POOL_SIZE` / `MAX_POOL_SIZE` | 5 / 10 | 발행 스레드풀 크기 (1분마다 재반영, 재시작 불필요) |
| `STUCK_PROCESSING_SEC` | 60 | PROCESSING으로 멈춘 건을 READY로 복구하는 기준 |

> `MAX_POOL_SIZE`는 `ThreadPoolExecutor` 특성상 작업 큐(100)가 가득 찬 뒤에야 쓰입니다.
> 배치가 회차당 `ISSUE_TRY_COUNT`건만 제출하므로 평소 동시 처리량은 `CORE_POOL_SIZE`입니다.

## 테스트

```bash
./gradlew test
```

기본 테스트는 운영 DB 없이 인메모리 H2(MariaDB 호환 모드)에서 실행되며, 스케줄러는 꺼 두고 테스트가 배치를 직접 호출해 결정적으로 검증합니다.
발행처는 `ScriptedIssuerClient`(테스트용)로 바꿔, 발행처별로 지연·실패·핀 값을 테스트가 직접 정합니다.
`SKIP LOCKED` 동작만은 H2로 MariaDB와 같다고 보장할 수 없어, 이 부분은 Testcontainers의 실제 MariaDB로 따로 검증합니다
(Docker가 없으면 해당 테스트만 건너뜁니다. GitHub Actions의 ubuntu 러너에서는 Docker가 기본 제공되어 CI에서 함께 실행됩니다).

| 테스트 | 검증 내용 |
|---|---|
| `OrderDetailTest` | 상태 전이 규칙(단위 테스트): 선점은 READY만, 실패 건에 늦게 온 성공은 승급, 성공 건은 실패로 덮어쓰지 않음, 중복 성공은 핀 유지 |
| `OrderServiceTest` | 주문 생성, 멱등 재요청, 내용이 다른 중복 거절, 고객사별 거래번호 분리, **동시 요청 시 주문 1건**, 인증/상태/상품 오류 |
| `CouponIssueResultWriterTest` | DB 반영 쪽 검증: 지연 성공의 승급, 성공 건 미덮어쓰기, 주문 완료 전환 조건·멱등, 대기 복귀, 멈춘 건 복구 |
| `CouponIssueTimeoutTest` | **실제 배치 + 테스트용 가짜 발행처**로 검증: 타임아웃 뒤 늦게 온 성공이 `UNUSED`로 반영됨, 큐 대기 시간은 타임아웃에 포함되지 않음(스레드 1개로 직렬 처리해도 전부 성공), 발행처별 라우팅과 쿠폰 유효일수 반영, 한 발행처 오류가 다른 발행처 건에 번지지 않음 |
| `CouponIssueBatchTest` | 배치 1회 실행으로 전건 발행·주문 완료, 선점 중복 방지, 선점 개수 제한 |
| `OrderApiTest` | 상태 코드(202/400/401/404), 깨진 JSON, 타 고객사 주문 조회 차단 |
| `ClaimConcurrencyMariaDbTest` | **실제 MariaDB**에서 여러 커넥션이 동시에 선점해도 중복·유실 없이 전 건이 한 번씩만 선점됨 (`SKIP LOCKED`) |

성능 측정(`OrderInsertBatchOn/OffBenchmark`)은 `@Tag("benchmark")`로 기본 `test`와 CI에서 제외되며, `./gradlew benchmark`로만 실행됩니다.

## 프로젝트 구조

```
src/main/java/com
├── Main.java
├── common
│   ├── config      # 스레드풀, 스케줄러 on/off, 시드 데이터, system_config 조회
│   ├── entity      # system_config
│   ├── exception   # ErrorCode, CouponApiException, 전역 예외 처리
│   └── security    # API 키 해시/비교
└── couponapi
    ├── controller  # REST API
    ├── service     # 인증, 주문 접수(멱등), 주문 저장(트랜잭션), 주문 조회
    ├── batch       # 선점, 발행(병렬/타임아웃), 결과 반영
    ├── issuer      # 발행처 연동: IssuerClient, 발행처별 선택(Registry), 가상 발행처(Mock)
    ├── entity      # Order, OrderDetail, Customer, Coupon, Issuer, 상태 enum
    ├── repository
    └── dto         # 요청/응답, 발행 대상(IssueTarget)
```

`Main`은 루트 패키지(`com`)에 두어 공통 모듈(`com.common`)과 도메인 모듈(`com.couponapi`)을 형제 패키지로 함께 컴포넌트 스캔합니다.
공통 설정·예외·보안은 `common`, 쿠폰 주문 도메인은 `couponapi`로 나눠 두었습니다.

## 한계와 다음 단계

- **발행처 연동은 mock**입니다. 실제 발행처 어댑터를 `IssuerClient` 구현체로 추가하고, 발행처 조회 API로 `ISSUE_FAIL` 건의 실제 발급 여부를 대조(reconciliation)하는 단계가 필요합니다.
- **망취소(발행 취소)는 구현하지 않았습니다.** 오류·타임아웃은 `ISSUE_FAIL`로만 처리하고 발행처에 취소 요청을 보내지 않습니다.
  타임아웃 뒤 늦게 온 성공 응답은 `UNUSED`로 반영하지만 이는 서버 프로세스 메모리 안의 콜백에 의존하므로,
  그 사이 서버가 재시작되면 발행처에는 핀이 발급됐는데 이쪽은 `ISSUE_FAIL`로 남을 수 있습니다. 보완 계획은 다음과 같습니다.
  - 요청마다 `orderDetailSeq` 기반 멱등 키를 발행처에 함께 보내, 응답을 못 받아도 조회·취소 대상을 특정합니다.
  - 결과를 알 수 없는 건(타임아웃)만 별도 상태(예: `CANCEL_REQUESTED`)로 두고 취소를 요청·재시도합니다. 취소 API가 없는 발행처는 조회 API로 대조해 성공이면 `UNUSED`로 반영합니다.
    발행처마다 취소·조회 API 제공 여부가 다르므로 이 능력도 어댑터별로 선언하게 할 계획입니다.
- **발행 처리량은 측정하지 않았습니다.** 기본 설정(회차당 10건, 스레드 5개, 3초 간격)과 mock 지연(평균 약 1.1초)으로 단순 추정하면
  한 회차가 5~6초에 10건, 즉 초당 2건 안팎이라 **1,000매 주문이 끝나기까지 8~10분** 걸립니다(추정치). 필요하면 `ISSUE_TRY_COUNT`와 스레드풀 크기를 키우고 실제 처리량을 측정해야 합니다.
- **발행처 간 격리가 없습니다.** 느린 발행처가 공유 스레드풀을 점유하면 다른 발행처 건도 늦어질 수 있습니다(위 "의도적으로 두지 않은 것" 참고).
- `ISSUE_FAIL` 건의 **자동 재시도 정책**이 없습니다 (재시도 횟수/간격, 최종 실패 알림).
- **PIN은 평문으로 저장되고 조회 응답에 그대로 나갑니다.** 운영에서는 암호화 저장과 접근 감사가 필요합니다.
- 스키마 마이그레이션 도구(Flyway/Liquibase)가 없습니다. `local`에서만 `ddl-auto=update`로 테이블을 만들고, 그 외에는 검증만 합니다.
- 연관 관계를 FK 객체 매핑 대신 ID 값으로 들고 있어 DB 레벨 FK 제약이 없습니다. (대량 배치 처리 시 의도적으로 단순화했으나, 운영에서는 제약 추가를 검토해야 합니다.)
- 요청 속도 제한(rate limit), 고객사 API 키 발급·재발급 API는 범위 밖입니다.
