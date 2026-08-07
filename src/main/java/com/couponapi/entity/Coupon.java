package com.couponapi.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long couponSeq;

    private Long issuerSeq;

    private String name;

    private BigDecimal price;

    private BigDecimal discountRate;

    private Integer validDays;

    private String status;  // OK, STOP, DEL

    private String issuerGoodsCode;

    private String customerGoodsCode;

    private String msgSmsTemplate;

    private String msgLmsTemplate;

    private String msgMmsTemplate;

    private String msgMmsImg;

    @CreationTimestamp
    @Column(name = "reg_date", updatable = false)
    private LocalDateTime regDate;

    @UpdateTimestamp
    private LocalDateTime modDate;

    @Builder
    public Coupon(String name, BigDecimal price, BigDecimal discountRate, Integer validDays, String status, String issuerGoodsCode, String customerGoodsCode, String msgSmsTemplate, String msgLmsTemplate, String msgMmsTemplate, String msgMmsImg) {
        this.name 			= name;
        this.price			= price;
        this.discountRate	= discountRate;
        this.validDays		= validDays;
        this.status = status != null ? status : "OK";
        this.issuerGoodsCode= issuerGoodsCode;
        this.customerGoodsCode= customerGoodsCode;
        this.msgSmsTemplate	= msgSmsTemplate;
        this.msgLmsTemplate	= msgLmsTemplate;
        this.msgMmsTemplate	= msgMmsTemplate;
        this.msgMmsImg		= msgMmsImg;
    }
}