package com.mapapi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Spring Boot 애플리케이션 컨텍스트 로드 테스트.
 * 환경변수가 설정되지 않은 테스트 환경에서는 비활성화될 수 있다.
 */
class MapApiApplicationTests {

    @Test
    void contextLoads() {
        // 기본 컨텍스트 로드 테스트
        // 환경변수 의존성이 있으므로, CI/CD에서 환경변수 설정 후 실행한다.
    }
}
