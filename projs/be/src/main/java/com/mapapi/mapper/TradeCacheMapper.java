package com.mapapi.mapper;

import com.mapapi.dto.TradeItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * apt_trade_cache 테이블 MyBatis Mapper 인터페이스.
 * SQL은 resources/mapper/TradeCacheMapper.xml에 정의한다.
 */
@Mapper
public interface TradeCacheMapper {

    /**
     * 캐시 히트 판정: 시군구+년월의 데이터 존재 여부 및 최종 조회 시각 확인
     */
    Map<String, Object> checkCacheStatus(@Param("sggCd") String sggCd,
                                          @Param("dealYmd") String dealYmd);

    /**
     * bounds 범위 내 거래 데이터 조회
     */
    List<TradeItem> findByBounds(@Param("sggCodes") List<String> sggCodes,
                                  @Param("dealYmd") String dealYmd,
                                  @Param("swLat") double swLat,
                                  @Param("neLat") double neLat,
                                  @Param("swLng") double swLng,
                                  @Param("neLng") double neLng);

    /**
     * 거래 캐시 일괄 저장
     */
    void insertBatch(@Param("items") List<TradeItem> items);

    /**
     * 캐시 갱신: 기존 데이터 삭제 (시군구+년월 단위)
     */
    void deleteBySggCdAndDealYmd(@Param("sggCd") String sggCd,
                                  @Param("dealYmd") String dealYmd);
}
