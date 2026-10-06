package com.couponapi.service;

import com.common.exception.CouponApiException;
import com.common.exception.ErrorCode;
import com.couponapi.dto.OrderStatusResponseDto;
import com.couponapi.entity.Customer;
import com.couponapi.entity.Order;
import com.couponapi.entity.OrderDetail;
import com.couponapi.entity.OrderDetailStatus;
import com.couponapi.repository.OrderDetailRepository;
import com.couponapi.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 주문 조회. 고객사가 발행 결과(핀)를 받아가는 경로. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderQueryService {

    private final CustomerAuthenticator customerAuthenticator;
    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;

    public OrderStatusResponseDto getOrder(String customerKey, String apiKey, String trxId) {
        Customer customer = customerAuthenticator.authenticate(customerKey, apiKey);

        // 다른 고객사의 주문은 "존재하지 않음"과 똑같이 404로 응답한다 (존재 여부 노출 방지)
        Order order = orderRepository.findByTrxId(trxId)
                .filter(o -> o.getCustomerSeq().equals(customer.getCustomerSeq()))
                .orElseThrow(() -> new CouponApiException(ErrorCode.ORDER_NOT_FOUND));

        List<OrderDetail> details = orderDetailRepository.findByOrderSeq(order.getOrderSeq());

        int issued = 0;
        int failed = 0;
        int pending = 0;
        for (OrderDetail d : details) {
            if (d.getStatus() == OrderDetailStatus.ISSUE_FAIL) {
                failed++;
            } else if (OrderDetailStatus.PENDING.contains(d.getStatus())) {
                pending++;
            } else {
                issued++;
            }
        }

        List<OrderStatusResponseDto.Item> items = details.stream()
                .map(d -> OrderStatusResponseDto.Item.builder()
                        .orderDetailSeq(d.getOrderDetailSeq())
                        .status(d.getStatus().name())
                        .pin(d.getPin())
                        .validStartDate(d.getValidStartDate())
                        .validEndDate(d.getValidEndDate())
                        .build())
                .toList();

        return OrderStatusResponseDto.builder()
                .resCode("0000")
                .resMsg("SUCCESS")
                .trxId(order.getTrxId())
                .customerTrxId(order.getCustomerTrxId())
                .status(order.getStatus().name())
                .quantity(order.getQuantity())
                .issuedCount(issued)
                .failedCount(failed)
                .pendingCount(pending)
                .items(items)
                .build();
    }
}
