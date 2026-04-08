package com.mapapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mapapi.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 좌표 -> 시군구 코드 변환 서비스.
 * Kakao REST API의 coord2regioncode 엔드포인트를 사용하여
 * 지도 bounds의 5개 기준점(4 꼭짓점 + 중심)에 해당하는 시군구 코드를 조회한다.
 */
@Service
public class RegionService {

    private static final Logger log = LoggerFactory.getLogger(RegionService.class);
    private static final String KAKAO_REGION_URL = "https://dapi.kakao.com/v2/local/geo/coord2regioncode";

    private final AppProperties appProperties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public RegionService(AppProperties appProperties) {
        this.appProperties = appProperties;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * bounds의 4 꼭짓점 + 중심점을 기반으로 고유 시군구 코드 목록을 반환한다.
     *
     * @param swLat 남서 위도
     * @param swLng 남서 경도
     * @param neLat 북동 위도
     * @param neLng 북동 경도
     * @return 고유 시군구 코드 리스트 (5자리)
     */
    public List<String> getRegionCodes(double swLat, double swLng, double neLat, double neLng) {
        // 5개 기준점: SW, NW, NE, SE, Center
        double centerLat = (swLat + neLat) / 2;
        double centerLng = (swLng + neLng) / 2;

        double[][] points = {
                {swLat, swLng},   // SW
                {neLat, swLng},   // NW
                {neLat, neLng},   // NE
                {swLat, neLng},   // SE
                {centerLat, centerLng} // Center
        };

        Set<String> sggCodes = new LinkedHashSet<>();

        for (double[] point : points) {
            try {
                String code = fetchRegionCode(point[0], point[1]);
                if (code != null) {
                    sggCodes.add(code);
                }
            } catch (Exception e) {
                log.warn("좌표->행정구역 변환 실패: lat={}, lng={}, error={}", point[0], point[1], e.getMessage());
            }
        }

        log.info("bounds에서 추출된 시군구 코드: {}", sggCodes);
        return new ArrayList<>(sggCodes);
    }

    /**
     * 단일 좌표에 대해 Kakao REST API를 호출하여 시군구 코드(5자리)를 반환한다.
     *
     * @param lat 위도
     * @param lng 경도
     * @return 시군구 코드 (5자리) 또는 null
     */
    private String fetchRegionCode(double lat, double lng) {
        String url = KAKAO_REGION_URL + "?x=" + lng + "&y=" + lat;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "KakaoAK " + appProperties.getKakao().getRestApiKey());
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        try {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode documents = root.get("documents");

            if (documents != null && documents.isArray()) {
                for (JsonNode doc : documents) {
                    // region_type이 "B"(법정동)인 항목의 code 앞 5자리 추출
                    String regionType = doc.has("region_type") ? doc.get("region_type").asText() : "";
                    if ("B".equals(regionType)) {
                        String code = doc.get("code").asText();
                        return code.substring(0, 5); // 시군구 코드 (앞 5자리)
                    }
                }
            }
        } catch (Exception e) {
            log.error("Kakao 행정구역 응답 파싱 오류: {}", e.getMessage());
        }

        return null;
    }
}
