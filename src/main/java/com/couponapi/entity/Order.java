package com.couponapi.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

// 엔티티 이름을 OrderEntity로 둔 이유: "Order"는 JPQL/HQL 예약어(order by)라
// 직접 작성하는 JPQL(update/delete)에서 파싱 문제를 일으킬 수 있다. 테이블명은 그대로 orders.
@Entity(name = "OrderEntity")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "orders", uniqueConstraints = {
        @UniqueConstraint(
                name = "uk_customer_trx_id",
                columnNames = {"customerSeq", "customerTrxId"}
        )
}, indexes = {
        @Index(name = "idx_orders_coupon_seq", columnList = "couponSeq"),
        @Index(name = "idx_orders_reg_date", columnList = "regDate"),
        @Index(name = "idx_orders_status", columnList = "status")
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

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
                 Long couponSeq, int quantity, OrderStatus status,
                 String msgSubject, String msgAddContent) {
        this.customerSeq = customerSeq;
        this.customerTrxId = customerTrxId;
        this.trxId = trxId;
        this.couponSeq = couponSeq;
        this.quantity = quantity;
        this.status = status != null ? status : OrderStatus.READY;
        this.msgSubject = msgSubject;
        this.msgAddContent = msgAddContent;
    }
}
