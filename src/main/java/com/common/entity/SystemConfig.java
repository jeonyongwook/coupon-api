package com.common.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "system_config") // 시스템 전반의 설정을 담는 테이블
public class SystemConfig {

    @EmbeddedId
    private SystemConfigId id; // 그룹키와 설정키가 합쳐진 ID

    @Column(nullable = false)
    private String configValue;

    private String description;

    @CreationTimestamp
    @Column(name = "reg_date", updatable = false)
    private LocalDateTime regDate;

    @UpdateTimestamp
    private LocalDateTime modDate;

    @Builder
    public SystemConfig(String groupKey, String configKey, String configValue, String description) {
        this.id = new SystemConfigId(groupKey, configKey);
        this.configValue = configValue;
        this.description = description;
    }

}