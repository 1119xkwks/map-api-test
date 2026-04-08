package com.mapapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapapi.config.AppProperties;
import com.mapapi.dto.TradeItem;
import com.mapapi.mapper.GeocodeCacheMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 주소 → 좌표(위경도) 변환 서비스.
 * 도로명 주소 기반으로 Kakao geocoding API를 호출한다.
 * 예: "서울 송파구 송이로31길 56" → (37.491, 127.134)
 */
@Service
public class GeocodeService {

    private static final Logger log = LoggerFactory.getLogger(GeocodeService.class);
    private static final String KAKAO_ADDRESS_URL = "https://dapi.kakao.com/v2/local/search/address.json";

    private final AppProperties appProperties;
    private final GeocodeCacheMapper geocodeCacheMapper;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public GeocodeService(AppProperties appProperties,
                          GeocodeCacheMapper geocodeCacheMapper) {
        this.appProperties = appProperties;
        this.geocodeCacheMapper = geocodeCacheMapper;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 거래 항목 리스트에 대해 도로명 주소 기반 일괄 geocoding.
     * 고유 주소 단위로 중복 제거하여 Kakao API 호출을 최소화한다.
     */
    /**
     * @param items 거래 항목 리스트
     * @param addressPrefix 주소 앞부분 (예: "서울특별시 송파구")
     */
    public void geocodeTradeItems(List<TradeItem> items, String addressPrefix) {
        // 1. 각 항목의 도로명 주소 조합
        Map<Integer, String> indexToAddress = new LinkedHashMap<>();
        for (int i = 0; i < items.size(); i++) {
            String address = buildRoadAddress(items.get(i), addressPrefix);
            indexToAddress.put(i, address);
        }

        // 2. 고유 주소 추출 (빈 주소 제외)
        List<String> uniqueAddresses = indexToAddress.values().stream()
                .filter(addr -> !addr.isEmpty())
                .distinct()
                .collect(Collectors.toList());

        log.info("[Geocoding] 전체 {}건, 고유 도로명주소 {}건, 주소목록={}",
                items.size(), uniqueAddresses.size(), uniqueAddresses);

        if (uniqueAddresses.isEmpty()) return;

        // 3. DB 캐시 일괄 조회
        Map<String, double[]> coordsMap = new HashMap<>();
        List<Map<String, Object>> cachedList = geocodeCacheMapper.findByAddresses(uniqueAddresses);
        if (cachedList != null) {
            for (Map<String, Object> row : cachedList) {
                String addr = (String) row.get("address");
                double lat = ((Number) row.get("lat")).doubleValue();
                double lng = ((Number) row.get("lng")).doubleValue();
                coordsMap.put(addr, new double[]{lat, lng});
            }
        }

        log.info("[Geocoding] DB 캐시 히트 {}건, 미스 {}건",
                coordsMap.size(), uniqueAddresses.size() - coordsMap.size());

        // 4. 캐시 미스만 Kakao API 호출
        int apiCallCount = 0;
        for (String address : uniqueAddresses) {
            if (coordsMap.containsKey(address)) continue;

            double[] coords = callKakaoGeocode(address);
            if (coords != null) {
                coordsMap.put(address, coords);
                try {
                    geocodeCacheMapper.insertIfNotExists(address, coords[0], coords[1]);
                } catch (Exception e) {
                    log.debug("geocode_cache 저장 실패: {}", e.getMessage());
                }
            }
            apiCallCount++;

            // Rate Limit: 초당 10회
            try { Thread.sleep(100); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }

        log.info("[Geocoding] Kakao API 호출 {}건 완료", apiCallCount);

        // 5. 결과를 전체 항목에 매핑
        int mappedCount = 0;
        for (Map.Entry<Integer, String> entry : indexToAddress.entrySet()) {
            double[] coords = coordsMap.get(entry.getValue());
            if (coords != null) {
                TradeItem item = items.get(entry.getKey());
                item.setLat(coords[0]);
                item.setLng(coords[1]);
                mappedCount++;
            }
        }

        log.info("[Geocoding] 좌표 매핑 완료 {}/{}건", mappedCount, items.size());
    }

    /**
     * 도로명 주소 조합.
     * roadNmBonbun/roadNmBubun에서 앞자리 0 제거하여 번지 생성.
     * 예: roadNm="송이로31길", bonbun="00056", bubun="00000" → "송파구 송이로31길 56"
     */
    /**
     * 도로명 주소 조합.
     * 예: addressPrefix="서울특별시 송파구", roadNm="송이로31길", bonbun="00056" → "서울특별시 송파구 송이로31길 56"
     */
    private String buildRoadAddress(TradeItem item, String addressPrefix) {
        String roadNm = item.getRoadNm();
        if (roadNm == null || roadNm.trim().isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append(addressPrefix).append(" ").append(roadNm.trim());

        // 본번
        String bonbun = trimLeadingZeros(item.getRoadNmBonbun());
        if (!bonbun.isEmpty()) {
            sb.append(" ").append(bonbun);
            // 부번 (0이 아닌 경우에만 추가)
            String bubun = trimLeadingZeros(item.getRoadNmBubun());
            if (!bubun.isEmpty() && !"0".equals(bubun)) {
                sb.append("-").append(bubun);
            }
        }

        return sb.toString();
    }

    /** "00056" → "56", "00000" → "" */
    private String trimLeadingZeros(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        String trimmed = value.trim().replaceFirst("^0+", "");
        return trimmed.isEmpty() ? "" : trimmed;
    }

    private double[] callKakaoGeocode(String address) {
        try {
            String encodedQuery = URLEncoder.encode(address, StandardCharsets.UTF_8);
            String urlStr = KAKAO_ADDRESS_URL + "?query=" + encodedQuery;

            log.info("[Kakao geocode] FULL URL: {}", urlStr);

            // RestTemplate 대신 HttpURLConnection 직접 사용 (이중 인코딩 방지)
            java.net.URL url = new java.net.URI(urlStr).toURL();
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "KakaoAK " + appProperties.getKakao().getRestApiKey());
            conn.setRequestProperty("Accept-Charset", "UTF-8");

            int status = conn.getResponseCode();
            java.io.BufferedReader reader;
            if (status >= 200 && status < 300) {
                reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            } else {
                reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8));
            }

            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();

            String responseBody = sb.toString();
            log.info("[Kakao geocode] HTTP {}, body(200자)={}", status,
                    responseBody.substring(0, Math.min(200, responseBody.length())));

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode documents = root.get("documents");

            if (documents != null && documents.isArray() && documents.size() > 0) {
                JsonNode first = documents.get(0);
                double lat = first.get("y").asDouble();
                double lng = first.get("x").asDouble();
                log.info("[Kakao geocode] 성공: '{}' → ({}, {})", address, lat, lng);
                return new double[]{lat, lng};
            } else {
                log.warn("[Kakao geocode] 결과 없음: '{}', body={}", address, responseBody);
            }
        } catch (Exception e) {
            log.warn("[Kakao geocode] 실패: '{}', error={}", address, e.getMessage());
        }
        return null;
    }
}
