package com.couponapi;

import com.couponapi.batch.CouponIssueBatch;
import com.couponapi.dto.OrderRequestDto;
import com.couponapi.entity.Coupon;
import com.couponapi.entity.Issuer;
import com.couponapi.entity.Order;
import com.couponapi.entity.OrderDetail;
import com.couponapi.entity.OrderDetailStatus;
import com.couponapi.entity.OrderStatus;
import com.couponapi.entity.UseStatus;
import com.couponapi.issuer.IssuerClient.IssueRequest;
import com.couponapi.issuer.IssuerClient.IssuedPin;
import com.couponapi.service.OrderService;
import com.common.entity.SystemConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static com.couponapi.ScriptedIssuerClient.sleep;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 배치(CouponIssueBatch)를 돌리되 발행처만 테스트가 제어하는 가짜로 바꿔,
 * 지연·타임아웃·다중 발행처 시나리오를 검증한다. (타임아웃 설정은 system_config 값으로 준다)
 */
class CouponIssueTimeoutTest extends IntegrationTestSupport {

    @Autowired OrderService orderService;
    @Autowired CouponIssueBatch batch;
    @Autowired @Qualifier("couponExecutor") ThreadPoolTaskExecutor couponExecutor;

    private void putConfig(String key, String value) {
        systemConfigRepository.save(SystemConfig.builder()
                .groupKey("BATCH_COUPON_ISSUE").configKey(key).configValue(value).description("test").build());
    }

    private Long defaultIssuerSeq() {
        return couponRepository.findByCustomerGoodsCodeAndStatus(GOODS_CODE, UseStatus.OK).orElseThrow().getIssuerSeq();
    }

    private Long createOrderAndGetFirstDetailSeq(String customerTrxId, int quantity) {
        String trxId = orderService.createOrder(request(customerTrxId, quantity), API_KEY).getTrxId();
        return detailsOf(trxId).get(0).getOrderDetailSeq();
    }

    private List<OrderDetail> detailsOf(String trxId) {
        Order order = orderRepository.findByTrxId(trxId).orElseThrow();
        return orderDetailRepository.findByOrderSeq(order.getOrderSeq());
    }

    private OrderDetail reload(Long orderDetailSeq) {
        return orderDetailRepository.findById(orderDetailSeq).orElseThrow();
    }

    /** 발행처 B와 그 발행처의 쿠폰 상품(GOODS-T2, 유효 7일)을 만든다. */
    private Coupon saveSecondIssuerCoupon() {
        Issuer issuer = issuerRepository.save(Issuer.builder()
                .name("테스트 발행처 B").businessNo("000-00-00001").status(UseStatus.OK).build());
        return couponRepository.save(Coupon.builder()
                .issuerSeq(issuer.getIssuerSeq())
                .name("테스트 쿠폰 B")
                .price(BigDecimal.valueOf(2000))
                .validDays(7)
                .status(UseStatus.OK)
                .issuerGoodsCode("ISSUER-T2")
                .customerGoodsCode("GOODS-T2")
                .build());
    }

    private String orderGoodsB(String customerTrxId) {
        OrderRequestDto dto = request(customerTrxId, 1);
        dto.setCustomerGoodsCode("GOODS-T2");
        return orderService.createOrder(dto, API_KEY).getTrxId();
    }

    private static void waitUntil(BooleanSupplier condition, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            sleep(50);
        }
    }

    @Test
    @DisplayName("타임아웃으로 ISSUE_FAIL이 된 뒤 늦게 도착한 성공 응답은 UNUSED로 반영된다")
    void lateSuccessAfterTimeoutIsApplied() {
        putConfig("ISSUE_TIMEOUT_SEC", "1");
        scriptedIssuer.on(defaultIssuerSeq(), req -> {
            sleep(2500); // 타임아웃(1초)보다 늦게 성공 응답
            return new IssuedPin("PIN-LATE-1", "ISS-LATE-1");
        });
        Long detailSeq = createOrderAndGetFirstDetailSeq("TO-001", 1);

        batch.processReadyOrders(); // 타임아웃까지만 기다리고 ISSUE_FAIL로 반영한 뒤 돌아온다

        assertThat(reload(detailSeq).getStatus()).isEqualTo(OrderDetailStatus.ISSUE_FAIL);

        waitUntil(() -> reload(detailSeq).getStatus() == OrderDetailStatus.UNUSED, 5000);
        OrderDetail upgraded = reload(detailSeq);
        assertThat(upgraded.getStatus()).isEqualTo(OrderDetailStatus.UNUSED);
        assertThat(upgraded.getPin()).isEqualTo("PIN-LATE-1");
    }

    @Test
    @DisplayName("타임아웃은 큐 대기 시간이 아니라 스레드가 실제로 처리를 시작한 시점부터 센다")
    void timeoutIsMeasuredFromActualStart() {
        putConfig("ISSUE_TIMEOUT_SEC", "2");
        // 건당 1.2초(타임아웃 2초보다 짧음). 스레드 1개로 3건을 직렬 처리하면 마지막 건은 제출 후 3.6초 뒤에 끝난다.
        // 제출 시점부터 타임아웃을 세면 2·3번째 건이 실패하고, 실제 시작 시점부터 세면 모두 성공한다.
        scriptedIssuer.on(defaultIssuerSeq(), req -> {
            sleep(1200);
            return new IssuedPin("PIN-" + req.orderDetailSeq(), "ISS-" + req.orderDetailSeq());
        });
        orderService.createOrder(request("TO-002", 3), API_KEY);

        int core = couponExecutor.getCorePoolSize();
        int max = couponExecutor.getMaxPoolSize();
        couponExecutor.setCorePoolSize(1);
        couponExecutor.setMaxPoolSize(1);
        try {
            batch.processReadyOrders();
        } finally {
            couponExecutor.setMaxPoolSize(max);
            couponExecutor.setCorePoolSize(core);
        }

        assertThat(orderDetailRepository.findAll()).hasSize(3)
                .allSatisfy(d -> assertThat(d.getStatus()).isEqualTo(OrderDetailStatus.UNUSED));
    }

    @Test
    @DisplayName("상품의 발행처에 맞는 클라이언트로 요청하고, 쿠폰 상품의 유효일수를 반영한다")
    void routesByIssuerAndAppliesCouponValidDays() {
        Coupon couponB = saveSecondIssuerCoupon();
        List<IssueRequest> received = new CopyOnWriteArrayList<>();
        scriptedIssuer.on(defaultIssuerSeq(), req -> {
            received.add(req);
            return new IssuedPin("A-" + req.orderDetailSeq(), "ISS-A-" + req.orderDetailSeq());
        });
        scriptedIssuer.on(couponB.getIssuerSeq(), req -> {
            received.add(req);
            return new IssuedPin("B-" + req.orderDetailSeq(), "ISS-B-" + req.orderDetailSeq());
        });

        String trxA = orderService.createOrder(request("RT-001", 1), API_KEY).getTrxId();
        String trxB = orderGoodsB("RT-002");

        batch.processReadyOrders();

        OrderDetail a = detailsOf(trxA).get(0);
        OrderDetail b = detailsOf(trxB).get(0);
        assertThat(a.getPin()).startsWith("A-");
        assertThat(b.getPin()).startsWith("B-");
        assertThat(received).extracting(IssueRequest::issuerGoodsCode)
                .containsExactlyInAnyOrder("ISSUER-T1", "ISSUER-T2");
        assertThat(a.getValidEndDate()).isEqualTo(a.getValidStartDate().plusDays(30));
        assertThat(b.getValidEndDate()).isEqualTo(b.getValidStartDate().plusDays(7));
    }

    @Test
    @DisplayName("한 발행처가 오류를 내도 그 건만 ISSUE_FAIL이 되고, 다른 발행처 건은 정상 발행된다")
    void issuerErrorOnlyFailsItsOwnDetails() {
        Coupon couponB = saveSecondIssuerCoupon();
        scriptedIssuer.on(defaultIssuerSeq(), req -> {
            throw new IllegalStateException("발행처 A 장애");
        });
        scriptedIssuer.on(couponB.getIssuerSeq(),
                req -> new IssuedPin("B-" + req.orderDetailSeq(), "ISS-B-" + req.orderDetailSeq()));

        String trxA = orderService.createOrder(request("RT-003", 1), API_KEY).getTrxId();
        String trxB = orderGoodsB("RT-004");

        batch.processReadyOrders();

        assertThat(detailsOf(trxA).get(0).getStatus()).isEqualTo(OrderDetailStatus.ISSUE_FAIL);
        assertThat(detailsOf(trxB).get(0).getStatus()).isEqualTo(OrderDetailStatus.UNUSED);
        // 실패여도 발행 시도는 끝났으므로 두 주문 모두 COMPLETED
        assertThat(orderRepository.findByTrxId(trxA).orElseThrow().getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(orderRepository.findByTrxId(trxB).orElseThrow().getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }
}
