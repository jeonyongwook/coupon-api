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

    // 주문 1건당 quantity(최대 1000)개까지 한 번에 생성되는 엔티티라 대량 insert가 잦다.
    // IDENTITY 채번은 Hibernate가 insert 배치를 아예 못 하게 막기 때문에(각 insert마다 PK를
    // 즉시 확인해야 함) SEQUENCE 채번(allocationSize=50, application.properties의
    // hibernate.jdbc.batch_size=50과 동일하게 맞춤)으로 바꿔 실제 배치 insert가 걸리도록 했다.
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_detail_seq_gen")
    @SequenceGenerator(name = "order_detail_seq_gen", sequenceName = "order_detail_seq", allocationSize = 50)
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
