package com.common.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;

@Slf4j
@Configuration
@EnableAsync // 비동기 기능을 활성화합니다.
@RequiredArgsConstructor
public class AsyncConfig {

    private final SystemConfigService systemConfigService;

    private static final String CONFIG_GROUP = "BATCH_COUPON_ISSUE";
    private static final int DEFAULT_CORE_POOL_SIZE = 5;
    private static final int DEFAULT_MAX_POOL_SIZE = 10;

    @Bean(name = "couponExecutor")
    public ThreadPoolTaskExecutor couponExecutor() {
        // system_config(BATCH_COUPON_ISSUE) 값으로 풀 크기 결정, 값이 없으면 기본값 사용
        Map<String, String> config = systemConfigService.getConfigMap(CONFIG_GROUP);

        int corePoolSize = systemConfigService.getIntOrDefault(config, "CORE_POOL_SIZE", DEFAULT_CORE_POOL_SIZE);
        int maxPoolSize = systemConfigService.getIntOrDefault(config, "MAX_POOL_SIZE", DEFAULT_MAX_POOL_SIZE);

        if (maxPoolSize < corePoolSize) {
            log.warn("MAX_POOL_SIZE({})가 CORE_POOL_SIZE({})보다 작아 MAX_POOL_SIZE를 CORE_POOL_SIZE로 맞춥니다.", maxPoolSize, corePoolSize);
            maxPoolSize = corePoolSize;
        }

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 1. 기본적으로 유지할 쓰레드 수 (평상시 동시 처리량) - system_config.CORE_POOL_SIZE
        executor.setCorePoolSize(corePoolSize);

        // 2. 최대 생성 가능한 쓰레드 수 - system_config.MAX_POOL_SIZE
        //    주의: ThreadPoolExecutor는 core가 다 찼을 때 먼저 "큐"에 쌓고, 큐가 가득 찬 뒤에야
        //    core를 넘어 max까지 스레드를 늘린다. 배치는 1회차에 ISSUE_TRY_COUNT건만 제출하므로
        //    (큐 크기 100보다 작으면) 평소에는 core 크기가 곧 동시 처리량이고 max는 거의 쓰이지 않는다.
        executor.setMaxPoolSize(maxPoolSize);

        // 3. 작업 대기 큐 크기 (메모리 상황에 맞게 조절)
        executor.setQueueCapacity(100);

        // 4. 로그에서 식별하기 편하게 이름 지정
        executor.setThreadNamePrefix("Coupon-Thread-");

        // 5. 서버 종료 시 진행 중인 작업 완료 후 종료
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();

        log.info("couponExecutor 초기화 완료: corePoolSize={}, maxPoolSize={}", corePoolSize, maxPoolSize);

        return executor;
    }
}