package com.mapapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapapi.config.AppProperties;
import com.mapapi.dto.TradeItem;
import com.mapapi.mapper.GeocodeCacheMapper;
import com.mapapi.mapper.RegionCodeMapper;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 주소 -> 좌표(위경도) 변환 서비스.
 * Kakao REST API의 address 검색 엔드포인트를 사용하고,
 * geocode_cache 테이블에 결과를 캐싱하여 중복 호출을 방지한다.
 */
@Service
public class GeocodeService {

    private static final Logger log = LoggerFactory.getLogger(GeocodeService.class);
    private static final String KAKAO_ADDRESS_URL = "https://dapi.kakao.com/v2/local/search/address";

    private final AppProperties appProperties;
    private final GeocodeCacheMapper geocodeCacheMapper;
    private final RegionCodeMapper regionCodeMapper;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public GeocodeService(AppProperties appProperties,
                          GeocodeCacheMapper geocodeCacheMapper,
                          RegionCodeMapper regionCodeMapper) {
        this.appProperties = appProperties;
        this.geocodeCacheMapper = geocodeCacheMapper;
        this.regionCodeMapper = regionCodeMapper;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 단일 주소를 geocoding하여 위경도를 반환한다.
     * geocode_cache에서 먼저 조회하고, 캐시 미스 시 Kakao API를 호출한다.
     *
     * @param address 전체 주소 문자열 (예: "서울특별시 강남구 역삼동 123-4")
     * @return {lat, lng} 또는 null (결과 없을 시)
     */
    public double[] geocodeAddress(String address) {
        // 1. 캐시 조회
        Map<String, Object> cached = geocodeCacheMapper.findByAddress(address);
        if (cached != null) {
            double lat = ((Number) cached.get("lat")).doubleValue();
            double lng = ((Number) cached.get("lng")).doubleValue();
            return new double[]{lat, lng};
        }

        // 2. Kakao REST API 호출
        try {
            String encodedQuery = URLEncoder.encode(address, StandardCharsets.UTF_8);
            String url = KAKAO_ADDRESS_URL + "?query=" + encodedQuery;

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "KakaoAK " + appProperties.getKakao().getRestApiKey());
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode documents = root.get("documents");

            if (documents != null && documents.isArray() && documents.size() > 0) {
                JsonNode first = documents.get(0);
                double lat = first.get("y").asDouble();
                double lng = first.get("x").asDouble();

                // 3. 캐시 저장
                geocodeCacheMapper.insertIfNotExists(address, lat, lng);
                return new double[]{lat, lng};
            }
        } catch (Exception e) {
            log.warn("Geocoding 실패: address={}, error={}", address, e.getMessage());
        }

        return null;
    }

    /**
     * 거래 항목 리스트에 대해 일괄 geocoding을 수행한다.
     * 시군구 코드로 region_code 테이블에서 시군구명을 조회하고,
     * 각 항목의 법정동+지번으로 전체 주소를 조합하여 geocoding한다.
     *
     * @param items  거래 항목 리스트
     * @param sggCd  시군구 코드 (5자리)
     */
    public void geocodeTradeItems(List<TradeItem> items, String sggCd) {
        // region_code 테이블에서 시군구명 조회
        String regionName = regionCodeMapper.findNameByCode(sggCd);
        if (regionName == null) {
            log.warn("시군구 코드에 해당하는 지역명을 찾을 수 없음: {}", sggCd);
            return;
        }

        // 각 항목의 주소 조합
        List<String> addresses = items.stream()
                .map(item -> buildAddress(regionName, item.getUmdNm(), item.getJibun()))
                .collect(Collectors.toList());

        // 일괄 캐시 조회 (성능 최적화)
        Map<String, double[]> cachedMap = new HashMap<>();
        if (!addresses.isEmpty()) {
            List<String> uniqueAddresses = addresses.stream().distinct().collect(Collectors.toList());
            List<Map<String, Object>> cachedList = geocodeCacheMapper.findByAddresses(uniqueAddresses);
            if (cachedList != null) {
                for (Map<String, Object> row : cachedList) {
                    String addr = (String) row.get("address");
                    double lat = ((Number) row.get("lat")).doubleValue();
                    double lng = ((Number) row.get("lng")).doubleValue();
                    cachedMap.put(addr, new double[]{lat, lng});
                }
            }
        }

        // 각 항목에 대해 geocoding
        for (int i = 0; i < items.size(); i++) {
            TradeItem item = items.get(i);
            String address = addresses.get(i);

            // 캐시 히트
            if (cachedMap.containsKey(address)) {
                double[] coords = cachedMap.get(address);
                item.setLat(coords[0]);
                item.setLng(coords[1]);
                continue;
            }

            // Kakao API 호출
            double[] coords = geocodeAddress(address);
            if (coords != null) {
                item.setLat(coords[0]);
                item.setLng(coords[1]);
                cachedMap.put(address, coords); // 동일 주소 중복 호출 방지
            }

            // Rate Limit 준수: 초당 10회 제한 (100ms 딜레이)
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * 시군구명, 법정동명, 지번을 조합하여 전체 주소를 생성한다.
     */
    private String buildAddress(String regionName, String umdNm, String jibun) {
        StringBuilder sb = new StringBuilder();
        sb.append(regionName);
        if (umdNm != null && !umdNm.isEmpty()) {
            sb.append(" ").append(umdNm.trim());
        }
        if (jibun != null && !jibun.isEmpty()) {
            sb.append(" ").append(jibun.trim());
        }
        return sb.toString();
    }
}
