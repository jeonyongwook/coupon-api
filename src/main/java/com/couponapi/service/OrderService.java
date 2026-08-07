package com.couponapi.service;

import com.common.exception.CouponApiException;
import com.couponapi.dto.OrderRequestDto;
import com.couponapi.dto.OrderResponseDto;
import com.couponapi.entity.Coupon;
import com.couponapi.entity.Customer;
import com.couponapi.entity.Order;
import com.couponapi.entity.OrderDetail;
import com.couponapi.repository.CouponRepository;
import com.couponapi.repository.CustomerRepository;
import com.couponapi.repository.OrderDetailRepository;
import com.couponapi.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor // final 필드 자동 주입
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final CustomerRepository customerRepository;
    private final CouponRepository couponRepository; // 상품 조회를 위해 추가

    @Transactional
    public OrderResponseDto createOrder(OrderRequestDto dto) {

        // 1. 고객사 Key 체크
        Customer customer = customerRepository.findByCustomerKey(dto.getCustomerKey())
                .orElseThrow(() -> new CouponApiException("E001", "Invalid Customer Key"));

        // 2. 상품 정보 조회
        Coupon coupon = couponRepository.findByCustomerGoodsCode(dto.getCustomerGoodsCode())
                .orElseThrow(() -> new CouponApiException("E002", "Invalid Goods Code"));

        // 3. 중복 거래 확인 (customerTrxId)
        orderRepository.findByCustomerTrxId(dto.getCustomerTrxId())
                .ifPresent(order -> {
                    throw new CouponApiException("E003", "Duplicate Transaction ID");
                });

        // 4. 메인 주문(Order) 생성 및 저장 (Builder 사용)
        String trxId = "TX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Order order = Order.builder()
                .customerSeq(customer.getCustomerSeq())
                .customerTrxId(dto.getCustomerTrxId())
                .trxId(trxId)
                .couponSeq(coupon.getCouponSeq())
                .quantity(dto.getQuantity())
                .status("READY")   // READY, COMPLETED
                .msgSubject(dto.getMsgSubject())
                .msgAddContent(dto.getMsgAddContent())
                .build();

        Order savedOrder = orderRepository.save(order);

        List<OrderDetail> details = new ArrayList<>();
        for (int i = 0; i < dto.getQuantity(); i++) {
            OrderDetail detail = OrderDetail.builder()
                    .orderSeq(savedOrder.getOrderSeq())
                    .status("READY")
                    .build();
            details.add(detail);
        }

        orderDetailRepository.saveAll(details);

        return OrderResponseDto.builder()
                .resCode("0000")
                .resMsg("SUCCESS")
                .trxId(savedOrder.getTrxId())
                .build();
    }
}