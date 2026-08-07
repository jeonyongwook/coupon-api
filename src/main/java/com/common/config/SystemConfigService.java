package com.common.config;

import com.common.entity.SystemConfig;
import com.common.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * system_config 테이블의 설정값을 조회하는 공통 컴포넌트.
 * 배치(CouponIssueBatch), 스레드풀 구성(AsyncConfig) 등에서 공용으로 사용한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemConfigService {

    private final SystemConfigRepository systemConfigRepository;

    /**
     * groupKey에 속한 설정을 Map<configKey, configValue> 형태로 조회
     */
    public Map<String, String> getConfigMap(String groupKey) {
        List<SystemConfig> configList = systemConfigRepository.findById_GroupKey(groupKey);

        return configList.stream()
                .collect(Collectors.toMap(
                        config -> config.getId().getConfigKey(), // Key
                        SystemConfig::getConfigValue,            // Value
                        (existing, replacement) -> existing      // 중복 키 발생 시 기존 값 유지
                ));
    }

    /**
     * 설정값을 int로 파싱, 없거나 파싱 실패 시 기본값 사용
     */
    public int getIntOrDefault(Map<String, String> config, String key, int defaultValue) {
        String value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("설정값 파싱 실패, 기본값 사용: key={}, value={}, default={}", key, value, defaultValue);
            return defaultValue;
        }
    }
}
