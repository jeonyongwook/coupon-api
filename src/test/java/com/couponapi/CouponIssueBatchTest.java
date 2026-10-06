package com.couponapi;

import com.couponapi.batch.CouponIssueBatch;
import com.couponapi.batch.CouponIssueClaimer;
import com.couponapi.entity.Order;
import com.couponapi.entity.OrderDetail;
import com.couponapi.entity.OrderDetailStatus;
import com.couponapi.entity.OrderStatus;
import com.couponapi.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CouponIssueBatchTest extends IntegrationTestSupport {

    @Autowired OrderService orderService;
    @Autowired CouponIssueBatch batch;
    @Autowired CouponIssueClaimer claimer;

    @Test
    @DisplayName("배치 1회 실행으로 대기 건이 모두 발행되고 주문이 COMPLETED가 된다")
    void batchIssuesAllPinsAndCompletesOrder() {
        String trxId = orderService.createOrder(request("B-001", 3), API_KEY).getTrxId();

        batch.processReadyOrders(); // 가짜 발행처 응답이 모두 올 때까지 블로킹

        Order order = orderRepository.findByTrxId(trxId).orElseThrow();
        List<OrderDetail> details = orderDetailRepository.findByOrderSeq(order.getOrderSeq());

        assertThat(details).hasSize(3);
        assertThat(details).allSatisfy(d -> {
            assertThat(d.getStatus()).isEqualTo(OrderDetailStatus.UNUSED);
            assertThat(d.getPin()).startsWith("PIN-");
            assertThat(d.getValidEndDate()).isAfter(d.getValidStartDate());
        });
        assertThat(details.stream().map(OrderDetail::getPin).distinct()).hasSize(3);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("한 번 선점한 건은 다시 선점되지 않는다 (중복 발행 방지)")
    void claimedDetailsAreNotClaimedTwice() {
        orderService.createOrder(request("B-002", 3), API_KEY);

        List<Long> first = claimer.claim(10);
        List<Long> second = claimer.claim(10);

        assertThat(first).hasSize(3);
        assertThat(second).isEmpty();
    }

    @Test
    @DisplayName("선점은 요청한 개수만큼만, 오래된 건부터 가져간다")
    void claimRespectsLimit() {
        orderService.createOrder(request("B-003", 5), API_KEY);

        assertThat(claimer.claim(2)).hasSize(2);
        assertThat(claimer.claim(10)).hasSize(3);
    }
}
