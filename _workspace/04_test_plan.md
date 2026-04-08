# 테스트 계획

## 테스트 전략
- **커버리지 목표**: 80% 이상 (단위 테스트 기준)
- **테스트 레벨**: 단위 / 통합 / E2E
- **테스트 비율**: 단위(70%) > 통합(20%) > E2E(10%)

---

## 테스트 매트릭스

| 기능 (FR) | 단위 테스트 | 통합 테스트 | E2E 테스트 | 우선순위 |
|-----------|-----------|-----------|-----------|---------|
| FR-1 카카오맵 표시 | - | - | O | P0 |
| FR-2 실거래가 마커 표시 | O | O | O | P0 |
| FR-3 줌 레벨 제한 | O | - | O | P0 |
| FR-4 자동 갱신 | O | - | O | P0 |
| FR-5 API 호출 제어 (debounce) | O | - | O | P0 |
| 캐시 히트/미스 로직 | O | O | - | P0 |
| XML 파싱 | O | - | - | P0 |
| Geocoding 캐시 | O | O | - | P1 |
| 에러 핸들링 | O | O | O | P0 |
| 파라미터 유효성 검증 | O | O | - | P0 |

---

## 단위 테스트 시나리오

### 프론트엔드

#### utils.js - debounce

| # | 시나리오 | 입력 | 기대 결과 |
|---|---------|------|----------|
| U-FE-01 | debounce 정상 동작 | 함수 + 500ms | delay 이후 1회만 실행됨 |
| U-FE-02 | debounce 연속 호출 | 100ms 간격 5회 호출 | 마지막 호출 후 500ms 뒤 1회만 실행 |
| U-FE-03 | debounce 독립 호출 | 1000ms 간격 2회 호출 | 각각 1회씩 총 2회 실행 |

#### utils.js - formatPrice

| # | 시나리오 | 입력 | 기대 결과 |
|---|---------|------|----------|
| U-FE-04 | 1억 이상 정수 | "180,000" | "18억" |
| U-FE-05 | 1억 이상 소수 | "95,000" | "9.5억" |
| U-FE-06 | 1억 이상 소수 자릿수 | "12,500" | "1.25억" |
| U-FE-07 | 1억 미만 | "8,000" | "8,000만" |
| U-FE-08 | null 입력 | null | "" |
| U-FE-09 | 빈 문자열 | "" | "" |
| U-FE-10 | 비숫자 문자열 | "abc" | "abc" |

#### utils.js - escapeHtml

| # | 시나리오 | 입력 | 기대 결과 |
|---|---------|------|----------|
| U-FE-11 | HTML 태그 이스케이프 | `"<script>alert(1)</script>"` | `"&lt;script&gt;alert(1)&lt;/script&gt;"` |
| U-FE-12 | 앰퍼샌드 이스케이프 | `"A & B"` | `"A &amp; B"` |
| U-FE-13 | 따옴표 이스케이프 | `"He said \"hi\""` | `"He said &quot;hi&quot;"` |
| U-FE-14 | null 입력 | null | "" |
| U-FE-15 | 정상 문자열 | "래미안" | "래미안" |

#### MarkerOverlay.js - groupByApartment

| # | 시나리오 | 입력 | 기대 결과 |
|---|---------|------|----------|
| U-FE-16 | 동일 아파트 그룹핑 | 같은 aptNm+lat+lng 3건 | 1그룹, trades 3건, 최신 거래가 대표 |
| U-FE-17 | 서로 다른 아파트 | aptNm 다른 2건 | 2그룹 |
| U-FE-18 | 좌표 없는 건 제외 | lat=null인 건 포함 | 좌표 없는 건은 스킵 |
| U-FE-19 | 빈 배열 | [] | [] |

#### useApartments.js

| # | 시나리오 | 입력 | 기대 결과 |
|---|---------|------|----------|
| U-FE-20 | 성공 응답 | 유효 bounds | apartments에 data 배열 저장, isLoading=false |
| U-FE-21 | 에러 응답 | 서버 500 | error에 메시지 저장, apartments=[] |
| U-FE-22 | 요청 취소 | 연속 호출 | 이전 요청 abort, 최신 결과만 반영 |
| U-FE-23 | clearApartments | - | apartments=[], 진행 중 요청 취소 |

#### KakaoMap.js - 줌 레벨

| # | 시나리오 | 입력 | 기대 결과 |
|---|---------|------|----------|
| U-FE-24 | level <= 4 (확대 충분) | level=3 | ZoomMessage 숨김, API 호출 |
| U-FE-25 | level > 4 (확대 부족) | level=5 | ZoomMessage 표시, 마커 제거, API 미호출 |
| U-FE-26 | level 경계값 | level=4 | ZoomMessage 숨김, API 호출 |

### 백엔드

#### ApartmentController - 파라미터 검증

| # | 시나리오 | 입력 | 기대 결과 |
|---|---------|------|----------|
| U-BE-01 | 정상 bounds | sw_lat=37.49, sw_lng=127.03, ne_lat=37.51, ne_lng=127.05 | 200 + 정상 응답 |
| U-BE-02 | 위도 범위 초과 | sw_lat=100 | 400 + INVALID_PARAMS |
| U-BE-03 | sw_lat > ne_lat | sw_lat=37.6, ne_lat=37.5 | 400 + INVALID_PARAMS |
| U-BE-04 | sw_lng > ne_lng | sw_lng=128, ne_lng=127 | 400 + INVALID_PARAMS |
| U-BE-05 | deal_ymd 기본값 | deal_ymd 미전달 | 현재 년월 사용 |
| U-BE-06 | deal_ymd 유효 | deal_ymd=202604 | 해당 년월 사용 |
| U-BE-07 | deal_ymd 잘못된 형식 | deal_ymd=2026 | 400 + INVALID_PARAMS |
| U-BE-08 | deal_ymd 비숫자 | deal_ymd=abcdef | 400 + INVALID_PARAMS |
| U-BE-09 | deal_ymd 월 범위 초과 | deal_ymd=202613 | 400 + INVALID_PARAMS |
| U-BE-10 | 필수 파라미터 누락 | sw_lat만 전달 | 400 + INVALID_PARAMS |
| U-BE-11 | 타입 불일치 | sw_lat=abc | 400 + INVALID_PARAMS |

#### TradeService - 캐시 로직

| # | 시나리오 | 입력 | 기대 결과 |
|---|---------|------|----------|
| U-BE-12 | 캐시 미스 (cnt=0) | 새 시군구+년월 | 공공API 호출 + DB 저장 |
| U-BE-13 | 캐시 히트 (과거 월) | cnt>0, 과거 월 | DB에서 직접 조회, API 미호출 |
| U-BE-14 | 캐시 히트 (현재 월, 오늘 fetch) | cnt>0, 현재 월, 오늘 fetched | DB에서 직접 조회 |
| U-BE-15 | 캐시 만료 (현재 월, 어제 fetch) | cnt>0, 현재 월, 어제 fetched | 기존 삭제 + 재호출 |
| U-BE-16 | 복수 시군구 처리 | sggCodes 3개 | 각각 캐시 확인, bounds 조회 1회 |

#### XmlParserUtil

| # | 시나리오 | 입력 | 기대 결과 |
|---|---------|------|----------|
| U-BE-17 | 정상 XML | 유효 item 3건 포함 XML | TradeItem 3건 |
| U-BE-18 | 빈 items | items 비어있는 XML | 빈 리스트 |
| U-BE-19 | resultCode 오류 | resultCode=01 | RuntimeException |
| U-BE-20 | aptNm 누락 item | aptNm 없는 item | 해당 item 스킵 |
| U-BE-21 | 숫자 필드 비정상 | floor="지하" | floor=0 (기본값) |
| U-BE-22 | XXE 공격 시도 | DOCTYPE 포함 XML | 파싱 거부 |

#### GeocodeService

| # | 시나리오 | 입력 | 기대 결과 |
|---|---------|------|----------|
| U-BE-23 | 캐시 히트 | DB에 존재하는 주소 | Kakao API 미호출, DB에서 반환 |
| U-BE-24 | 캐시 미스 | DB에 없는 주소 | Kakao API 호출 + DB 저장 |
| U-BE-25 | Kakao API 결과 없음 | 존재하지 않는 주소 | null 반환, 마커 미표시 |
| U-BE-26 | 주소 조합 | regionName="서울 강남구", umdNm="역삼동", jibun="123" | "서울 강남구 역삼동 123" |

#### RegionService

| # | 시나리오 | 입력 | 기대 결과 |
|---|---------|------|----------|
| U-BE-27 | 5개 좌표 계산 | bounds | SW, NW, NE, SE, Center 올바른 좌표 |
| U-BE-28 | 중복 시군구 제거 | 5개 좌표 중 3개 같은 시군구 | 고유 코드만 반환 |
| U-BE-29 | 좌표 변환 실패 시 | 1개 좌표 실패 | 나머지 4개만 포함 |

#### TradeItem - buildDealDate

| # | 시나리오 | 입력 | 기대 결과 |
|---|---------|------|----------|
| U-BE-30 | 정상 날짜 | year=2026, month=4, day=8 | "2026-04-08" |
| U-BE-31 | 한 자리 월/일 | year=2026, month=1, day=5 | "2026-01-05" |
| U-BE-32 | null 필드 | dealDay=null | dealDate 미생성 |

#### GlobalExceptionHandler

| # | 시나리오 | 입력 | 기대 결과 |
|---|---------|------|----------|
| U-BE-33 | 외부 API 관련 RuntimeException | 메시지에 "공공데이터 API" 포함 | 502 + EXTERNAL_API_ERROR |
| U-BE-34 | 일반 RuntimeException | NullPointerException | 500 + INTERNAL_ERROR |
| U-BE-35 | IllegalArgumentException | 유효성 검증 실패 | 400 + INVALID_PARAMS |

---

## 통합 테스트 시나리오

### API 엔드포인트 (Spring Boot MockMvc)

| # | 시나리오 | 요청 | 기대 결과 | 유형 |
|---|---------|------|----------|------|
| I-01 | 정상 조회 (캐시 히트) | GET /api/apartments?sw_lat=37.49&sw_lng=127.03&ne_lat=37.51&ne_lng=127.05 | 200 + success=true, data 배열 | 통합 |
| I-02 | 파라미터 누락 | GET /api/apartments?sw_lat=37.49 | 400 + INVALID_PARAMS | 통합 |
| I-03 | config 조회 | GET /api/config | 200 + success=true, kakaoJsKey 포함 | 통합 |
| I-04 | 외부 API 오류 시 | 공공API 모킹 - 오류 | 502 + EXTERNAL_API_ERROR | 통합 |
| I-05 | 빈 시군구 코드 | Kakao API 모킹 - 빈 응답 | 200 + data=[] | 통합 |

### MyBatis Mapper (DB 연동)

| # | 시나리오 | 기대 결과 | 유형 |
|---|---------|----------|------|
| I-06 | checkCacheStatus - 데이터 없음 | cnt=0, last_fetched=null | 통합 |
| I-07 | insertBatch + findByBounds | 저장 후 bounds 조건으로 조회 | 통합 |
| I-08 | deleteBySggCdAndDealYmd | 삭제 후 cnt=0 | 통합 |
| I-09 | geocode insertIfNotExists 중복 | UNIQUE 충돌 시 무시 | 통합 |
| I-10 | findByAddresses 일괄 조회 | 여러 주소 한 번에 조회 | 통합 |

---

## E2E 테스트 시나리오 (Playwright)

### 핵심 플로우

| # | 시나리오 | 단계 | 기대 결과 |
|---|---------|------|----------|
| E-01 | 초기 로드 | 페이지 접속 | 1. 로딩 스피너 표시 2. SDK 로드 후 지도 렌더링 |
| E-02 | 줌 레벨 부족 시 안내 | 1. 지도 축소 (level > 4) | ZoomMessage 표시, 마커 없음 |
| E-03 | 줌 충분 시 마커 표시 | 1. 지도 확대 (level <= 4) 2. idle 대기 | API 호출 후 마커 오버레이 표시 |
| E-04 | 지도 이동 시 갱신 | 1. 마커 표시 상태 2. 지도 드래그 | 기존 마커 제거 + 새 마커 표시 |
| E-05 | 마커 클릭 상세 팝업 | 1. 마커 표시 2. 마커 클릭 | 상세 팝업 (아파트명, 거래가, 면적 등) 표시 |
| E-06 | 상세 팝업 닫기 | 1. 팝업 열기 2. 닫기 버튼 클릭 | 팝업 사라짐 |
| E-07 | API 오류 시 | 백엔드 다운 상태에서 접속 | 에러 메시지 표시 |
| E-08 | SDK 로드 실패 | 잘못된 JS Key 응답 | "지도를 불러올 수 없습니다" 에러 표시 |

---

## 코드 리뷰 체크리스트

- [x] TypeScript 타입 안전성 -- N/A (JavaScript 사용, JSDoc으로 타입 힌트 제공)
- [x] 입력 검증 -- ApartmentController에서 bounds/dealYmd 유효성 검증 완료
- [x] 에러 처리 일관성 -- GlobalExceptionHandler로 400/502/500 분기 처리 (수정 완료)
- [x] SQL Injection 방지 -- MyBatis #{} 파라미터 바인딩 사용 확인
- [x] XSS 방지 -- escapeHtml 유틸리티 추가 적용 (수정 완료)
- [x] XXE 방지 -- XmlParserUtil에서 DocumentBuilderFactory XXE 보안 설정 확인
- [x] 환경변수 하드코딩 없음 -- application.yml에서 ${} 참조, 프론트엔드 NEXT_PUBLIC_API_URL
- [x] N+1 쿼리 없음 -- geocode 일괄 조회(findByAddresses) 최적화 확인
- [x] 불필요한 리렌더링 없음 -- useCallback, useMemo로 최적화 확인
- [x] CORS 설정 -- GET only, localhost:3000 허용, /api/** 경로 한정
