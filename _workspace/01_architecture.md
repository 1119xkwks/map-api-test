# 아키텍처 설계 문서

## 프로젝트 개요
- **프로젝트명**: 카카오맵 아파트 실거래가 지도
- **설명**: 카카오맵 위에 현재 보이는 영역의 아파트 매매 실거래가를 마커로 표시하는 단일 페이지 웹앱
- **타깃 사용자**: 아파트 매매 시세를 지도 기반으로 확인하려는 일반 사용자
- **프로젝트 규모**: 소규모 (MVP)

---

## 기능 요구사항

| # | 기능 | 설명 | 우선순위 |
|---|------|------|---------|
| FR-1 | 카카오맵 표시 | Kakao Maps JS SDK로 전체 화면 지도 렌더링 | P0 |
| FR-2 | 실거래가 마커 표시 | 지도 영역 내 아파트 실거래가를 마커(아파트명 + 최근 거래가)로 표시 | P0 |
| FR-3 | 줌 레벨 제한 | 200m 축척 이상 확대 시에만 조회, 그 외에는 안내 메시지 표시 | P0 |
| FR-4 | 자동 갱신 | 지도 이동/확대/축소 시 기존 마커 제거 후 새 데이터 조회 | P0 |
| FR-5 | API 호출 제어 | 지도 이벤트에 debounce 적용하여 과도한 API 호출 방지 | P0 |

## 비기능 요구사항

| # | 항목 | 요구사항 |
|---|------|---------|
| NFR-1 | 성능 | 지도 이동 후 데이터 표시까지 2초 이내 (캐시 히트 시) |
| NFR-2 | API 부하 | 공공API 반복 호출 최소화 (같은 지역+월 데이터는 DB 캐시) |
| NFR-3 | UX | 로딩 중 스피너 표시, 에러 시 사용자 친화적 메시지 |

---

## 기술 스택

| 구분 | 기술 | 선택 근거 |
|------|------|----------|
| Frontend | Next.js 14 (App Router) | React 기반 SSR/CSR 지원, App Router로 라우팅 단순화 |
| 지도 SDK | Kakao Maps JavaScript SDK v3 | 국내 지도 서비스 중 가장 정확한 한국 주소 체계 지원 |
| Backend | Spring Boot 3.x (Java 17+) | 엔터프라이즈 수준 REST API, 모놀리식 구조에 적합 |
| 빌드 도구 | Gradle (Groovy DSL) | Maven 대비 빌드 속도 우수, 간결한 의존성 선언 |
| DB 연동 | MyBatis 3 | SQL 직접 작성으로 복잡한 조건 쿼리 용이, XML Mapper로 SQL 관리 |
| DB | PostgreSQL 16 (Neon) | 기존 dong_code/region_code 테이블 활용, 캐시 테이블 추가 |
| XML 파싱 | JAXB / Jackson XML | Spring Boot 내장 XML 파싱, 공공API XML 응답 처리 |
| HTTP 클라이언트 | RestTemplate 또는 WebClient | Spring 내장, 외부 API(공공데이터, Kakao) 호출 |
| 좌표 변환 | Kakao REST API (주소 검색) | 법정동+지번 주소를 위경도 좌표로 변환 |
| 좌표→행정구역 | Kakao REST API (좌표→행정구역) | 지도 bounds 중심 좌표를 시군구 코드로 변환 |

### 기술 선택 트레이드오프

| 결정 | 장점 | 단점 |
|------|------|------|
| MyBatis (JPA 대신) | SQL 직접 제어, bounds 필터링 같은 범위 쿼리에 유리, 러닝 커브 낮음 | 엔티티 자동 매핑 없음, CRUD 보일러플레이트 증가 |
| Spring Boot (Express 대신) | 강력한 타입 시스템, 의존성 주입, 체계적 레이어 분리 | 초기 설정 복잡, 메모리 사용량 높음 |
| Next.js (Vanilla JS 대신) | React 컴포넌트 재사용, 상태 관리 용이, 개발 생산성 | 번들 크기 증가, SSR 오버헤드 (CSR만 사용하므로 최소화) |

---

## 시스템 아키텍처

```
+------------------------------------------------------------------+
|                      Browser (Client)                             |
|                                                                   |
|  +------------------------------------------------------------+  |
|  |            Next.js (React) Application                      |  |
|  |                                                             |  |
|  |  KakaoMap.js         useApartments.js       api.js          |  |
|  |  - 지도 렌더링       - 데이터 fetch 훅     - fetch wrapper  |  |
|  |  - idle 이벤트 감지  - 로딩/에러 상태      - Base URL 관리  |  |
|  |  - 마커/오버레이     - debounce 적용                        |  |
|  +------------------------------------------------------------+  |
|                          |                                        |
|                   debounce (500ms)                                 |
|                          |                                        |
|              GET /api/apartments                                   |
|              ?sw_lat=..&sw_lng=..&ne_lat=..&ne_lng=..             |
|                          |                                        |
+------------------------------------------------------------------+
                           |
                           v
+------------------------------------------------------------------+
|              Spring Boot 3.x Backend (port 8080)                  |
|                                                                   |
|  +------------------+  +------------------+  +-----------------+  |
|  | Apartment        |  | Region           |  | Geocode         |  |
|  | Controller       |  | Service          |  | Service         |  |
|  |                  |  |                  |  |                 |  |
|  | 1.bounds 수신    |->| 2.bounds 내      |  | 5.주소->좌표    |  |
|  | 7.응답 반환      |<-|   region_code    |  |  (Kakao REST)   |  |
|  |                  |  |   조회           |  |                 |  |
|  +------------------+  +--------+---------+  +-------^---------+  |
|                                 |                    |            |
|                        +--------v---------+          |            |
|                        | Trade            |          |            |
|                        | Service          |----------+            |
|                        |                  |                       |
|                        | 3.캐시 확인      |                       |
|                        | 4.공공API 호출   |                       |
|                        | 6.결과 캐싱      |                       |
|                        +--------+---------+                       |
|                                 |                                 |
|  +------------------+           |                                 |
|  | MyBatis Mappers  |<----------+                                 |
|  | - TradeCacheMapper           |                                 |
|  | - GeocodeCacheMapper         |                                 |
|  +------------------+           |                                 |
+------------------------------------------------------------------+
                               |
              +----------------+----------------+
              v                v                v
   +---------------+  +---------------+  +------------------+
   | PostgreSQL    |  | 공공데이터     |  | Kakao REST API   |
   | (Neon)        |  | 포털 API      |  |                  |
   |               |  |               |  | - 주소 검색      |
   | - dong_code   |  | 실거래가      |  |   (geocoding)    |
   | - region_code |  | 상세 자료     |  | - 좌표->행정구역 |
   | - apt_trade   |  |               |  |                  |
   |   _cache      |  |               |  |                  |
   | - geocode     |  |               |  |                  |
   |   _cache      |  |               |  |                  |
   +---------------+  +---------------+  +------------------+
```

---

## 데이터 흐름 (상세)

### 메인 플로우: 지도 이동 -> 마커 표시

```
1. [Frontend] 사용자가 지도 이동/확대/축소
2. [Frontend] idle 이벤트 발생 -> useApartments 훅의 debounce (500ms)
3. [Frontend] 줌 레벨 확인
   - level 5 이상 (200m 축척 미만): ZoomMessage 컴포넌트 표시, 기존 마커 제거, API 호출 안함
   - level 4 이하 (200m 축척 이상): 다음 단계 진행
4. [Frontend] 현재 지도 bounds (sw_lat, sw_lng, ne_lat, ne_lng) 추출
5. [Frontend] GET /api/apartments?sw_lat=..&sw_lng=..&ne_lat=..&ne_lng=.. 호출
6. [Backend] ApartmentController가 bounds 파라미터 수신 및 유효성 검증
7. [Backend] RegionService: bounds 영역의 5개 기준점(4 꼭짓점 + 중심) 좌표 계산
8. [Backend] RegionService: Kakao REST API "좌표->행정구역 변환" 호출하여 시군구 코드 획득
9. [Backend] TradeService: 각 시군구 코드에 대해
   a. TradeCacheMapper로 apt_trade_cache에서 해당 시군구+현재월 데이터 존재 여부 확인
   b. 캐시 미스 시: 공공API 호출 (LAWD_CD=시군구코드, DEAL_YMD=현재년월)
   c. XmlParserUtil로 공공API 응답 XML 파싱
   d. GeocodeService: 각 거래 건의 주소로 Kakao REST API geocoding하여 좌표 획득
   e. TradeCacheMapper/GeocodeCacheMapper로 파싱 결과 + 좌표를 DB에 저장
   f. 캐시 히트 시: TradeCacheMapper로 DB에서 직접 조회
10. [Backend] bounds 범위 내 좌표를 가진 데이터만 필터링하여 ApartmentResponse DTO로 반환
11. [Frontend] 기존 마커 전부 제거
12. [Frontend] MarkerOverlay 컴포넌트로 커스텀 오버레이 마커 생성 (아파트명 + 거래가)
```

### 좌표->시군구 코드 매핑 전략

Kakao REST API의 `https://dapi.kakao.com/v2/local/geo/coord2regioncode` 를 사용한다.
- 입력: 경도(x), 위도(y)
- 출력: region_type "B" (법정동) 항목의 code
- code의 앞 5자리가 시군구 코드 = region_code 테이블의 code와 일치

bounds의 4개 꼭짓점과 중심점(총 5개 좌표)에 대해 조회하여, 고유한 시군구 코드 목록을 추출한다.

### 캐싱 전략

| 항목 | 설계 |
|------|------|
| 캐시 단위 | 시군구 코드(5자리) + 계약년월(YYYYMM) |
| 캐시 저장소 | PostgreSQL apt_trade_cache 테이블 |
| 캐시 갱신 | 현재 월 데이터는 하루 1회 갱신 (fetched_at 기준), 과거 월은 영구 캐시 |
| 캐시 무효화 | fetched_at이 당일 0시 이전이면 현재 월 데이터 재조회 |
| 좌표 캐시 | geocoding 결과는 geocode_cache 테이블에 저장하여 재호출 방지 |

### 마커 데이터 구조

```json
{
  "aptNm": "래미안아파트",
  "dealAmount": "95,000",
  "lat": 37.5012,
  "lng": 127.0396,
  "excluUseAr": 84.97,
  "floor": 12,
  "dealDate": "2026-03-15",
  "buildYear": 2008,
  "umdNm": "잠실동",
  "jibun": "40-2",
  "roadNm": "올림픽로35길"
}
```

프론트엔드에서는 커스텀 오버레이로 아파트명과 거래가를 표시하고, 클릭 시 상세 정보를 표시한다.

### Kakao Maps 줌 레벨 기준

Kakao Maps SDK의 줌 레벨(level)은 숫자가 작을수록 확대된 상태이다.
- level 1 = 20m, level 2 = 30m, level 3 = 50m, level 4 = 100m, level 5 = 250m, ...
- **200m 축척 이상 확대** = level 4 이하 (level 1~4)
- level 5 이상이면 안내 메시지 표시

---

## 핵심 아키텍처 결정사항

### 1. 좌표->행정구역 매핑: Kakao REST API 사용
- **결정**: DB 기반 공간 쿼리 대신 Kakao REST API 좌표->행정구역 변환 사용
- **근거**: PostGIS 설치 없이 구현 가능, Neon 호스팅 DB에 공간 확장 불필요
- **트레이드오프**: Kakao API 호출 횟수 증가 (매 조회 시 최대 5회) vs 공간 데이터 관리 복잡도 제거

### 2. Geocoding: 백엔드에서 일괄 처리 후 캐시
- **결정**: 공공API 응답의 주소를 Kakao REST API로 geocoding하여 좌표를 구하고, DB에 캐시
- **근거**: 같은 아파트 주소는 매월 반복 등장하므로, 한번 geocoding 하면 재사용 가능
- **트레이드오프**: 첫 조회 시 geocoding 지연 발생 vs 이후 조회는 DB에서 즉시 반환

### 3. 데이터 조회 범위: 현재 월만 조회
- **결정**: MVP 단계에서는 현재 년월의 실거래가만 조회
- **근거**: 공공API가 월 단위 조회만 지원, 여러 월 조회 시 API 호출 급증
- **트레이드오프**: 최신 데이터만 표시 vs 과거 추이 분석 불가 (향후 확장 지점)

### 4. 프론트엔드와 백엔드 분리 배포
- **결정**: Next.js 프론트엔드와 Spring Boot 백엔드를 독립적으로 배포
- **근거**: 기술 스택이 다르므로(Node.js vs JVM) 별도 프로세스로 실행, CORS로 통신
- **트레이드오프**: CORS 설정 필요 vs 독립적 배포/스케일 가능

### 5. MyBatis XML Mapper 사용 (어노테이션 기반 대신)
- **결정**: SQL을 XML Mapper 파일에 작성하고, Java 인터페이스는 `@Mapper`로 선언
- **근거**: bounds 필터링, 캐시 히트 판정 등 조건 분기가 있는 SQL을 XML의 동적 SQL(`<if>`, `<foreach>`)로 관리하기 용이
- **트레이드오프**: XML 파일 관리 필요 vs SQL 가독성 및 유지보수성 우수

---

## 디렉토리 구조

```
map-api-test/
├── _workspace/                          # 설계 산출물
│   ├── 00_input.md
│   ├── 01_architecture.md
│   ├── 02_api_spec.md
│   └── 03_db_schema.md
├── projs/
│   ├── fe/                              # 프론트엔드 (Next.js)
│   │   ├── package.json
│   │   ├── next.config.mjs
│   │   ├── .env.local.example           # NEXT_PUBLIC_API_URL 등
│   │   ├── src/
│   │   │   ├── app/
│   │   │   │   ├── layout.js            # 루트 레이아웃 (Kakao SDK 스크립트 로드)
│   │   │   │   ├── page.js              # 메인 지도 페이지
│   │   │   │   └── globals.css          # 전역 스타일 (지도 전체 화면)
│   │   │   ├── components/
│   │   │   │   ├── KakaoMap.js          # 지도 초기화, 이벤트 바인딩
│   │   │   │   ├── MarkerOverlay.js     # 커스텀 오버레이 마커 생성/관리
│   │   │   │   └── ZoomMessage.js       # 줌 레벨 부족 시 안내 메시지
│   │   │   ├── hooks/
│   │   │   │   └── useApartments.js     # 아파트 데이터 fetch + debounce 훅
│   │   │   └── lib/
│   │   │       ├── api.js               # fetch wrapper (Base URL, 에러 처리)
│   │   │       └── utils.js             # debounce, formatPrice 유틸
│   │   └── public/                      # 정적 자원 (있을 경우)
│   │
│   └── be/                              # 백엔드 (Spring Boot)
│       ├── build.gradle                 # Gradle 빌드 스크립트
│       ├── settings.gradle              # 프로젝트 설정
│       ├── src/
│       │   ├── main/
│       │   │   ├── java/com/mapapi/
│       │   │   │   ├── MapApiApplication.java       # @SpringBootApplication 메인 클래스
│       │   │   │   ├── config/
│       │   │   │   │   ├── WebConfig.java           # CORS 설정 (@Configuration)
│       │   │   │   │   └── MyBatisConfig.java       # MyBatis 설정 (MapperScan, TypeAlias)
│       │   │   │   ├── controller/
│       │   │   │   │   └── ApartmentController.java # REST 엔드포인트 (@RestController)
│       │   │   │   ├── service/
│       │   │   │   │   ├── RegionService.java       # 좌표->시군구 코드 (Kakao API)
│       │   │   │   │   ├── TradeService.java        # 공공API 호출 + 캐시 로직
│       │   │   │   │   └── GeocodeService.java      # 주소->좌표 변환 (Kakao API)
│       │   │   │   ├── mapper/
│       │   │   │   │   ├── TradeCacheMapper.java    # MyBatis Mapper 인터페이스 (@Mapper)
│       │   │   │   │   └── GeocodeCacheMapper.java  # MyBatis Mapper 인터페이스 (@Mapper)
│       │   │   │   ├── dto/
│       │   │   │   │   ├── ApartmentResponse.java   # API 응답 DTO (success, count, data)
│       │   │   │   │   └── TradeItem.java           # 개별 거래 건 DTO
│       │   │   │   └── util/
│       │   │   │       └── XmlParserUtil.java       # 공공API XML 응답 파서
│       │   │   └── resources/
│       │   │       ├── application.yml               # Spring Boot 설정 (DB, MyBatis)
│       │   │       ├── mapper/
│       │   │       │   ├── TradeCacheMapper.xml      # 거래 캐시 SQL (MyBatis XML)
│       │   │       │   └── GeocodeCacheMapper.xml    # 지오코딩 캐시 SQL (MyBatis XML)
│       │   │       └── schema.sql                    # DDL (spring.sql.init으로 자동 실행)
│       │   └── test/
│       │       └── java/com/mapapi/
│       │           ├── controller/
│       │           │   └── ApartmentControllerTest.java
│       │           └── service/
│       │               └── TradeServiceTest.java
│       └── gradle/
│           └── wrapper/                  # Gradle Wrapper
├── scripts/
│   └── init-db.js                       # 기존 DB 초기화 스크립트 (Node.js, 유지)
├── .env.example
├── .env                                 # (사용자가 직접 관리, 절대 수정 금지)
└── package.json                         # 루트 (init-db.js용 Node.js 의존성)
```

---

## Spring Boot 핵심 설정

### application.yml (환경변수 참조)

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 5
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql

mybatis:
  mapper-locations: classpath:mapper/*.xml
  type-aliases-package: com.mapapi.dto
  configuration:
    map-underscore-to-camel-case: true

app:
  kakao:
    rest-api-key: ${KAKAO_REST_API_KEY}
    javascript-key: ${KAKAO_JAVASCRIPT_KEY}
  data-go-kr:
    service-key: ${DATA_GO_KR_SERVICE_KEY}
```

### build.gradle 주요 의존성

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.2.x'
    id 'io.spring.dependency-management' version '1.1.x'
}

java {
    sourceCompatibility = '17'
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter:3.0.3'
    implementation 'org.postgresql:postgresql'
    implementation 'com.fasterxml.jackson.dataformat:jackson-dataformat-xml'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.mybatis.spring.boot:mybatis-spring-boot-starter-test:3.0.3'
}
```

---

## 프론트엔드 전달 사항

1. **Next.js App Router 사용**: `src/app/` 디렉토리 기반. 지도 페이지는 클라이언트 컴포넌트(`"use client"`)로 구현한다.
2. **Kakao Maps SDK 로드**: `layout.js`의 `<Script>` 태그로 Kakao Maps JS SDK를 로드한다. SDK 키는 백엔드 `GET /api/config` 에서 받거나, `next.config.mjs`의 환경변수(`NEXT_PUBLIC_KAKAO_JS_KEY`)를 사용한다.
3. **이벤트 처리**: `KakaoMap.js`에서 `idle` 이벤트에 500ms debounce 적용. `useApartments` 훅이 fetch와 상태 관리를 담당한다.
4. **줌 레벨 체크**: `map.getLevel()` <= 4 일 때만 API 호출. 그 외에는 `ZoomMessage` 컴포넌트 표시 + 기존 마커 전부 제거.
5. **마커 관리**: `MarkerOverlay.js`에서 `kakao.maps.CustomOverlay` 배열을 관리. API 호출 전 기존 오버레이 전부 `setMap(null)`로 제거 후 새로 생성.
6. **API 통신**: `lib/api.js`에서 Base URL(`http://localhost:8080/api`)을 관리. fetch wrapper로 에러 핸들링 공통화.
7. **환경변수**: `.env.local`에 `NEXT_PUBLIC_API_URL=http://localhost:8080/api` 설정. 사용자가 `.env.local.example`을 복사하여 직접 작성.
8. **CORS**: 개발 시 `http://localhost:3000` (Next.js dev) -> `http://localhost:8080` (Spring Boot). Spring Boot WebConfig에서 허용.

## 백엔드 전달 사항

1. **Spring Boot 3.x + Java 17**: `MapApiApplication.java`에 `@SpringBootApplication` 선언. port 8080.
2. **CORS 설정**: `WebConfig.java`에서 `WebMvcConfigurer` 구현. 개발 시 `http://localhost:3000` 허용, `GET` 메서드만 허용.
3. **환경변수**: `application.yml`에서 `${VARIABLE}` 형태로 시스템 환경변수 참조. `.env` 파일은 직접 읽지 않는다. 개발 시 IDE 또는 셸에서 환경변수를 설정한다.
4. **MyBatis 설정**: `MyBatisConfig.java`에서 `@MapperScan("com.mapapi.mapper")` 선언. `map-underscore-to-camel-case: true`로 DB 컬럼명(snake_case)을 DTO 필드명(camelCase)에 자동 매핑.
5. **PostgreSQL 연결**: `spring.datasource`에서 환경변수 기반 JDBC URL 구성. HikariCP 커넥션 풀(기본 5개).
6. **공공API 호출**: `RestTemplate`으로 HTTP GET. `XmlParserUtil`에서 Jackson XML 또는 JAXB로 XML 파싱. `numOfRows=1000`으로 설정하여 페이징 최소화.
7. **Kakao REST API 호출**: `Authorization: KakaoAK {KAKAO_REST_API_KEY}` 헤더 사용. `RestTemplate`에 `HttpHeaders` 설정.
8. **에러 핸들링**: `@RestControllerAdvice`로 글로벌 예외 처리. 외부 API 실패 시 502, 파라미터 오류 시 400, 내부 오류 시 500 반환.
9. **DB 초기화**: `schema.sql`에 `CREATE TABLE IF NOT EXISTS`로 apt_trade_cache, geocode_cache DDL 작성. `spring.sql.init.mode=always`로 앱 시작 시 자동 실행. 기존 dong_code, region_code는 `scripts/init-db.js`(Node.js)로 별도 초기화.

## QA 전달 사항

1. **테스트 범위**: 줌 레벨 제한 동작, debounce 동작, API 응답 파싱, 캐시 히트/미스, 마커 표시/제거
2. **API 명세**: `_workspace/02_api_spec.md` 참조
3. **비기능 테스트**: 다수 마커(100+) 표시 시 성능, 공공API 타임아웃 처리
4. **단위 테스트**: Spring Boot 테스트 프레임워크 사용. `TradeServiceTest`에서 캐시 로직 검증, `ApartmentControllerTest`에서 파라미터 검증

## DevOps 전달 사항

1. **프론트엔드 기술 스택**: Node.js 20+, Next.js 14, npm
2. **백엔드 기술 스택**: Java 17+, Spring Boot 3.x, Gradle, PostgreSQL 16 (Neon)
3. **환경변수**: `.env.example` 참조 (KAKAO_REST_API_KEY, KAKAO_JAVASCRIPT_KEY, DATA_GO_KR_SERVICE_KEY, DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD). 실제 값은 사용자가 직접 설정.
4. **실행**:
   - 백엔드: `cd projs/be && ./gradlew bootRun` (환경변수 설정 필요)
   - 프론트엔드: `cd projs/fe && npm install && npm run dev`
   - DB 초기화: `node scripts/init-db.js` (기존 스크립트)
5. **빌드**:
   - 백엔드: `./gradlew build` -> `build/libs/mapapi-0.0.1-SNAPSHOT.jar`
   - 프론트엔드: `npm run build` -> `.next/` 정적 빌드
