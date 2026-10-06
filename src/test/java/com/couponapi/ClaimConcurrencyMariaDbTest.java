package com.couponapi;

import com.couponapi.batch.CouponIssueClaimer;
import com.couponapi.entity.OrderDetail;
import com.couponapi.entity.OrderDetailStatus;
import com.couponapi.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 MariaDB(SELECT ... FOR UPDATE SKIP LOCKED)에서 동시 선점이 겹치지 않는지 검증한다.
 *
 * 서버를 여러 대 띄운 상황은 "DB 입장에서 서로 다른 커넥션이 동시에 같은 행을 선점하려는 것"과 같으므로,
 * 여러 스레드가 각자 별도 트랜잭션(=별도 커넥션)으로 claim()을 동시에 호출해 이를 재현한다.
 * (H2에서는 이 동작을 MariaDB와 동일하다고 보장할 수 없어 기본 테스트와 분리했다)
 *
 * Docker가 없는 환경에서는 이 테스트만 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext
class ClaimConcurrencyMariaDbTest extends IntegrationTestSupport {

    @Container
    static final MariaDBContainer<?> MARIADB = new MariaDBContainer<>("mariadb:10.11");

    @DynamicPropertySource
    static void mariaDbProperties(DynamicPropertyRegistry registry) {
        String url = MARIADB.getJdbcUrl();
        registry.add("spring.datasource.url", () -> url + (url.contains("?") ? "&" : "?") + "rewriteBatchedStatements=true");
        registry.add("spring.datasource.username", MARIADB::getUsername);
        registry.add("spring.datasource.password", MARIADB::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.mariadb.jdbc.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MariaDBDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired OrderService orderService;
    @Autowired CouponIssueClaimer claimer;

    @Test
    @DisplayName("여러 커넥션이 동시에 선점해도 같은 건을 두 번 가져가지 않는다 (SKIP LOCKED)")
    void concurrentClaimsNeverOverlap() throws Exception {
        int total = 300;
        int threads = 4;
        int limit = 100;
        orderService.createOrder(request("CC-001", total), API_KEY);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);

        List<Future<List<Long>>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit((Callable<List<Long>>) () -> {
                ready.countDown();
                go.await(); // 모든 스레드가 준비된 뒤 동시에 출발
                return claimer.claim(limit);
            }));
        }
        ready.await();
        go.countDown();

        List<Long> claimed = new ArrayList<>();
        for (Future<List<Long>> future : futures) {
            claimed.addAll(future.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();

        // 동시 구간에서 가져간 건끼리는 절대 겹치지 않아야 한다.
        assertThat(claimed).doesNotHaveDuplicates();

        // 남은 건도 마저 선점하면 전체가 정확히 한 번씩만 선점된다 (유실 없음).
        List<Long> rest;
        while (!(rest = claimer.claim(limit)).isEmpty()) {
            claimed.addAll(rest);
        }
        assertThat(claimed).hasSize(total).doesNotHaveDuplicates();

        List<OrderDetail> details = orderDetailRepository.findAll();
        assertThat(details).hasSize(total);
        assertThat(details).allSatisfy(d -> assertThat(d.getStatus()).isEqualTo(OrderDetailStatus.PROCESSING));
    }
}
