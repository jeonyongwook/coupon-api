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

    // customerKey(식별자)와 쌍으로 쓰이는 인증용 시크릿.
    // 요청 헤더(X-API-KEY)로 전달받아 검증한다 - customerKey는 body에 평문으로 오가는 식별자일 뿐이라
    // 그것만으로 인증하면 그대로 도용될 수 있기 때문에 별도 비밀값을 둔다.
    // TODO: 운영 반영 시에는 평문 저장 대신 해시(BCrypt 등)로 저장하고, 발급 시점에만 평문을 노출하는 방식으로 전환 권장.
    @Column(nullable = false, length = 64)
    private String secretKey;

    private String name;

    private String status;  // OK, STOP, DEL

    @CreationTimestamp
    @Column(name = "reg_date", updatable = false)
    private LocalDateTime regDate;

    @UpdateTimestamp
    private LocalDateTime modDate;

    @Builder
    public Customer(String customerKey, String secretKey, String name, String status) {
        this.customerKey = customerKey;
        this.secretKey = secretKey;
        this.name = name;
        this.status = status != null ? status : "OK";
    }
}
