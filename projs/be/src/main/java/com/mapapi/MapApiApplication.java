package com.mapapi;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.mapapi.config.AppProperties;

/**
 * 카카오맵 아파트 실거래가 지도 — Spring Boot 메인 클래스
 */
@SpringBootApplication
@MapperScan("com.mapapi.mapper")
@EnableConfigurationProperties(AppProperties.class)
public class MapApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(MapApiApplication.class, args);
    }
}
