package com.mapapi.controller;

import com.mapapi.config.AppProperties;
import com.mapapi.dto.ApartmentResponse;
import com.mapapi.dto.ConfigResponse;
import com.mapapi.dto.TradeItem;
import com.mapapi.service.RegionService;
import com.mapapi.service.TradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 아파트 실거래가 API 컨트롤러.
 * - GET /api/apartments: 지도 영역 내 아파트 실거래가 조회
 * - GET /api/config: 프론트엔드 설정값 반환
 */
@RestController
public class ApartmentController {

    private static final Logger log = LoggerFactory.getLogger(ApartmentController.class);

    private final RegionService regionService;
    private final TradeService tradeService;
    private final AppProperties appProperties;

    public ApartmentController(RegionService regionService,
                                TradeService tradeService,
                                AppProperties appProperties) {
        this.regionService = regionService;
        this.tradeService = tradeService;
        this.appProperties = appProperties;
    }

    /**
     * 지도에 보이는 영역(bounds) 내의 아파트 실거래가 데이터를 반환한다.
     *
     * @param swLat   남서쪽 위도
     * @param swLng   남서쪽 경도
     * @param neLat   북동쪽 위도
     * @param neLng   북동쪽 경도
     * @param dealYmd 계약년월 (YYYYMM, 선택, 기본=현재년월)
     * @return 거래 데이터 응답
     */
    @GetMapping("/api/apartments")
    public ApartmentResponse getApartments(
            @RequestParam("sw_lat") double swLat,
            @RequestParam("sw_lng") double swLng,
            @RequestParam("ne_lat") double neLat,
            @RequestParam("ne_lng") double neLng,
            @RequestParam(value = "deal_ymd", required = false) String dealYmd
    ) {
        // 파라미터 유효성 검증
        validateBounds(swLat, swLng, neLat, neLng);

        // deal_ymd 기본값: 현재년월
        if (dealYmd == null || dealYmd.isEmpty()) {
            LocalDate now = LocalDate.now();
            dealYmd = String.format("%d%02d", now.getYear(), now.getMonthValue());
        } else {
            validateDealYmd(dealYmd);
        }

        // 지도 중심 좌표 계산
        double centerLat = (swLat + neLat) / 2;
        double centerLng = (swLng + neLng) / 2;

        log.info("아파트 실거래가 조회: center=({},{}), bounds=[{},{},{},{}], dealYmd={}",
                centerLat, centerLng, swLat, swLng, neLat, neLng, dealYmd);

        // 1. 지도 중심 좌표 → 시군구 코드 + 동 이름 (Kakao API 1회)
        RegionService.RegionInfo regionInfo = regionService.getRegionInfo(centerLat, centerLng);

        if (regionInfo == null) {
            log.warn("시군구 코드를 찾을 수 없음: center=({},{})", centerLat, centerLng);
            return ApartmentResponse.ok(dealYmd, List.of());
        }

        // 2. 해당 동의 거래 데이터 조회 (최근 3개월 순차, 해당 동만 geocoding)
        TradeService.TradeResult result = tradeService.getTradeData(
                regionInfo.getSggCd(), regionInfo.getUmdNm(),
                regionInfo.getAddressPrefix(), dealYmd,
                swLat, swLng, neLat, neLng);

        return ApartmentResponse.ok(result.getDealYmd(), result.getItems());
    }

    /**
     * 프론트엔드에서 필요한 공개 설정값을 반환한다.
     */
    @GetMapping("/api/config")
    public ConfigResponse getConfig() {
        return ConfigResponse.ok(Map.of(
                "kakaoJsKey", appProperties.getKakao().getJavascriptKey()
        ));
    }

    /**
     * 테스트용: 도로명 주소 → Kakao geocode 결과 직접 확인
     * 예: GET /api/test/geocode?address=서울특별시 송파구 송이로31길 56
     */
    @GetMapping("/api/test/geocode")
    public Map<String, Object> testGeocode(@RequestParam("address") String address) {
        log.info("[TEST geocode] address='{}'", address);

        try {
            String encodedQuery = java.net.URLEncoder.encode(address, java.nio.charset.StandardCharsets.UTF_8);
            String url = "https://dapi.kakao.com/v2/local/search/address?query=" + encodedQuery;

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("Authorization", "KakaoAK " + appProperties.getKakao().getRestApiKey());
            org.springframework.http.HttpEntity<Void> entity = new org.springframework.http.HttpEntity<>(headers);

            org.springframework.web.client.RestTemplate rt = new org.springframework.web.client.RestTemplate();
            org.springframework.http.ResponseEntity<String> response = rt.exchange(
                    url, org.springframework.http.HttpMethod.GET, entity, String.class);

            log.info("[TEST geocode] HTTP {}, body={}", response.getStatusCode(), response.getBody());

            return Map.of(
                    "address", address,
                    "url", url,
                    "status", response.getStatusCode().toString(),
                    "body", response.getBody() != null ? response.getBody() : "null"
            );
        } catch (Exception e) {
            log.error("[TEST geocode] 에러: {}", e.getMessage(), e);
            return Map.of(
                    "address", address,
                    "error", e.getMessage()
            );
        }
    }

    /**
     * bounds 파라미터 유효성 검증
     */
    private void validateBounds(double swLat, double swLng, double neLat, double neLng) {
        if (swLat < -90 || swLat > 90 || neLat < -90 || neLat > 90) {
            throw new IllegalArgumentException("위도는 -90 ~ 90 범위여야 합니다.");
        }
        if (swLng < -180 || swLng > 180 || neLng < -180 || neLng > 180) {
            throw new IllegalArgumentException("경도는 -180 ~ 180 범위여야 합니다.");
        }
        if (swLat > neLat) {
            throw new IllegalArgumentException("sw_lat은 ne_lat보다 작아야 합니다.");
        }
        if (swLng > neLng) {
            throw new IllegalArgumentException("sw_lng은 ne_lng보다 작아야 합니다.");
        }
    }

    /**
     * deal_ymd 파라미터 유효성 검증 (YYYYMM 형식, 6자리 숫자)
     */
    private void validateDealYmd(String dealYmd) {
        if (dealYmd.length() != 6) {
            throw new IllegalArgumentException("deal_ymd는 YYYYMM 형식(6자리)이어야 합니다.");
        }
        try {
            int year = Integer.parseInt(dealYmd.substring(0, 4));
            int month = Integer.parseInt(dealYmd.substring(4, 6));
            if (year < 2006 || year > 2100 || month < 1 || month > 12) {
                throw new IllegalArgumentException("deal_ymd의 년/월 값이 유효하지 않습니다.");
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("deal_ymd는 숫자로 구성된 YYYYMM 형식이어야 합니다.");
        }
    }
}
