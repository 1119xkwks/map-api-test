package com.mapapi.service;

import com.mapapi.dto.TradeItem;
import com.mapapi.config.AppProperties;
import com.mapapi.util.XmlParserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 아파트 매매 실거래가 데이터 조회 서비스.
 * 캐시 없이 매번 공공API 호출 → 파싱 → geocoding → 반환.
 */
@Service
public class TradeService {

    private static final Logger log = LoggerFactory.getLogger(TradeService.class);
    private static final String TRADE_API_URL = "http://apis.data.go.kr/1613000/RTMSDataSvcAptTradeDev/getRTMSDataSvcAptTradeDev";

    private final GeocodeService geocodeService;
    private final AppProperties appProperties;
    private final RestTemplate restTemplate;

    public TradeService(GeocodeService geocodeService,
                        AppProperties appProperties) {
        this.geocodeService = geocodeService;
        this.appProperties = appProperties;
        this.restTemplate = new RestTemplate();
    }

    /** 조회 결과 + 실제 조회된 년월 */
    public static class TradeResult {
        private final String dealYmd;
        private final List<TradeItem> items;

        public TradeResult(String dealYmd, List<TradeItem> items) {
            this.dealYmd = dealYmd;
            this.items = items;
        }

        public String getDealYmd() { return dealYmd; }
        public List<TradeItem> getItems() { return items; }
    }

    /**
     * 메인 조회: 최근 3개월 순차 조회, 데이터 있는 첫 번째 월 반환.
     */
    public TradeResult getTradeData(String sggCd, String umdNm,
                                     String addressPrefix, String dealYmd,
                                     double swLat, double swLng,
                                     double neLat, double neLng) {
        List<String> ymdsToTry = getRecentMonths(dealYmd, 3);
        log.info("[조회 시작] sggCd={}, 동={}, 조회월={}", sggCd, umdNm, ymdsToTry);

        // 최근 3개월 합산 조회. 최신월 우선, 같은 아파트(aptNm+지번)는 최신 거래만 유지
        List<TradeItem> merged = new ArrayList<>();
        Set<String> seenApts = new HashSet<>(); // "아파트명|지번" 중복 체크용
        String latestYmd = dealYmd;

        for (String ymd : ymdsToTry) {
            List<TradeItem> results = fetchTradeData(sggCd, ymd, umdNm, addressPrefix, swLat, swLng, neLat, neLng);

            if (results == null) {
                log.info("[{}월] 공공API에 데이터 없음 → 이전 월 시도", ymd);
                continue;
            }

            int addedCount = 0;
            for (TradeItem item : results) {
                // 같은 단지(아파트명+지번)는 최신월 데이터만 유지
                String key = (item.getAptNm() != null ? item.getAptNm().trim() : "")
                        + "|" + (item.getJibun() != null ? item.getJibun().trim() : "");
                if (!seenApts.contains(key)) {
                    seenApts.add(key);
                    merged.add(item);
                    addedCount++;
                }
            }

            log.info("[{}월] {}건 중 {}건 추가 (중복 단지 {}건 제외)",
                    ymd, results.size(), addedCount, results.size() - addedCount);

            if (!merged.isEmpty() && latestYmd.equals(dealYmd)) {
                latestYmd = ymd;
            }
        }

        log.info("[조회 완료] 최근 3개월 합산 {}건, 고유 단지 {}건", merged.size(), seenApts.size());
        return new TradeResult(latestYmd, merged);
    }

    /**
     * 공공API 호출 → 파싱 → 해당 동 필터 → geocoding → bounds 필터 → 반환.
     */
    private List<TradeItem> fetchTradeData(String sggCd, String dealYmd, String umdNm,
                                            String addressPrefix,
                                            double swLat, double swLng,
                                            double neLat, double neLng) {
        try {
            // 1. 공공API 호출
            String urlString = TRADE_API_URL
                    + "?serviceKey=" + appProperties.getDataGoKr().getServiceKey()
                    + "&LAWD_CD=" + sggCd
                    + "&DEAL_YMD=" + dealYmd
                    + "&pageNo=1"
                    + "&numOfRows=1000";

            URI uri = new URI(urlString);
            log.info("[공공API 요청] FULL URL: {}", urlString);

            ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
            String body = response.getBody();
            log.info("[공공API 응답] HTTP {}, bodyLen={}, body(1000자):\n{}",
                    response.getStatusCode(),
                    body != null ? body.length() : 0,
                    body != null ? body.substring(0, Math.min(1000, body.length())) : "null");

            if (body == null || body.isEmpty()) return null;

            // 2. XML 파싱
            List<TradeItem> allItems = XmlParserUtil.parseTradeXml(body);
            log.info("[파싱 완료] 전체 {}건", allItems.size());

            if (allItems.isEmpty()) return null; // 공공API에 데이터 없음 → 이전월 시도

            // 3. 해당 동만 필터링 ("문정동" vs "문정" 같은 차이 허용)
            // 먼저 공공API 데이터의 동 이름 샘플 로그
            Set<String> umdNameSet = allItems.stream()
                    .map(item -> item.getUmdNm() != null ? item.getUmdNm().trim() : "")
                    .collect(Collectors.toSet());
            log.info("[동 이름 비교] Kakao 동='{}', 공공API 동 목록={}", umdNm, umdNameSet);

            // "문정동" vs "문정" 양방향 매칭
            String umdNmNorm = umdNm.replace("동", "").replace("읍", "").replace("면", "").trim();
            List<TradeItem> filtered = allItems.stream()
                    .filter(item -> {
                        String itemUmd = item.getUmdNm() != null ? item.getUmdNm().trim() : "";
                        String itemUmdNorm = itemUmd.replace("동", "").replace("읍", "").replace("면", "").trim();
                        return umdNm.equals(itemUmd)
                                || umdNmNorm.equals(itemUmdNorm)
                                || umdNm.startsWith(itemUmd)
                                || itemUmd.startsWith(umdNm);
                    })
                    .collect(Collectors.toList());

            log.info("[동 필터] Kakao='{}', norm='{}', 매칭 {}건 / 전체 {}건",
                    umdNm, umdNmNorm, filtered.size(), allItems.size());

            if (filtered.isEmpty()) return List.of();

            // 4. dealAmount trim, sggCd 설정
            for (TradeItem item : filtered) {
                if (item.getSggCd() == null || item.getSggCd().isEmpty()) item.setSggCd(sggCd);
                item.setDealYmd(dealYmd);
                if (item.getDealAmount() != null) item.setDealAmount(item.getDealAmount().trim());
            }

            // 5. geocoding (도로명 주소 기반, 고유 주소 단위)
            geocodeService.geocodeTradeItems(filtered, addressPrefix);

            // 6. 좌표 있는 것만 + bounds 필터
            List<TradeItem> results = filtered.stream()
                    .filter(item -> item.getLat() != null && item.getLng() != null
                            && item.getLat() != 0.0 && item.getLng() != 0.0)
                    .filter(item -> item.getLat() >= swLat && item.getLat() <= neLat
                            && item.getLng() >= swLng && item.getLng() <= neLng)
                    .collect(Collectors.toList());

            // 7. dealDate 생성
            for (TradeItem item : results) {
                item.buildDealDate();
            }

            log.info("[최종] geocoding 후 bounds 내 {}건", results.size());
            return results;

        } catch (org.springframework.web.client.RestClientException e) {
            log.error("[API 호출 실패] {}", e.getMessage());
            throw new RuntimeException("공공API 호출 실패: " + e.getMessage(), e);
        } catch (XmlParserUtil.ApiResponseException e) {
            log.error("[API 응답 에러] code={}, msg={}", e.getResultCode(), e.getResultMsg());
            throw e;
        } catch (XmlParserUtil.XmlParseException e) {
            log.error("[XML 파싱 실패] {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("[기타 오류] {}", e.getMessage(), e);
            throw new RuntimeException("데이터 처리 중 오류: " + e.getMessage(), e);
        }
    }

    private List<String> getRecentMonths(String dealYmd, int months) {
        List<String> result = new ArrayList<>();
        int year = Integer.parseInt(dealYmd.substring(0, 4));
        int month = Integer.parseInt(dealYmd.substring(4, 6));
        for (int i = 0; i < months; i++) {
            result.add(String.format("%d%02d", year, month));
            month--;
            if (month < 1) { month = 12; year--; }
        }
        return result;
    }
}
