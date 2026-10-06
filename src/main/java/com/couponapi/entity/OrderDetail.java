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
        @Index(name = "idx_order_detail_order_seq", columnList = "orderSeq"),
        @Index(name = "idx_order_detail_reg_date", columnList = "regDate"),
        // 배치가 status=READY 건을 오래된 순으로 가져오는 쿼리를 위한 복합 인덱스
        @Index(name = "idx_order_detail_status_reg", columnList = "status, regDate")
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderDetailStatus status;

    @CreationTimestamp
    @Column(name = "reg_date", updatable = false)
    private LocalDateTime regDate;

    @UpdateTimestamp
    private LocalDateTime modDate;

    @Builder
    public OrderDetail(Long orderSeq, String pin, String issuerTrxId, LocalDate validStartDate,
                       LocalDate validEndDate, OrderDetailStatus status) {
        this.orderSeq = orderSeq;
        this.pin = pin;
        this.issuerTrxId = issuerTrxId;
        this.validStartDate = validStartDate;
        this.validEndDate = validEndDate;
        this.status = status != null ? status : OrderDetailStatus.READY;
    }

    /** 배치가 이 건을 선점했음을 표시 (발행처 요청 직전). */
    public void markProcessing() {
        this.status = OrderDetailStatus.PROCESSING;
    }

    /** 발행처에 요청을 보내지 못한 경우 등, 다시 대기 상태로 되돌린다. */
    public void releaseToReady() {
        this.status = OrderDetailStatus.READY;
    }

    /** 쿠폰 발행 성공 처리 */
    public void issueSuccess(String pin, String issuerTrxId, LocalDate validStartDate, LocalDate validEndDate) {
        this.pin = pin;
        this.issuerTrxId = issuerTrxId;
        this.validStartDate = validStartDate;
        this.validEndDate = validEndDate;
        this.status = OrderDetailStatus.UNUSED;
    }

    /** 쿠폰 발행 실패 처리 */
    public void issueFail() {
        this.status = OrderDetailStatus.ISSUE_FAIL;
    }
}
