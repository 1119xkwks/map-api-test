# 아파트 매매 실거래가 API 정리

## 1. API 개요

- **API 이름 (영문)**: Detailed data on actual apartment sales prices  
- **API 이름 (국문)**: 아파트 매매 실거래가 상세 자료  
- **설명**: 지역코드(LAWD_CD)와 계약월(DEAL_YMD)을 기반으로 특정 지역의 아파트 매매 실거래 상세 데이터를 조회

### Base URL
http://apis.data.go.kr/1613000/RTMSDataSvcAptTradeDev

### Endpoint
/getRTMSDataSvcAptTradeDev

- Method: GET  
- Response Format: XML  
- 데이터 갱신 주기: 일 1회  
- TPS 제한: 30  

---

## 2. API 기능

### 아파트 매매 실거래가 조회

- Function Name: `getRTMSDataSvcAptTradeDev`
- 설명: 법정동 코드(앞 5자리) + 계약년월 기준 조회

---

## 3. Request Parameters

| 파라미터 | 설명 | 필수 여부 | 예시 |
|----------|------|----------|------|
| serviceKey | 인증키 (URL Encode) | 필수 | 발급키 |
| LAWD_CD | 지역코드 (5자리) | 필수 | 11110 |
| DEAL_YMD | 계약년월 (YYYYMM) | 필수 | 202407 |
| pageNo | 페이지 번호 | 선택 | 1 |
| numOfRows | 페이지당 건수 | 선택 | 10 |

---

## 4. Request Example

https://apis.data.go.kr/1613000/RTMSDataSvcAptTradeDev/getRTMSDataSvcAptTradeDev?serviceKey=서비스키&LAWD_CD=11110&DEAL_YMD=202407&pageNo=1&numOfRows=10

---

## 5. Response 구조

### Header

| 필드 | 설명 |
|------|------|
| resultCode | 결과 코드 |
| resultMsg | 결과 메시지 |

### Body (Pagination)

| 필드 | 설명 |
|------|------|
| numOfRows | 페이지 결과 수 |
| pageNo | 페이지 번호 |
| totalCount | 전체 데이터 수 |

### Item (실거래 데이터)

#### 위치 정보

| 필드 | 설명 |
|------|------|
| sggCd | 시군구 코드 |
| umdCd | 읍면동 코드 |
| umdNm | 법정동 |
| jibun | 지번 |
| roadNm | 도로명 |

#### 아파트 정보

| 필드 | 설명 |
|------|------|
| aptNm | 아파트명 |
| aptSeq | 단지 일련번호 |
| aptDong | 동 |
| buildYear | 건축년도 |

#### 거래 정보

| 필드 | 설명 |
|------|------|
| excluUseAr | 전용면적 |
| floor | 층 |
| dealYear | 계약년도 |
| dealMonth | 계약월 |
| dealDay | 계약일 |
| dealAmount | 거래금액 (만원) |

#### 거래 주체

| 필드 | 설명 |
|------|------|
| slerGbn | 매도자 |
| buyerGbn | 매수자 |
| dealingGbn | 거래유형 |
| estateAgentSggNm | 중개사 위치 |

#### 추가 정보

| 필드 | 설명 |
|------|------|
| cdealType | 거래 해제 여부 |
| cdealDay | 해제일 |
| rgstDate | 등기일 |
| landLeaseholdGbn | 토지임대부 여부 |

---

## 6. Response Example

```xml
<response>
  <header>
    <resultCode>000</resultCode>
    <resultMsg>OK</resultMsg>
  </header>
  <body>
    <items>
      <item>
        <aptNm>종로중흥S클래스</aptNm>
        <dealAmount>12,000</dealAmount>
        <dealYear>2024</dealYear>
        <dealMonth>7</dealMonth>
        <dealDay>23</dealDay>
        <floor>10</floor>
        <excluUseAr>17.811</excluUseAr>
      </item>
    </items>
    <numOfRows>1</numOfRows>
    <pageNo>1</pageNo>
    <totalCount>40</totalCount>
  </body>
</response>
```

---

## 7. 에러 코드

| 코드 | 설명 | 대응 |
|------|------|------|
| 01 | Application Error | 서버 문제 |
| 02 | DB Error | 서버 문제 |
| 03 | No Data | 데이터 없음 |
| 04 | HTTP Error | 요청 오류 |
| 05 | Timeout | 서버 지연 |
| 10 | 잘못된 요청 | ServiceKey 누락 |
| 11 | 필수 파라미터 없음 | 파라미터 확인 |
| 12 | API 없음 | URL 확인 |
| 20 | 접근 거부 | 승인 필요 |
| 22 | 요청 초과 | 트래픽 초과 |
| 30 | 잘못된 키 | 키 확인 |
| 31 | 만료된 키 | 연장 필요 |
| 32 | IP 불일치 | 등록 IP 확인 |

---

## 8. 핵심 요약

- REST GET API
- XML 응답
- 필수 파라미터: serviceKey, LAWD_CD, DEAL_YMD
- pagination 지원
- 거래 데이터 포함
- TPS 제한: 30
