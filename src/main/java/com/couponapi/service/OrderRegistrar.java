package com.couponapi.service;

import com.couponapi.dto.OrderRequestDto;
import com.couponapi.entity.*;
import com.couponapi.repository.OrderDetailRepository;
import com.couponapi.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 주문(Order)과 주문 상세(OrderDetail)를 하나의 트랜잭션으로 저장한다.
 * OrderService와 분리한 이유: 유니크 제약 위반(동시 중복 요청)이 이 트랜잭션을 롤백시킨 뒤,
 * OrderService가 "깨끗한 새 트랜잭션"에서 기존 주문을 다시 조회해 멱등 응답을 줄 수 있게 하기 위해서다.
 * (같은 트랜잭션 안에서 예외를 잡고 계속 진행하면 rollback-only 상태 때문에 커밋이 실패한다)
 */
@Component
@RequiredArgsConstructor
public class OrderRegistrar {

    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;

    @Transactional
    public Order register(Customer customer, Coupon coupon, OrderRequestDto dto) {
        // UUID 전체(32자)를 사용해 충돌 가능성을 사실상 없앤다.
        String trxId = "TX-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();

        Order order = orderRepository.save(Order.builder()
                .customerSeq(customer.getCustomerSeq())
                .customerTrxId(dto.getCustomerTrxId())
                .trxId(trxId)
                .couponSeq(coupon.getCouponSeq())
                .quantity(dto.getQuantity())
                .status(OrderStatus.READY)
                .msgSubject(dto.getMsgSubject())
                .msgAddContent(dto.getMsgAddContent())
                .build());

        List<OrderDetail> details = new ArrayList<>(dto.getQuantity());
        for (int i = 0; i < dto.getQuantity(); i++) {
            details.add(OrderDetail.builder()
                    .orderSeq(order.getOrderSeq())
                    .status(OrderDetailStatus.READY)
                    .build());
        }
        orderDetailRepository.saveAll(details);

        return order;
    }
}
