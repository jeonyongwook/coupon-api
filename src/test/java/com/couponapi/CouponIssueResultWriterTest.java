package com.couponapi;

import com.couponapi.batch.CouponIssueClaimer;
import com.couponapi.batch.CouponIssueResultWriter;
import com.couponapi.entity.Order;
import com.couponapi.entity.OrderDetail;
import com.couponapi.entity.OrderDetailStatus;
import com.couponapi.entity.OrderStatus;
import com.couponapi.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CouponIssueResultWriterTest extends IntegrationTestSupport {

    @Autowired OrderService orderService;
    @Autowired CouponIssueResultWriter writer;
    @Autowired CouponIssueClaimer claimer;

    private Order createOrder(String trxId, int quantity) {
        String issuedTrxId = orderService.createOrder(request(trxId, quantity), API_KEY).getTrxId();
        return orderRepository.findByTrxId(issuedTrxId).orElseThrow();
    }

    private OrderDetail reload(Long orderDetailSeq) {
        return orderDetailRepository.findById(orderDetailSeq).orElseThrow();
    }

    @Test
    @DisplayName("타임아웃 실패 뒤 늦게 도착한 성공은 반영되고, 이미 성공한 건은 실패로 덮어쓰지 않는다")
    void lateSuccessUpgradesFailAndSuccessIsNeverOverwritten() {
        Order order = createOrder("W-001", 1);
        Long id = orderDetailRepository.findByOrderSeq(order.getOrderSeq()).get(0).getOrderDetailSeq();

        writer.applyFail(id);
        assertThat(reload(id).getStatus()).isEqualTo(OrderDetailStatus.ISSUE_FAIL);

        writer.applySuccess(id, "PIN-LATE", "ISS-LATE");
        OrderDetail upgraded = reload(id);
        assertThat(upgraded.getStatus()).isEqualTo(OrderDetailStatus.UNUSED);
        assertThat(upgraded.getPin()).isEqualTo("PIN-LATE");

        writer.applyFail(id);
        assertThat(reload(id).getStatus()).isEqualTo(OrderDetailStatus.UNUSED);
    }

    @Test
    @DisplayName("성공 건에 성공이 중복 반영되어도 핀이 바뀌지 않는다")
    void duplicateSuccessDoesNotChangePin() {
        Order order = createOrder("W-002", 1);
        Long id = orderDetailRepository.findByOrderSeq(order.getOrderSeq()).get(0).getOrderDetailSeq();

        writer.applySuccess(id, "PIN-FIRST", "ISS-FIRST");
        writer.applySuccess(id, "PIN-SECOND", "ISS-SECOND");

        assertThat(reload(id).getPin()).isEqualTo("PIN-FIRST");
    }

    @Test
    @DisplayName("주문은 READY/PROCESSING 상세가 모두 없어질 때에만 COMPLETED가 된다")
    void orderCompletesOnlyWhenNoPendingDetails() {
        Order order = createOrder("W-003", 2);
        List<OrderDetail> details = orderDetailRepository.findByOrderSeq(order.getOrderSeq());
        Long first = details.get(0).getOrderDetailSeq();
        Long second = details.get(1).getOrderDetailSeq();

        assertThat(writer.completeFinishedOrders()).isZero();

        writer.applySuccess(first, "PIN-A", "ISS-A");
        assertThat(writer.completeFinishedOrders()).isZero();
        assertThat(orderRepository.findById(order.getOrderSeq()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.READY);

        writer.applyFail(second); // 실패여도 "발행 시도 종료"이므로 완료 대상
        assertThat(writer.completeFinishedOrders()).isEqualTo(1);
        assertThat(orderRepository.findById(order.getOrderSeq()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.COMPLETED);

        assertThat(writer.completeFinishedOrders()).isZero(); // 멱등
    }

    @Test
    @DisplayName("release는 PROCESSING 건만 READY로 되돌린다")
    void releaseOnlyAffectsProcessing() {
        Order order = createOrder("W-004", 1);
        Long id = orderDetailRepository.findByOrderSeq(order.getOrderSeq()).get(0).getOrderDetailSeq();

        writer.release(id); // READY는 변화 없음
        assertThat(reload(id).getStatus()).isEqualTo(OrderDetailStatus.READY);

        claimer.claim(10);
        assertThat(reload(id).getStatus()).isEqualTo(OrderDetailStatus.PROCESSING);

        writer.release(id);
        assertThat(reload(id).getStatus()).isEqualTo(OrderDetailStatus.READY);
    }

    @Test
    @DisplayName("오래 PROCESSING에 머문 건은 READY로 복구된다")
    void stuckProcessingIsRecovered() {
        createOrder("W-005", 2);
        assertThat(claimer.claim(10)).hasSize(2);

        // 기준 시각을 미래로 주면 방금 선점한 건도 "오래된 건"으로 취급된다
        int recovered = writer.releaseStaleProcessing(LocalDateTime.now().plusSeconds(5));

        assertThat(recovered).isEqualTo(2);
        assertThat(orderDetailRepository.findAll())
                .allMatch(d -> d.getStatus() == OrderDetailStatus.READY);
    }
}
