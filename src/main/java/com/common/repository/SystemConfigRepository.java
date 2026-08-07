package com.common.repository;

import com.common.entity.SystemConfig;
import com.common.entity.SystemConfigId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SystemConfigRepository extends JpaRepository<SystemConfig, SystemConfigId> {

    // 1. groupKey로 전체 리스트 가져오기
    // 필드 경로: id(SystemConfigId) -> groupKey
    List<SystemConfig> findById_GroupKey(String groupKey);

    // 2. groupKey와 configKey 조합으로 특정 설정 하나만 가져오기
    // 필드 경로: id -> groupKey AND id -> configKey
    Optional<SystemConfig> findById_GroupKeyAndId_ConfigKey(String groupKey, String configKey);
}