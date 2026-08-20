package com.couponapi.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Issuer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long issuerSeq;

    private String name;

    @Column(length = 30)
    private String businessNo;

    private String status;  // OK, STOP, DEL

    @CreationTimestamp
    @Column(name = "reg_date", updatable = false)
    private LocalDateTime regDate;

    @UpdateTimestamp
    private LocalDateTime modDate;

    @Builder
    public Issuer(String name, String businessNo, String status) {
        this.name = name;
        this.businessNo = businessNo;
        this.status = status != null ? status : "OK";
    }
}
