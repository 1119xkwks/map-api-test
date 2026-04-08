package com.mapapi.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * geocode_cache 테이블 MyBatis Mapper 인터페이스.
 * SQL은 resources/mapper/GeocodeCacheMapper.xml에 정의한다.
 */
@Mapper
public interface GeocodeCacheMapper {

    /**
     * 주소로 좌표 조회 (캐시 히트)
     */
    Map<String, Object> findByAddress(@Param("address") String address);

    /**
     * geocoding 결과 저장 (중복 시 무시)
     */
    void insertIfNotExists(@Param("address") String address,
                            @Param("lat") double lat,
                            @Param("lng") double lng);

    /**
     * 여러 주소를 한번에 조회 (일괄 geocoding 최적화)
     */
    List<Map<String, Object>> findByAddresses(@Param("addresses") List<String> addresses);
}
