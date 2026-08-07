package com.common.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * system_config(BATCH_COUPON_ISSUE)의 CORE_POOL_SIZE / MAX_POOL_SIZE 값을
 * 주기적으로 다시 읽어, 서버 재시작 없이 couponExecutor 풀 크기에 반영한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponExecutorConfigRefresher {

    private final SystemConfigService systemConfigService;

    @Qualifier("couponExecutor")
    private final ThreadPoolTaskExecutor couponExecutor;

    private static final String CONFIG_GROUP = "BATCH_COUPON_ISSUE";
    private static final int DEFAULT_CORE_POOL_SIZE = 5;
    private static final int DEFAULT_MAX_POOL_SIZE = 10;

    // 1분마다 설정값을 다시 확인 (값이 실제로 바뀐 경우에만 적용)
    @Scheduled(fixedDelay = 60000)
    public void refresh() {
        Map<String, String> config = systemConfigService.getConfigMap(CONFIG_GROUP);

        int newCore = systemConfigService.getIntOrDefault(config, "CORE_POOL_SIZE", DEFAULT_CORE_POOL_SIZE);
        int newMax = systemConfigService.getIntOrDefault(config, "MAX_POOL_SIZE", DEFAULT_MAX_POOL_SIZE);

        if (newMax < newCore) {
            log.warn("MAX_POOL_SIZE({})가 CORE_POOL_SIZE({})보다 작아 MAX_POOL_SIZE를 CORE_POOL_SIZE로 맞춥니다.", newMax, newCore);
            newMax = newCore;
        }

        int currentCore = couponExecutor.getCorePoolSize();
        int currentMax = couponExecutor.getMaxPoolSize();

        if (newCore == currentCore && newMax == currentMax) {
            return; // 변경 없음
        }

        applyPoolSize(newCore, newMax, currentMax);

        log.info("couponExecutor 풀 크기 갱신: corePoolSize {} -> {}, maxPoolSize {} -> {}",
                currentCore, newCore, currentMax, newMax);
    }

    /**
     * ThreadPoolExecutor는 내부적으로 core <= max 관계가 항상 유지되어야 하므로
     * (위반 시 IllegalArgumentException), 늘릴 때는 max를 먼저, 줄일 때는 core를 먼저 적용한다.
     */
    private void applyPoolSize(int newCore, int newMax, int currentMax) {
        if (newCore > currentMax) {
            couponExecutor.setMaxPoolSize(newMax);
            couponExecutor.setCorePoolSize(newCore);
        } else {
            couponExecutor.setCorePoolSize(newCore);
            couponExecutor.setMaxPoolSize(newMax);
        }
    }
}
