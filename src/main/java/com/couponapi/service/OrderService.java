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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor // final 필드 자동 주입
@Transactional(readOnly = true)
public class OrderService {

    private static final String COUPON_STATUS_OK = "OK";
    private static final String CUSTOMER_STATUS_OK = "OK";

    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final CustomerRepository customerRepository;
    private final CouponRepository couponRepository;

    @Transactional
    public OrderResponseDto createOrder(OrderRequestDto dto, String apiKey) {

        // 1. 고객사 Key 체크
        Customer customer = customerRepository.findByCustomerKey(dto.getCustomerKey())
                .orElseThrow(() -> new CouponApiException("E001", "Invalid Customer Key"));

        // 1-1. API 시크릿 키 검증. customerKey는 body에 평문으로 오가는 식별자일 뿐이라
        //      그것만으로 인증하면 그대로 도용될 수 있으므로 별도 시크릿을 헤더로 받아 검증한다.
        verifyApiKey(customer, apiKey);

        // 1-2. 고객사 상태 체크 (정지/삭제된 고객사는 주문 불가)
        if (!CUSTOMER_STATUS_OK.equals(customer.getStatus())) {
            throw new CouponApiException("E004", "Inactive Customer");
        }

        // 2. 상품 정보 조회 (판매 중지/삭제된 쿠폰 상품은 주문 불가하므로 status까지 함께 체크)
        Coupon coupon = couponRepository.findByCustomerGoodsCodeAndStatus(dto.getCustomerGoodsCode(), COUPON_STATUS_OK)
                .orElseThrow(() -> new CouponApiException("E002", "Invalid Goods Code"));

        // 3. 중복 거래 확인 (customerTrxId는 고객사별로 유니크 - Order 테이블의 유니크 제약과
        //    동일한 범위로 체크해야 한다. 전역으로 체크하면 다른 고객사의 동일한 값과 충돌 오판 가능)
        orderRepository.findByCustomerSeqAndCustomerTrxId(customer.getCustomerSeq(), dto.getCustomerTrxId())
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

        Order savedOrder;
        try {
            savedOrder = orderRepository.save(order);
        } catch (DataIntegrityViolationException e) {
            // 3번의 조회-후-저장(check-then-act) 사이에 동시 요청이 끼어드는 경쟁 상태를 대비한 최종 방어선.
            // 앞선 조회는 통과했더라도 DB의 유니크 제약(customerSeq+customerTrxId)이 최종적으로 보증한다.
            throw new CouponApiException("E003", "Duplicate Transaction ID");
        }

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

    /**
     * customerKey(식별자)만으로는 요청 위조가 가능하므로, 고객사에 발급된 secretKey를
     * 헤더(X-API-KEY)로 받아 검증한다. 타이밍 공격 방지를 위해 상수 시간 비교(MessageDigest.isEqual)를 사용한다.
     */
    private void verifyApiKey(Customer customer, String apiKey) {
        if (apiKey == null || customer.getSecretKey() == null
                || !MessageDigest.isEqual(
                        apiKey.getBytes(StandardCharsets.UTF_8),
                        customer.getSecretKey().getBytes(StandardCharsets.UTF_8))) {
            throw new CouponApiException("E010", "Invalid API Key", HttpStatus.UNAUTHORIZED);
        }
    }
}
