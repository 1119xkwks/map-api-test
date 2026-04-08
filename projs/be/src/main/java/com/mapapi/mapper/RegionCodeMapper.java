package com.mapapi.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * region_code 테이블 조회용 MyBatis Mapper 인터페이스.
 * 시군구 코드로 시군구 명을 조회한다.
 */
@Mapper
public interface RegionCodeMapper {

    /**
     * 시군구 코드로 시군구 명 조회
     * @param code 시군구코드 (5자리)
     * @return 시군구명 (예: "서울특별시 강남구")
     */
    String findNameByCode(@Param("code") String code);
}
