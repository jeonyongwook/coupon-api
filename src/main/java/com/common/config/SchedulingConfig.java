package com.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄러(배치) 활성화. 테스트에서는 app.scheduling.enabled=false로 꺼서
 * 배치를 테스트 코드가 직접 호출하며 결정적으로 검증할 수 있게 한다.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
