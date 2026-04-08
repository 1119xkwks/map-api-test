package com.mapapi.dto;

/**
 * 개별 아파트 매매 거래 건 DTO.
 * MyBatis 결과 매핑 및 API 응답에 공통으로 사용한다.
 */
public class TradeItem {

    private String aptNm;        // 아파트명
    private String dealAmount;   // 거래금액 (만원, 콤마 포함)
    private Double lat;          // 위도
    private Double lng;          // 경도
    private double excluUseAr;   // 전용면적 (m2)
    private int floor;           // 층
    private String dealDate;     // 거래일 (YYYY-MM-DD, 응답용)
    private int buildYear;       // 건축년도
    private String umdNm;        // 법정동명
    private String jibun;        // 지번
    private String roadNm;       // 도로명
    private String sggCd;        // 시군구코드 (5자리)
    private String cdealType;    // 해제여부 (""=정상, "O"=해제)
    private String dealingGbn;   // 거래유형

    // MyBatis 매핑 및 내부 처리용 필드
    private String dealYmd;      // 계약년월 (YYYYMM)
    private String dealYear;     // 거래년
    private String dealMonth;    // 거래월
    private String dealDay;      // 거래일
    private String umdCd;        // 읍면동코드

    public TradeItem() {
    }

    // --- Getters & Setters ---

    public String getAptNm() {
        return aptNm;
    }

    public void setAptNm(String aptNm) {
        this.aptNm = aptNm;
    }

    public String getDealAmount() {
        return dealAmount;
    }

    public void setDealAmount(String dealAmount) {
        this.dealAmount = dealAmount;
    }

    public Double getLat() {
        return lat;
    }

    public void setLat(Double lat) {
        this.lat = lat;
    }

    public Double getLng() {
        return lng;
    }

    public void setLng(Double lng) {
        this.lng = lng;
    }

    public double getExcluUseAr() {
        return excluUseAr;
    }

    public void setExcluUseAr(double excluUseAr) {
        this.excluUseAr = excluUseAr;
    }

    public int getFloor() {
        return floor;
    }

    public void setFloor(int floor) {
        this.floor = floor;
    }

    public String getDealDate() {
        return dealDate;
    }

    public void setDealDate(String dealDate) {
        this.dealDate = dealDate;
    }

    public int getBuildYear() {
        return buildYear;
    }

    public void setBuildYear(int buildYear) {
        this.buildYear = buildYear;
    }

    public String getUmdNm() {
        return umdNm;
    }

    public void setUmdNm(String umdNm) {
        this.umdNm = umdNm;
    }

    public String getJibun() {
        return jibun;
    }

    public void setJibun(String jibun) {
        this.jibun = jibun;
    }

    public String getRoadNm() {
        return roadNm;
    }

    public void setRoadNm(String roadNm) {
        this.roadNm = roadNm;
    }

    public String getSggCd() {
        return sggCd;
    }

    public void setSggCd(String sggCd) {
        this.sggCd = sggCd;
    }

    public String getCdealType() {
        return cdealType;
    }

    public void setCdealType(String cdealType) {
        this.cdealType = cdealType;
    }

    public String getDealingGbn() {
        return dealingGbn;
    }

    public void setDealingGbn(String dealingGbn) {
        this.dealingGbn = dealingGbn;
    }

    public String getDealYmd() {
        return dealYmd;
    }

    public void setDealYmd(String dealYmd) {
        this.dealYmd = dealYmd;
    }

    public String getDealYear() {
        return dealYear;
    }

    public void setDealYear(String dealYear) {
        this.dealYear = dealYear;
    }

    public String getDealMonth() {
        return dealMonth;
    }

    public void setDealMonth(String dealMonth) {
        this.dealMonth = dealMonth;
    }

    public String getDealDay() {
        return dealDay;
    }

    public void setDealDay(String dealDay) {
        this.dealDay = dealDay;
    }

    public String getUmdCd() {
        return umdCd;
    }

    public void setUmdCd(String umdCd) {
        this.umdCd = umdCd;
    }

    /**
     * dealYear, dealMonth, dealDay로부터 "YYYY-MM-DD" 형식의 dealDate를 생성한다.
     */
    public void buildDealDate() {
        if (dealYear != null && dealMonth != null && dealDay != null) {
            this.dealDate = dealYear + "-"
                    + String.format("%02d", Integer.parseInt(dealMonth.trim()))
                    + "-"
                    + String.format("%02d", Integer.parseInt(dealDay.trim()));
        }
    }
}
