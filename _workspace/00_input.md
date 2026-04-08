# 00. 사용자 입력 정리

## 앱 설명
카카오맵 기반 아파트 실거래가 지도 웹앱. 지도에서 보이는 영역의 아파트 매매 실거래가를 조회하여 마커로 표시한다.

## 핵심 기능
1. **카카오맵 표시**: Kakao Maps JavaScript SDK를 사용하여 지도 렌더링
2. **아파트 실거래가 마커 표시**: 지도 위에 아파트명 + 최근 실거래가를 마커로 표시
3. **줌 레벨 제한**: 200m 축척 이상 확대 시에만 데이터 조회, 그 이하(축소)에서는 안내 메시지
4. **자동 갱신**: 지도 확대/축소/이동 시마다 기존 마커를 지우고 새로운 영역의 데이터를 조회하여 표시

## 기술 스택
- **Frontend**: Next.js (React)
- **Backend**: Spring Boot (모놀리식, Gradle 빌드)
- **DB 연동**: MyBatis (JPA 사용하지 않음)
- **Database**: PostgreSQL (Neon) — 기존 dong_code, region_code 테이블 활용
- **외부 API**: 국토교통부 아파트 매매 실거래가 상세 자료 (getRTMSDataSvcAptTradeDev)

## 규모
MVP (단일 페이지 지도 앱)

## 기존 코드
- `package.json`: dotenv, iconv-lite, pg 의존성
- `scripts/init-db.js`: dong_code, region_code 테이블 초기화 스크립트
- `.env.example`: KAKAO_REST_API_KEY, KAKAO_JAVASCRIPT_KEY, DATA_GO_KR_SERVICE_KEY, DB 접속 정보

## 외부 API 명세

### 국토교통부 아파트 매매 실거래가 상세 자료
- **URL**: `http://apis.data.go.kr/1613000/RTMSDataSvcAptTradeDev/getRTMSDataSvcAptTradeDev`
- **Method**: GET (REST)
- **Response Format**: XML

#### 요청 파라미터
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| serviceKey | String | O | 공공데이터포털 인증키 (URL Encoding) |
| LAWD_CD | String | O | 법정동코드 앞 5자리 (시군구 코드) |
| DEAL_YMD | String | O | 계약년월 (YYYYMM) |
| pageNo | Int | X | 페이지 번호 (기본값 1) |
| numOfRows | Int | X | 한 페이지 결과 수 (기본값 10) |

#### 주요 응답 항목
| 항목 | 설명 |
|------|------|
| aptNm | 아파트명 |
| dealAmount | 거래금액 (만원) |
| excluUseAr | 전용면적 (㎡) |
| floor | 층 |
| buildYear | 건축년도 |
| dealYear / dealMonth / dealDay | 거래년/월/일 |
| umdNm | 법정동 |
| jibun | 지번 |
| sggCd | 시군구코드 |
| umdCd | 읍면동코드 |
| roadNm | 도로명 |
| cdealType | 해제여부 |
| dealingGbn | 거래유형 |

## DB 스키마 (기존)
- `dong_code(code VARCHAR(10) PK, name VARCHAR(100))` — 법정동 전체 코드 + 이름
- `region_code(code VARCHAR(5) PK, name VARCHAR(100))` — 시군구 코드(앞5자리) + 대표 이름

## 주의사항
- 과도한 API 호출 방지: 줌 레벨 200m 이상에서만 조회
- 지도 이동/확대/축소 시 debounce 적용 필요
- .env 파일은 절대 생성/수정하지 않음
