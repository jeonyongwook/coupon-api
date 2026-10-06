package com.couponapi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** JDBC 배치 ON: batch_size=50, order_inserts, rewriteBatchedStatements=true (운영 설정과 동일). */
@Tag("benchmark")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext
class OrderInsertBatchOnBenchmark extends OrderInsertBenchmarkSupport {

    @Container
    static final MariaDBContainer<?> MARIADB = new MariaDBContainer<>("mariadb:10.11");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String url = MARIADB.getJdbcUrl();
        registry.add("spring.datasource.url", () -> url + (url.contains("?") ? "&" : "?") + "rewriteBatchedStatements=true");
        registry.add("spring.datasource.username", MARIADB::getUsername);
        registry.add("spring.datasource.password", MARIADB::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.mariadb.jdbc.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MariaDBDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.jdbc.batch_size", () -> "50");
        registry.add("spring.jpa.properties.hibernate.order_inserts", () -> "true");
    }

    @Test
    @DisplayName("주문 접수 1,000건 상세 INSERT - JDBC 배치 ON")
    void measure() {
        runBenchmark("BATCH_ON");
    }
}
