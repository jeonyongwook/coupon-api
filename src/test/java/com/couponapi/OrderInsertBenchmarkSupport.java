package com.couponapi;

import com.couponapi.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 주문 접수(상세 1,000건 INSERT 포함) 소요 시간 측정 공통 로직.
 * 설정(JDBC 배치 on/off)만 다른 구체 클래스에서 호출한다. 기본 test 태스크에서는 제외되며
 * `./gradlew benchmark`로 실행한다. (Docker 필요)
 */
abstract class OrderInsertBenchmarkSupport extends IntegrationTestSupport {

    private static final int QUANTITY = 1000;
    private static final int WARMUP_RUNS = 2;
    private static final int MEASURED_RUNS = 5;

    @Autowired OrderService orderService;

    protected void runBenchmark(String label) {
        for (int i = 0; i < WARMUP_RUNS; i++) {
            orderService.createOrder(request("BM-" + label + "-W" + i, QUANTITY), API_KEY);
        }

        long[] millis = new long[MEASURED_RUNS];
        for (int i = 0; i < MEASURED_RUNS; i++) {
            long start = System.nanoTime();
            orderService.createOrder(request("BM-" + label + "-R" + i, QUANTITY), API_KEY);
            millis[i] = (System.nanoTime() - start) / 1_000_000;
        }

        long[] sorted = millis.clone();
        Arrays.sort(sorted);
        System.out.printf("[BENCHMARK] %s | quantity=%d | runs=%d | median=%dms | min=%dms | max=%dms | all=%s%n",
                label, QUANTITY, MEASURED_RUNS, sorted[MEASURED_RUNS / 2], sorted[0], sorted[MEASURED_RUNS - 1],
                Arrays.toString(millis));

        // 측정 대상이 실제로 저장됐는지만 확인한다 (속도에 대한 단언은 하지 않는다).
        assertThat(orderDetailRepository.count()).isEqualTo((long) (WARMUP_RUNS + MEASURED_RUNS) * QUANTITY);
    }
}
