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

/**
 * 좌표 → 시군구 코드 + 동 이름 변환 서비스.
 * 지도 중심 좌표 1개로 해당 지역의 시군구 코드와 법정동 이름을 조회한다.
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

    /** 조회 결과: 시군구 코드 + 시도명 + 시군구명 + 동 이름 */
    public static class RegionInfo {
        private final String sggCd;
        private final String sidoName;   // 예: "서울특별시"
        private final String sggName;    // 예: "송파구"
        private final String umdNm;      // 예: "문정동"

        public RegionInfo(String sggCd, String sidoName, String sggName, String umdNm) {
            this.sggCd = sggCd;
            this.sidoName = sidoName;
            this.sggName = sggName;
            this.umdNm = umdNm;
        }

        public String getSggCd() { return sggCd; }
        public String getSidoName() { return sidoName; }
        public String getSggName() { return sggName; }
        public String getUmdNm() { return umdNm; }

        /** 도로명 주소 prefix: "서울특별시 송파구" */
        public String getAddressPrefix() {
            return sidoName + " " + sggName;
        }

        @Override
        public String toString() {
            return sggCd + "(" + sidoName + " " + sggName + " " + umdNm + ")";
        }
    }

    /**
     * 지도 중심 좌표 1개로 시군구 코드 + 동 이름을 조회한다.
     *
     * @return RegionInfo 또는 null
     */
    public RegionInfo getRegionInfo(double lat, double lng) {
        String url = KAKAO_REGION_URL + "?x=" + lng + "&y=" + lat;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "KakaoAK " + appProperties.getKakao().getRestApiKey());
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode documents = root.get("documents");

            if (documents != null && documents.isArray()) {
                for (JsonNode doc : documents) {
                    if ("B".equals(doc.path("region_type").asText())) {
                        String code = doc.get("code").asText();
                        String sggCd = code.substring(0, 5);
                        String sidoName = doc.path("region_1depth_name").asText("").trim();
                        String sggName = doc.path("region_2depth_name").asText("").trim();
                        String umdNm = doc.path("region_3depth_name").asText("").trim();

                        log.info("[지역 조회] 중심좌표({},{}) → {} {} {} (sggCd={})",
                                lat, lng, sidoName, sggName, umdNm, sggCd);
                        return new RegionInfo(sggCd, sidoName, sggName, umdNm);
                    }
                }
            }
        } catch (Exception e) {
            log.error("좌표→행정구역 변환 실패: lat={}, lng={}, error={}", lat, lng, e.getMessage());
        }

        return null;
    }
}
