# API 명세

## 기본 정보
- **Base URL**: `http://localhost:8080/api`
- **인증 방식**: 없음 (MVP, 내부 API)
- **응답 형식**: JSON
- **인코딩**: UTF-8
- **서버**: Spring Boot 3.x (port 8080)

---

## 엔드포인트 목록

| Method | Path | 설명 | 인증 | Spring 매핑 |
|--------|------|------|------|------------|
| GET | /api/apartments | 지도 영역 내 아파트 실거래가 조회 | X | `@GetMapping("/api/apartments")` |
| GET | /api/config | 프론트엔드 설정값 반환 (Kakao JS Key) | X | `@GetMapping("/api/config")` |

---

## 상세 API

### [GET] /api/apartments

지도에 보이는 영역(bounds) 내의 아파트 실거래가 데이터를 반환한다.

**Spring Controller 매핑**:
```java
@RestController
public class ApartmentController {

    @GetMapping("/api/apartments")
    public ApartmentResponse getApartments(
        @RequestParam("sw_lat") double swLat,
        @RequestParam("sw_lng") double swLng,
        @RequestParam("ne_lat") double neLat,
        @RequestParam("ne_lng") double neLng,
        @RequestParam(value = "deal_ymd", required = false) String dealYmd
    ) { ... }
}
```

#### 요청

| 파라미터 | 타입 | 필수 | 설명 | 예시 |
|---------|------|------|------|------|
| sw_lat | double | O | 남서쪽 위도 | 37.4950 |
| sw_lng | double | O | 남서쪽 경도 | 127.0300 |
| ne_lat | double | O | 북동쪽 위도 | 37.5100 |
| ne_lng | double | O | 북동쪽 경도 | 127.0500 |
| deal_ymd | String | X | 계약년월 (YYYYMM), 기본값=현재년월 | 202604 |

```
GET /api/apartments?sw_lat=37.4950&sw_lng=127.0300&ne_lat=37.5100&ne_lng=127.0500
```

#### 처리 흐름

```
1. ApartmentController: Query 파라미터 유효성 검증 (4개 좌표 필수, 숫자 범위)
2. RegionService: bounds 영역에서 5개 기준점 좌표 계산 (4 꼭짓점 + 중심)
3. RegionService: Kakao REST API 좌표->행정구역 변환으로 시군구 코드 목록 추출
4. TradeService: 각 시군구 코드에 대해
   a. TradeCacheMapper로 apt_trade_cache에서 해당 시군구+년월 데이터 조회
   b. 캐시 미스 또는 만료 시: 공공API 호출 -> XmlParserUtil로 파싱 -> GeocodeService로 geocoding -> TradeCacheMapper/GeocodeCacheMapper로 캐시 저장
5. TradeCacheMapper: bounds 범위 내 좌표의 데이터만 필터링 (WHERE lat BETWEEN, lng BETWEEN)
6. ApartmentController: ApartmentResponse DTO로 응답 반환
```

#### 응답 (200 OK)

```json
{
  "success": true,
  "count": 25,
  "dealYmd": "202604",
  "data": [
    {
      "aptNm": "래미안잠실아파트",
      "dealAmount": "180,000",
      "lat": 37.5065,
      "lng": 127.0832,
      "excluUseAr": 84.97,
      "floor": 15,
      "dealDate": "2026-04-12",
      "buildYear": 2008,
      "umdNm": "잠실동",
      "jibun": "40-2",
      "roadNm": "올림픽로35길",
      "sggCd": "11710",
      "cdealType": "",
      "dealingGbn": "중개거래"
    }
  ]
}
```

#### 응답 필드 설명

| 필드 | 타입 | 설명 |
|------|------|------|
| success | boolean | 성공 여부 |
| count | int | 반환된 거래 건수 |
| dealYmd | String | 조회 대상 계약년월 (YYYYMM) |
| data | List\<TradeItem\> | 거래 데이터 배열 |
| data[].aptNm | String | 아파트명 |
| data[].dealAmount | String | 거래금액 (만원, 콤마 포함) |
| data[].lat | double | 위도 |
| data[].lng | double | 경도 |
| data[].excluUseAr | double | 전용면적 (m2) |
| data[].floor | int | 층 |
| data[].dealDate | String | 거래일 (YYYY-MM-DD) |
| data[].buildYear | int | 건축년도 |
| data[].umdNm | String | 법정동명 |
| data[].jibun | String | 지번 |
| data[].roadNm | String | 도로명 |
| data[].sggCd | String | 시군구코드 (5자리) |
| data[].cdealType | String | 해제여부 (빈 문자열=정상, "O"=해제) |
| data[].dealingGbn | String | 거래유형 (중개거래, 직거래 등) |

#### DTO 클래스 구조

```java
// ApartmentResponse.java
public class ApartmentResponse {
    private boolean success;
    private int count;
    private String dealYmd;
    private List<TradeItem> data;
}

// TradeItem.java
public class TradeItem {
    private String aptNm;
    private String dealAmount;
    private double lat;
    private double lng;
    private double excluUseAr;
    private int floor;
    private String dealDate;
    private int buildYear;
    private String umdNm;
    private String jibun;
    private String roadNm;
    private String sggCd;
    private String cdealType;
    private String dealingGbn;
}
```

#### 에러 응답

**400 Bad Request** -- 파라미터 누락 또는 유효하지 않은 값

```json
{
  "success": false,
  "error": "INVALID_PARAMS",
  "message": "sw_lat, sw_lng, ne_lat, ne_lng 파라미터가 필요합니다."
}
```

**502 Bad Gateway** -- 외부 API 호출 실패

```json
{
  "success": false,
  "error": "EXTERNAL_API_ERROR",
  "message": "공공데이터 API 호출에 실패했습니다. 잠시 후 다시 시도해주세요."
}
```

**500 Internal Server Error** -- 서버 내부 오류

```json
{
  "success": false,
  "error": "INTERNAL_ERROR",
  "message": "서버 오류가 발생했습니다."
}
```

**글로벌 예외 처리 (Spring)**:
```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleMissingParam(MissingServletRequestParameterException ex) {
        return Map.of("success", false, "error", "INVALID_PARAMS", "message", ex.getMessage());
    }

    @ExceptionHandler(ExternalApiException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Map<String, Object> handleExternalApi(ExternalApiException ex) {
        return Map.of("success", false, "error", "EXTERNAL_API_ERROR", "message", "공공데이터 API 호출에 실패했습니다.");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleGeneral(Exception ex) {
        return Map.of("success", false, "error", "INTERNAL_ERROR", "message", "서버 오류가 발생했습니다.");
    }
}
```

---

### [GET] /api/config

프론트엔드에서 필요한 공개 설정값을 반환한다. Kakao JavaScript Key를 프론트엔드에 하드코딩하지 않기 위해 사용한다.

**Spring Controller 매핑**:
```java
@GetMapping("/api/config")
public Map<String, Object> getConfig() {
    return Map.of(
        "success", true,
        "data", Map.of("kakaoJsKey", kakaoJavascriptKey)
    );
}
```

#### 요청

파라미터 없음.

```
GET /api/config
```

#### 응답 (200 OK)

```json
{
  "success": true,
  "data": {
    "kakaoJsKey": "abc123..."
  }
}
```

#### 응답 필드 설명

| 필드 | 타입 | 설명 |
|------|------|------|
| data.kakaoJsKey | String | Kakao Maps JavaScript SDK 앱 키 |

---

## 외부 API 호출 명세 (백엔드 내부 참조용)

### 1. 국토교통부 실거래가 API

```
GET http://apis.data.go.kr/1613000/RTMSDataSvcAptTradeDev/getRTMSDataSvcAptTradeDev
  ?serviceKey={DATA_GO_KR_SERVICE_KEY}
  &LAWD_CD={시군구코드5자리}
  &DEAL_YMD={YYYYMM}
  &pageNo=1
  &numOfRows=1000
```

- 응답: XML
- 파싱 대상: `response > body > items > item` 배열
- Spring Boot에서: `RestTemplate.getForObject(url, String.class)` 후 `XmlParserUtil`로 파싱
- 또는 Jackson XML `XmlMapper`로 직접 역직렬화

### 2. Kakao REST API -- 좌표->행정구역 변환

```
GET https://dapi.kakao.com/v2/local/geo/coord2regioncode
  ?x={경도}&y={위도}
Headers:
  Authorization: KakaoAK {KAKAO_REST_API_KEY}
```

- 응답: JSON
- 사용 필드: `documents[]` 중 `region_type === "B"` 항목의 `code` 앞 5자리
- Spring Boot에서: `RestTemplate` + `HttpEntity`(Authorization 헤더) 사용

### 3. Kakao REST API -- 주소 검색 (Geocoding)

```
GET https://dapi.kakao.com/v2/local/search/address
  ?query={시군구명+법정동명+지번}
Headers:
  Authorization: KakaoAK {KAKAO_REST_API_KEY}
```

- 응답: JSON
- 사용 필드: `documents[0].x` (경도), `documents[0].y` (위도)
- 결과 없을 시: 해당 거래 건은 좌표 없이 스킵 (마커 미표시)

---

## Rate Limiting / 호출 제한 주의사항

| API | 제한 | 대응 |
|-----|------|------|
| 공공데이터 API | 일 1,000회 (기본) | DB 캐싱으로 반복 호출 방지 |
| Kakao REST API | 초 10회 / 일 100,000회 | geocoding 결과 DB 캐싱, 좌표->행정구역은 최대 5회/조회 |

---

## CORS 설정 (Spring Boot WebConfig)

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins("http://localhost:3000")
            .allowedMethods("GET")
            .allowedHeaders("Content-Type");
    }
}
```

개발 환경:
```
Access-Control-Allow-Origin: http://localhost:3000
Access-Control-Allow-Methods: GET
Access-Control-Allow-Headers: Content-Type
```
