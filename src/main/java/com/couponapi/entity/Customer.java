package com.couponapi.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long customerSeq;

    @Column(unique = true, nullable = false, length = 20)
    private String customerKey;

    // API 시크릿 키는 평문으로 저장하지 않고 SHA-256 해시(hex 64자)만 저장한다.
    // 시크릿은 서버가 무작위로 발급하는 고엔트로피 값이라 BCrypt처럼 느린 해시까지는 필요 없고,
    // DB가 유출돼도 원본 키를 알 수 없게 하는 것이 목적이다. (비교는 ApiKeyHasher + 상수 시간 비교)
    @Column(nullable = false, length = 64)
    private String secretKeyHash;

    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UseStatus status;

    @CreationTimestamp
    @Column(name = "reg_date", updatable = false)
    private LocalDateTime regDate;

    @UpdateTimestamp
    private LocalDateTime modDate;

    @Builder
    public Customer(String customerKey, String secretKeyHash, String name, UseStatus status) {
        this.customerKey = customerKey;
        this.secretKeyHash = secretKeyHash;
        this.name = name;
        this.status = status != null ? status : UseStatus.OK;
    }
}
