# map-api-test

카카오맵 기반 아파트 실거래가 지도 웹앱. 지도에서 보이는 영역의 아파트 매매 실거래가를 마커로 표시한다.

## 기술 스택

| 구분 | 기술 |
|------|------|
| Frontend | Next.js 14 (App Router) |
| Backend | Spring Boot 3.2.5, Java 17+ |
| DB 연동 | MyBatis 3.0.3 (XML Mapper) |
| 빌드 | Gradle 8.14.4 |
| Database | PostgreSQL (Neon) |
| 지도 | Kakao Maps JavaScript SDK v3 |

## 사전 준비

1. `.env.example`을 복사하여 `.env` 파일을 생성하고 실제 값을 입력한다.
2. `projs/fe/.env.local.example`을 복사하여 `projs/fe/.env.local` 파일을 생성한다.

## 실행 방법

### 1. DB 초기화 (최초 1회)

```bash
npm install
node scripts/init-db.js
```

### 2. 백엔드 실행

```bash
cd projs/be

# Windows (Git Bash)
export JAVA_HOME="/c/Program Files/JAVA/JDK25"
export PATH="$JAVA_HOME/bin:$PATH"

# 환경변수 설정
export DB_HOST=<호스트>
export DB_PORT=5432
export DB_NAME=<DB명>
export DB_USER=<유저>
export DB_PASSWORD=<비밀번호>
export KAKAO_REST_API_KEY=<카카오 REST API 키>
export KAKAO_JAVASCRIPT_KEY=<카카오 JavaScript 키>
export DATA_GO_KR_SERVICE_KEY=<공공데이터포털 서비스키>

./gradlew bootRun
```

백엔드가 `http://localhost:8080`에서 실행된다.

### 3. 프론트엔드 실행

```bash
cd projs/fe
npm install
npm run dev
```

프론트엔드가 `http://localhost:3000`에서 실행된다.

### 4. 브라우저 접속

`http://localhost:3000` 으로 접속한다.

## 주요 기능

- 카카오맵 위에 아파트 매매 실거래가를 마커로 표시
- 200m 축척 이상 확대 시에만 데이터 조회 (과도한 API 호출 방지)
- 지도 이동/확대/축소 시 자동으로 데이터 갱신
- 마커 클릭 시 상세 정보 확인 (전용면적, 층, 거래일 등)
