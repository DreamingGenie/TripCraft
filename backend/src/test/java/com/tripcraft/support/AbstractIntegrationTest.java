package com.tripcraft.support;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/**
 * Testcontainers(실 MySQL 8) 기반 통합테스트 베이스.
 *
 * <p>{@code @Tag("integration")} 이므로 기본 {@code ./gradlew test} 에서는 제외되고
 * {@code ./gradlew integrationTest}(Docker 필요)에서만 실행된다.
 *
 * <p>스키마는 저장소 정본 {@code docs/sql/schema.sql} 을 컨테이너 initdb 로 그대로 올린다
 * (테스트 리소스로 복제하지 않아 드리프트 없음). 테스트 실행 작업 디렉터리는 {@code backend/} 이므로
 * 상대경로 {@code ../docs/sql/schema.sql} 로 해석된다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@Tag("integration")
public abstract class AbstractIntegrationTest {

    // 클래스 로드시 1회 기동해 하위 통합테스트가 컨테이너를 공유(기동 비용 절감).
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("tripcraft")
            .withUsername("tripcraft")
            .withPassword("tripcraft")
            .withCopyFileToContainer(
                    MountableFile.forHostPath("../docs/sql/schema.sql"),
                    "/docker-entrypoint-initdb.d/01-schema.sql");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }
}
