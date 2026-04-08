package com.mapapi.service;

import com.mapapi.dto.TradeItem;
import com.mapapi.mapper.TradeCacheMapper;
import com.mapapi.config.AppProperties;
import com.mapapi.util.XmlParserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 아파트 매매 실거래가 데이터 조회 서비스.
 * 캐시 확인 -> 캐시 미스 시 공공API 호출 -> XML 파싱 -> Geocoding -> DB 저장
 */
@Service
public class TradeService {

    private static final Logger log = LoggerFactory.getLogger(TradeService.class);
    private static final String TRADE_API_URL = "http://apis.data.go.kr/1613000/RTMSDataSvcAptTradeDev/getRTMSDataSvcAptTradeDev";

    private final TradeCacheMapper tradeCacheMapper;
    private final GeocodeService geocodeService;
    private final AppProperties appProperties;
    private final RestTemplate restTemplate;

    public TradeService(TradeCacheMapper tradeCacheMapper,
                        GeocodeService geocodeService,
                        AppProperties appProperties) {
        this.tradeCacheMapper = tradeCacheMapper;
        this.geocodeService = geocodeService;
        this.appProperties = appProperties;
        this.restTemplate = new RestTemplate();
    }

    /**
     * 시군구 코드 목록과 계약년월, bounds를 기반으로 거래 데이터를 조회한다.
     * 각 시군구 코드에 대해 캐시를 확인하고, 캐시 미스/만료 시 공공API를 호출한다.
     *
     * @param sggCodes 시군구 코드 리스트
     * @param dealYmd  계약년월 (YYYYMM)
     * @param swLat    남서 위도
     * @param swLng    남서 경도
     * @param neLat    북동 위도
     * @param neLng    북동 경도
     * @return bounds 범위 내 거래 데이터 리스트
     */
    public List<TradeItem> getTradeData(List<String> sggCodes, String dealYmd,
                                         double swLat, double swLng,
                                         double neLat, double neLng) {
        // 각 시군구 코드에 대해 캐시 상태 확인 및 필요 시 데이터 로드
        for (String sggCd : sggCodes) {
            ensureCacheLoaded(sggCd, dealYmd);
        }

        // bounds 범위 내 데이터 조회
        List<TradeItem> results = tradeCacheMapper.findByBounds(
                sggCodes, dealYmd, swLat, neLat, swLng, neLng);

        // dealDate 필드 생성
        for (TradeItem item : results) {
            item.buildDealDate();
        }

        log.info("조회 결과: sggCodes={}, dealYmd={}, count={}", sggCodes, dealYmd, results.size());
        return results;
    }

    /**
     * 특정 시군구+년월의 캐시가 유효한지 확인하고, 필요 시 공공API에서 로드한다.
     *
     * @param sggCd   시군구 코드
     * @param dealYmd 계약년월
     */
    private void ensureCacheLoaded(String sggCd, String dealYmd) {
        Map<String, Object> status = tradeCacheMapper.checkCacheStatus(sggCd, dealYmd);

        long cnt = 0;
        Timestamp lastFetched = null;

        if (status != null) {
            Object cntObj = status.get("cnt");
            if (cntObj instanceof Number) {
                cnt = ((Number) cntObj).longValue();
            }
            Object fetchedObj = status.get("last_fetched");
            if (fetchedObj instanceof Timestamp) {
                lastFetched = (Timestamp) fetchedObj;
            }
        }

        if (cnt == 0) {
            // 캐시 미스: 공공API 호출
            log.info("캐시 미스: sggCd={}, dealYmd={}", sggCd, dealYmd);
            fetchAndCacheTradeData(sggCd, dealYmd);
            return;
        }

        // 현재 월 데이터인 경우 캐시 만료 확인
        String currentYmd = getCurrentYmd();
        if (dealYmd.equals(currentYmd) && lastFetched != null) {
            LocalDateTime fetchedTime = lastFetched.toLocalDateTime();
            LocalDateTime todayStart = LocalDate.now().atStartOfDay();

            if (fetchedTime.isBefore(todayStart)) {
                // 캐시 만료: 기존 삭제 후 재호출
                log.info("캐시 만료 (현재 월, 오늘 이전 데이터): sggCd={}, dealYmd={}", sggCd, dealYmd);
                tradeCacheMapper.deleteBySggCdAndDealYmd(sggCd, dealYmd);
                fetchAndCacheTradeData(sggCd, dealYmd);
                return;
            }
        }

        // 캐시 히트
        log.debug("캐시 히트: sggCd={}, dealYmd={}, cnt={}", sggCd, dealYmd, cnt);
    }

    /**
     * 공공데이터 포털 API를 호출하여 실거래가 데이터를 가져오고 DB에 캐싱한다.
     *
     * @param sggCd   시군구 코드
     * @param dealYmd 계약년월
     */
    private void fetchAndCacheTradeData(String sggCd, String dealYmd) {
        try {
            // serviceKey는 이미 인코딩된 상태일 수 있으므로 URI 객체를 직접 생성하여 이중 인코딩 방지
            String urlString = TRADE_API_URL
                    + "?serviceKey=" + appProperties.getDataGoKr().getServiceKey()
                    + "&LAWD_CD=" + sggCd
                    + "&DEAL_YMD=" + dealYmd
                    + "&pageNo=1"
                    + "&numOfRows=1000";

            URI uri = new URI(urlString);
            ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);

            if (response.getBody() == null || response.getBody().isEmpty()) {
                log.warn("공공API 응답이 비어있음: sggCd={}, dealYmd={}", sggCd, dealYmd);
                return;
            }

            // XML 파싱
            List<TradeItem> items = XmlParserUtil.parseTradeXml(response.getBody());
            log.info("공공API 파싱 결과: sggCd={}, dealYmd={}, items={}", sggCd, dealYmd, items.size());

            if (items.isEmpty()) {
                return;
            }

            // 각 항목에 sggCd, dealYmd 설정
            for (TradeItem item : items) {
                if (item.getSggCd() == null || item.getSggCd().isEmpty()) {
                    item.setSggCd(sggCd);
                }
                item.setDealYmd(dealYmd);
                // dealAmount 공백 제거
                if (item.getDealAmount() != null) {
                    item.setDealAmount(item.getDealAmount().trim());
                }
            }

            // Geocoding
            geocodeService.geocodeTradeItems(items, sggCd);

            // DB 저장 (배치 크기 제한: 500건씩)
            int batchSize = 500;
            for (int i = 0; i < items.size(); i += batchSize) {
                int end = Math.min(i + batchSize, items.size());
                List<TradeItem> batch = items.subList(i, end);
                tradeCacheMapper.insertBatch(batch);
            }

            log.info("캐시 저장 완료: sggCd={}, dealYmd={}, total={}", sggCd, dealYmd, items.size());

        } catch (Exception e) {
            log.error("공공API 호출/파싱/저장 오류: sggCd={}, dealYmd={}, error={}", sggCd, dealYmd, e.getMessage(), e);
            throw new RuntimeException("공공데이터 API 호출에 실패했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 현재 년월(YYYYMM) 문자열을 반환한다.
     */
    private String getCurrentYmd() {
        LocalDate now = LocalDate.now();
        return String.format("%d%02d", now.getYear(), now.getMonthValue());
    }
}
