package com.couponapi;

import com.common.exception.CouponApiException;
import com.common.exception.ErrorCode;
import com.couponapi.dto.OrderRequestDto;
import com.couponapi.dto.OrderResponseDto;
import com.couponapi.entity.Order;
import com.couponapi.entity.OrderDetail;
import com.couponapi.entity.OrderDetailStatus;
import com.couponapi.entity.OrderStatus;
import com.couponapi.entity.UseStatus;
import com.couponapi.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderServiceTest extends IntegrationTestSupport {

    @Autowired
    OrderService orderService;

    @Test
    @DisplayName("주문을 접수하면 READY 주문과 수량만큼의 READY 상세가 생성된다")
    void createsOrderWithReadyDetails() {
        OrderResponseDto res = orderService.createOrder(request("T-001", 3), API_KEY);

        assertThat(res.getResCode()).isEqualTo("0000");
        Order order = orderRepository.findByTrxId(res.getTrxId()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.READY);
        assertThat(order.getQuantity()).isEqualTo(3);

        List<OrderDetail> details = orderDetailRepository.findByOrderSeq(order.getOrderSeq());
        assertThat(details).hasSize(3);
        assertThat(details).allMatch(d -> d.getStatus() == OrderDetailStatus.READY);
    }

    @Test
    @DisplayName("같은 customerTrxId로 같은 내용을 재요청하면 새 주문 없이 기존 trxId를 돌려준다 (멱등)")
    void sameRequestIsIdempotent() {
        OrderResponseDto first = orderService.createOrder(request("T-002", 2), API_KEY);
        OrderResponseDto second = orderService.createOrder(request("T-002", 2), API_KEY);

        assertThat(second.getTrxId()).isEqualTo(first.getTrxId());
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(orderDetailRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 customerTrxId인데 내용이 다르면 E003으로 거절한다")
    void sameTrxIdWithDifferentPayloadIsRejected() {
        orderService.createOrder(request("T-003", 2), API_KEY);

        assertThatThrownBy(() -> orderService.createOrder(request("T-003", 5), API_KEY))
                .isInstanceOfSatisfying(CouponApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_TRANSACTION));
        assertThat(orderRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 고객사는 같은 customerTrxId를 독립적으로 쓸 수 있다")
    void trxIdIsScopedPerCustomer() {
        OrderResponseDto a = orderService.createOrder(request("T-004", 1), API_KEY);

        OrderRequestDto other = request("T-004", 1);
        other.setCustomerKey(OTHER_CUSTOMER_KEY);
        OrderResponseDto b = orderService.createOrder(other, OTHER_API_KEY);

        assertThat(b.getTrxId()).isNotEqualTo(a.getTrxId());
        assertThat(orderRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("동시에 같은 요청이 들어와도 주문은 1건만 생기고 모든 응답이 같은 trxId를 돌려준다")
    void concurrentSameRequestCreatesSingleOrder() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);

        List<Future<OrderResponseDto>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return orderService.createOrder(request("T-RACE", 2), API_KEY);
            }));
        }
        ready.await();
        start.countDown();

        Set<String> trxIds = new HashSet<>();
        for (Future<OrderResponseDto> f : futures) {
            trxIds.add(f.get(30, TimeUnit.SECONDS).getTrxId());
        }
        pool.shutdown();

        assertThat(trxIds).hasSize(1);
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(orderDetailRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("틀린 API 키와 존재하지 않는 고객사는 같은 E010으로 응답한다 (고객키 추측 방지)")
    void wrongKeyAndUnknownCustomerLookTheSame() {
        assertThatThrownBy(() -> orderService.createOrder(request("T-005", 1), "wrong-key"))
                .isInstanceOfSatisfying(CouponApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_API_KEY));

        OrderRequestDto unknown = request("T-005", 1);
        unknown.setCustomerKey("NO_SUCH_CUSTOMER");
        assertThatThrownBy(() -> orderService.createOrder(unknown, API_KEY))
                .isInstanceOfSatisfying(CouponApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_API_KEY));
    }

    @Test
    @DisplayName("정지된 고객사는 주문할 수 없다")
    void inactiveCustomerIsRejected() {
        saveCustomer("STOPPED_CUST", "stopped-key", UseStatus.STOP);
        OrderRequestDto dto = request("T-006", 1);
        dto.setCustomerKey("STOPPED_CUST");

        assertThatThrownBy(() -> orderService.createOrder(dto, "stopped-key"))
                .isInstanceOfSatisfying(CouponApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INACTIVE_CUSTOMER));
    }

    @Test
    @DisplayName("존재하지 않는 상품 코드는 E002")
    void unknownGoodsCodeIsRejected() {
        OrderRequestDto dto = request("T-007", 1);
        dto.setCustomerGoodsCode("NO_SUCH_GOODS");

        assertThatThrownBy(() -> orderService.createOrder(dto, API_KEY))
                .isInstanceOfSatisfying(CouponApiException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_GOODS_CODE));
    }
}
