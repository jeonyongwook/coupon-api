package com.couponapi.service;

import com.common.exception.CouponApiException;
import com.common.exception.ErrorCode;
import com.couponapi.dto.OrderRequestDto;
import com.couponapi.dto.OrderResponseDto;
import com.couponapi.entity.Coupon;
import com.couponapi.entity.Customer;
import com.couponapi.entity.Order;
import com.couponapi.entity.UseStatus;
import com.couponapi.repository.CouponRepository;
import com.couponapi.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/**
 * 쿠폰 발행 주문 접수.
 *
 * 멱등성: 같은 고객사가 같은 customerTrxId로 다시 요청하면
 *  - 요청 내용이 같으면 새로 만들지 않고 처음 접수된 주문의 trxId를 그대로 돌려준다 (네트워크 재시도 안전).
 *  - 요청 내용이 다르면 E003(409)으로 거절한다 (같은 거래번호로 다른 주문을 만들 수 없음).
 * 동시에 같은 요청이 들어오는 경우는 DB 유니크 제약(customerSeq + customerTrxId)이 최종 보증한다.
 *
 * 이 클래스에는 @Transactional을 두지 않는다. 저장은 OrderRegistrar가 자체 트랜잭션으로 수행하고,
 * 제약 위반 후의 재조회는 별도의 새 트랜잭션에서 이뤄져야 하기 때문이다.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final CustomerAuthenticator customerAuthenticator;
    private final OrderRegistrar orderRegistrar;
    private final OrderRepository orderRepository;
    private final CouponRepository couponRepository;

    public OrderResponseDto createOrder(OrderRequestDto dto, String apiKey) {

        // 1. 고객사 인증 (customerKey + API 시크릿 키, 상태 확인)
        Customer customer = customerAuthenticator.authenticate(dto.getCustomerKey(), apiKey);

        // 2. 상품 조회 (판매 중지/삭제된 쿠폰 상품은 주문 불가)
        Coupon coupon = couponRepository.findByCustomerGoodsCodeAndStatus(dto.getCustomerGoodsCode(), UseStatus.OK)
                .orElseThrow(() -> new CouponApiException(ErrorCode.INVALID_GOODS_CODE));

        // 3. 이미 접수된 거래인지 확인 (고객사별로 customerTrxId 유니크)
        Optional<Order> existing = orderRepository.findByCustomerSeqAndCustomerTrxId(
                customer.getCustomerSeq(), dto.getCustomerTrxId());
        if (existing.isPresent()) {
            return replayOrConflict(existing.get(), coupon, dto);
        }

        // 4. 신규 접수. 3번 조회와 저장 사이에 동일 요청이 끼어든 경우 유니크 제약이 막아준다.
        try {
            return toResponse(orderRegistrar.register(customer, coupon, dto));
        } catch (DataIntegrityViolationException e) {
            Order raced = orderRepository.findByCustomerSeqAndCustomerTrxId(
                            customer.getCustomerSeq(), dto.getCustomerTrxId())
                    .orElseThrow(() -> e); // 다른 제약 위반이라면 원래 예외를 그대로 전파
            return replayOrConflict(raced, coupon, dto);
        }
    }

    private OrderResponseDto replayOrConflict(Order existing, Coupon coupon, OrderRequestDto dto) {
        if (isSameRequest(existing, coupon, dto)) {
            return toResponse(existing);
        }
        throw new CouponApiException(ErrorCode.DUPLICATE_TRANSACTION);
    }

    private boolean isSameRequest(Order order, Coupon coupon, OrderRequestDto dto) {
        return Objects.equals(order.getCouponSeq(), coupon.getCouponSeq())
                && Objects.equals(order.getQuantity(), dto.getQuantity())
                && Objects.equals(order.getMsgSubject(), dto.getMsgSubject())
                && Objects.equals(order.getMsgAddContent(), dto.getMsgAddContent());
    }

    private OrderResponseDto toResponse(Order order) {
        return OrderResponseDto.builder()
                .resCode("0000")
                .resMsg("SUCCESS")
                .trxId(order.getTrxId())
                .build();
    }
}
