package com.common.entity;

import jakarta.persistence.Embeddable;
import lombok.*;
import java.io.Serializable;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@EqualsAndHashCode // 복합키는 두 객체가 같은지 비교할 수 있어야 하므로 필수입니다.
public class SystemConfigId implements Serializable {
    private String groupKey;
    private String configKey;
}