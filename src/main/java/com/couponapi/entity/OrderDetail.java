package com.couponapi.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "order_detail", indexes = {
        @Index(name = "idx_order_seq", columnList = "orderSeq"),
        @Index(name = "idx_reg_date", columnList = "regDate")
})
public class OrderDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long orderDetailSeq;

    @Column(nullable = false)
    private Long orderSeq;

    @Column(unique = true)
    private String pin;

    @Column(unique = true)
    private String issuerTrxId;

    private LocalDate validStartDate;

    private LocalDate validEndDate;

    private String status;  // READY, UNUSED, USED, PART-USED, EXPIRED, CANCELED, ISSUE_FAIL

    @CreationTimestamp
    @Column(name = "reg_date", updatable = false)
    private LocalDateTime regDate;

    @UpdateTimestamp
    private LocalDateTime modDate;

    @Builder
    public OrderDetail(Long orderSeq, String pin, String issuerTrxId, LocalDate validStartDate, LocalDate validEndDate, String status) {
        this.orderSeq		= orderSeq;
        this.pin			= pin;
        this.issuerTrxId	= issuerTrxId;
        this.validStartDate	= validStartDate;
        this.validEndDate	= validEndDate;
        this.status = status != null ? status : "READY";
    }

    /**
     * 쿠폰 발행 성공 처리
     */
    public void issueSuccess(String pin, String issuerTrxId, LocalDate validStartDate, LocalDate validEndDate) {
        this.pin = pin;
        this.issuerTrxId = issuerTrxId;
        this.validStartDate = validStartDate;
        this.validEndDate = validEndDate;
        this.status = "UNUSED";
    }

    /**
     * 쿠폰 발행 실패 처리
     */
    public void issueFail() {
        this.status = "ISSUE_FAIL";
    }
}