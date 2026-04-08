# 코드 리뷰 & 테스트 보고서

## 종합 평가
- **배포 준비 상태**: 수정 후 배포 가능
- **테스트 커버리지**: 테스트 미작성 상태 (기존 테스트 파일은 빈 stub만 존재)
- **총평**: 프론트엔드-백엔드 간 API 인터페이스가 올바르게 일치하며, 핵심 비즈니스 로직(캐시, XML 파싱, Geocoding)이 체계적으로 구현되어 있다. XSS 방어 누락과 예외 처리 분류 오류 2건의 필수 수정을 직접 반영하였다.

---

## 발견 사항

### 필수 수정 (보안/기능) -- 2건, 모두 수정 완료

#### [RED-01] XSS 취약점 -- MarkerOverlay.js innerHTML 미이스케이프 (수정 완료)

- **파일**: `projs/fe/src/components/MarkerOverlay.js`
- **내용**: `createDetailHTML()` 및 마커 말풍선에서 `aptNm`, `umdNm`, `jibun`, `roadNm`, `dealAmount` 등 서버 데이터를 `innerHTML`에 직접 삽입하고 있었다. 공공API 데이터에 HTML 특수문자가 포함될 경우 XSS 공격이 가능하다.
- **수정**: `escapeHtml()` 유틸리티 함수를 `lib/utils.js`에 추가하고, 모든 동적 데이터 삽입 지점에 적용하였다.

#### [RED-02] RuntimeException 일괄 502 처리 -- GlobalExceptionHandler (수정 완료)

- **파일**: `projs/be/src/main/java/com/mapapi/controller/GlobalExceptionHandler.java`
- **내용**: `@ExceptionHandler(RuntimeException.class)`가 모든 RuntimeException을 502 Bad Gateway로 응답하고 있었다. NullPointerException, ArrayIndexOutOfBoundsException 등 내부 버그도 502로 반환되어 디버깅이 어렵고 클라이언트가 잘못된 에러 유형을 수신했다.
- **수정**: RuntimeException 메시지에 외부 API 관련 키워드("공공데이터 API", "Kakao", "공공API")가 포함된 경우에만 502를 반환하고, 나머지는 500 Internal Server Error로 분류하도록 변경하였다.

---

### 권장 수정 (품질/성능) -- 5건

#### [YELLOW-01] Geocoding Thread.sleep(100) 블로킹

- **파일**: `projs/be/src/main/java/com/mapapi/service/GeocodeService.java` (line 157)
- **내용**: Kakao API Rate Limit 준수를 위해 `Thread.sleep(100)`을 사용하지만, 이는 요청 처리 쓰레드를 블로킹한다. 시군구 1개에 거래 건이 500건이면 최소 50초가 소요될 수 있다.
- **권장**: 첫 호출 시 캐시 미스 건수만큼 시간이 걸리는 것은 불가피하나, 로깅으로 소요 시간을 모니터링하고 비동기 처리(@Async)를 검토할 것.

#### [YELLOW-02] CORS 설정 localhost 고정

- **파일**: `projs/be/src/main/java/com/mapapi/config/WebConfig.java` (line 16)
- **내용**: `allowedOrigins("http://localhost:3000")`이 하드코딩되어 있다. 배포 환경에서는 실제 도메인으로 변경해야 한다.
- **권장**: 환경변수(`CORS_ALLOWED_ORIGIN`)로 외부화할 것.

#### [YELLOW-03] RestTemplate 인스턴스 관리

- **파일**: `RegionService.java`, `TradeService.java`, `GeocodeService.java`
- **내용**: 각 서비스에서 `new RestTemplate()`으로 개별 인스턴스를 생성하고 있다. 커넥션 풀링, 타임아웃 설정 등이 누락되어 있다.
- **권장**: `@Bean`으로 RestTemplate을 등록하고 커넥션 타임아웃(5초), 읽기 타임아웃(10초)을 설정할 것.

#### [YELLOW-04] 프론트엔드 API Base URL 기본값

- **파일**: `projs/fe/src/lib/api.js` (line 7)
- **내용**: `NEXT_PUBLIC_API_URL` 환경변수가 없으면 `http://localhost:8080`을 사용한다. API 경로가 `/api/config`, `/api/apartments`로 이미 `/api` prefix를 포함하고 있으므로 Base URL에 `/api`를 붙이면 이중 prefix가 된다.
- **현상태**: 현재 코드에서 `fetchApartments`는 `${API_BASE_URL}/api/apartments`로 호출하므로, `NEXT_PUBLIC_API_URL`이 `http://localhost:8080`이면 정상 동작한다. 아키텍처 문서에서 `http://localhost:8080/api`로 설명한 부분과 실제 코드가 다르지만, 코드가 올바른 상태이다.

#### [YELLOW-05] 공공API 1000건 페이징 미처리

- **파일**: `projs/be/src/main/java/com/mapapi/service/TradeService.java` (line 141)
- **내용**: `numOfRows=1000`으로 설정하여 단일 페이지만 조회한다. 시군구에 따라 월 거래 건수가 1000건을 초과할 수 있으며, 이 경우 일부 데이터가 누락된다.
- **권장**: 공공API 응답의 totalCount를 확인하여 다음 페이지가 있으면 추가 호출하는 페이징 로직을 추가할 것.

---

### 참고 사항 -- 4건

#### [GREEN-01] TypeScript 미사용

- 프론트엔드가 JavaScript로 작성되어 있다. MVP 단계에서는 적절하나, 규모 확장 시 TypeScript 마이그레이션을 권장한다.

#### [GREEN-02] 프론트엔드 테스트 프레임워크 미설정

- package.json에 테스트 관련 의존성(vitest, @testing-library/react 등)이 없다. 테스트 작성 시 추가 설정이 필요하다.

#### [GREEN-03] 백엔드 테스트 stub만 존재

- `MapApiApplicationTests.java`에 빈 contextLoads() 테스트만 있다. 실질적인 단위/통합 테스트가 필요하다.

#### [GREEN-04] 향후 과거 월 데이터 조회 기능

- 현재 MVP에서는 현재 월만 조회한다. deal_ymd 파라미터는 이미 지원하므로, 프론트엔드에서 월 선택 UI를 추가하면 확장 가능하다.

---

## 정합성 매트릭스

| 검증 항목 | 상태 | 비고 |
|----------|------|------|
| 아키텍처 설계 <-> 코드 구조 | OK | 설계 문서의 디렉토리 구조, 레이어 분리가 코드에 올바르게 반영됨 |
| API 명세 <-> 컨트롤러 구현 | OK | 엔드포인트 URL, 파라미터명, 응답 JSON 구조 일치 |
| API 명세 <-> 프론트엔드 호출 | OK | fetchApartments 파라미터(sw_lat, sw_lng, ne_lat, ne_lng), fetchConfig 호출 경로 일치 |
| API 응답 DTO <-> 프론트엔드 파싱 | OK | success, count, dealYmd, data 필드 구조 일치, TradeItem 필드명 일치 |
| DB 스키마 <-> schema.sql | OK | apt_trade_cache, geocode_cache DDL이 설계 문서와 일치 |
| DB 스키마 <-> MyBatis Mapper XML | OK | SELECT/INSERT 컬럼이 테이블 정의와 일치, #{} 파라미터 바인딩 사용 |
| 줌 레벨 기준 (level <= 4) | OK | KakaoMap.js ZOOM_THRESHOLD=4, level > 4이면 안내 메시지 |
| 캐시 전략 <-> TradeService 구현 | OK | 캐시 미스/히트/만료 로직이 설계 문서의 판정 로직과 일치 |
| 보안 체크리스트 | OK (수정 후) | SQL Injection(#{} 사용), XSS(escapeHtml 추가), XXE(DocumentBuilderFactory 설정), API Key 환경변수 참조 |

---

## 수정 이력

| 파일 | 수정 내용 | 이슈 번호 |
|------|----------|----------|
| `projs/fe/src/lib/utils.js` | `escapeHtml()` 함수 추가 | RED-01 |
| `projs/fe/src/components/MarkerOverlay.js` | 모든 innerHTML 동적 데이터에 escapeHtml 적용, import 추가 | RED-01 |
| `projs/be/src/main/java/com/mapapi/controller/GlobalExceptionHandler.java` | RuntimeException 핸들러에서 외부 API 관련 여부를 메시지 기반으로 판별하여 502/500 분기 처리 | RED-02 |

---

## 빌드 확인

| 항목 | 결과 | 비고 |
|------|------|------|
| 프론트엔드 빌드 (`npm run build`) | 성공 | Next.js 16.2.2, Turbopack, 정적 페이지 생성 완료 |
| 백엔드 컴파일 (`./gradlew compileJava`) | 성공 | Spring Boot 3.2.5, Java 25 환경에서 컴파일 성공 |
