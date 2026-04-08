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

    /** 보이는 동 중 lat/lng이 NULL인 항목 조회 (추가 geocoding용) */
    List<TradeItem> findNullLatByUmdNames(@Param("sggCd") String sggCd,
                                           @Param("dealYmd") String dealYmd,
                                           @Param("umdNames") List<String> umdNames);

    /** geocoding 결과로 lat/lng 업데이트 */
    void updateLatLng(@Param("id") int id, @Param("lat") double lat, @Param("lng") double lng);

    /** 진단용: 시군구+년월 전체 건수 */
    int countBySggCodesAndDealYmd(@Param("sggCodes") List<String> sggCodes,
                                   @Param("dealYmd") String dealYmd);

    /** 진단용: lat/lng이 NULL인 건수 */
    int countNullLatBySggCodesAndDealYmd(@Param("sggCodes") List<String> sggCodes,
                                          @Param("dealYmd") String dealYmd);
}
