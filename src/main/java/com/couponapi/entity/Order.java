package com.couponapi.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "orders", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_customer_trx_id",
                columnNames = {"customerSeq", "customerTrxId"}
        )
}, indexes = {
        @Index(name = "idx_coupon_seq", columnList = "couponSeq"),
        @Index(name = "idx_reg_date", columnList = "regDate")
})
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long orderSeq;

    @Column(nullable = false)
    private Long customerSeq;

    @Column(nullable = false, length = 30)
    private String customerTrxId;

    @Column(unique = true, nullable = false)
    private String trxId;

    @Column(nullable = false)
    private Long couponSeq;

    private Integer quantity;

    private String status;  // READY, COMPLETED

    @Column(length = 100)
    private String msgSubject;

    @Column(length = 100)
    private String msgAddContent;

    @CreationTimestamp
    @Column(name = "reg_date", updatable = false)
    private LocalDateTime regDate;

    @UpdateTimestamp
    private LocalDateTime modDate;

    @Builder
    public Order(Long customerSeq, String customerTrxId, String trxId,
                 Long couponSeq, int quantity, String status,
                 String msgSubject, String msgAddContent) {
        this.customerSeq = customerSeq;
        this.customerTrxId = customerTrxId;
        this.trxId = trxId;
        this.couponSeq = couponSeq;
        this.quantity = quantity;
        this.status = status != null ? status : "READY";
        this.msgSubject = msgSubject;
        this.msgAddContent = msgAddContent;
    }
}