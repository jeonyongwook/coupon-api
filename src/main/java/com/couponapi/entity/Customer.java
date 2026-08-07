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

    private String name;

    private String status;  // OK, STOP, DEL

    @CreationTimestamp
    @Column(name = "reg_date", updatable = false)
    private LocalDateTime regDate;

    @UpdateTimestamp
    private LocalDateTime modDate;

    @Builder
    public Customer(String customerKey, String name, String status) {
        this.customerKey = customerKey;
        this.name = name;
        this.status = status != null ? status : "OK";
    }
}