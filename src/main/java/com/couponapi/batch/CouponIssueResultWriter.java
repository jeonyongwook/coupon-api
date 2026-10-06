package com.couponapi.batch;

import com.couponapi.entity.OrderDetail;
import com.couponapi.entity.OrderDetailStatus;
import com.couponapi.entity.OrderStatus;
import com.couponapi.repository.OrderDetailRepository;
import com.couponapi.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 핀 발행 결과를 DB에 반영하는 책임만 담당. 각 메서드가 짧은 트랜잭션 하나다.
 * CouponIssueBatch와 분리된 별도 빈이어야 @Transactional이 프록시를 통해 정상적으로 걸린다.
 * (같은 클래스 내부 호출은 AOP 프록시를 거치지 않아 트랜잭션이 적용되지 않는 self-invocation 문제)
 *
 * 상태 전이 규칙 (이미 확정된 결과를 뒤집지 않도록 각 메서드가 스스로 가드한다):
 *   success : READY / PROCESSING / ISSUE_FAIL  -> UNUSED   (타임아웃 후 늦게 도착한 성공이 실패를 성공으로 "승급")
 *   fail    : READY / PROCESSING               -> ISSUE_FAIL (이미 성공한 건은 절대 실패로 덮어쓰지 않음)
 *   release : PROCESSING                       -> READY    (발행처에 요청조차 못 보낸 건을 되돌림)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueResultWriter {

    private final OrderDetailRepository orderDetailRepository;
    private final OrderRepository orderRepository;

    @Transactional
    public void applySuccess(Long orderDetailSeq, String pin, String issuerTrxId) {
        orderDetailRepository.findById(orderDetailSeq).ifPresentOrElse(detail -> {
            OrderDetailStatus status = detail.getStatus();
            boolean applicable = status == OrderDetailStatus.READY
                    || status == OrderDetailStatus.PROCESSING
                    || status == OrderDetailStatus.ISSUE_FAIL;
            if (!applicable) {
                log.warn("이미 확정된 발행 건이라 성공 재반영을 건너뜁니다: OrderDetailSeq = {}, status = {}",
                        orderDetailSeq, status);
                return;
            }
            detail.issueSuccess(pin, issuerTrxId, LocalDate.now(), LocalDate.now().plusDays(30));
            log.info("쿠폰 발행 성공 반영: OrderDetailSeq = {}", orderDetailSeq);
        }, () -> log.warn("발행 결과를 반영할 OrderDetail을 찾지 못했습니다: OrderDetailSeq = {}", orderDetailSeq));
    }

    @Transactional
    public void applyFail(Long orderDetailSeq) {
        orderDetailRepository.findById(orderDetailSeq).ifPresentOrElse(detail -> {
            OrderDetailStatus status = detail.getStatus();
            if (status != OrderDetailStatus.READY && status != OrderDetailStatus.PROCESSING) {
                log.warn("이미 확정된 발행 건이라 실패 반영을 건너뜁니다: OrderDetailSeq = {}, status = {}",
                        orderDetailSeq, status);
                return;
            }
            detail.issueFail();
            log.info("쿠폰 발행 실패 반영: OrderDetailSeq = {}", orderDetailSeq);
        }, () -> log.warn("발행 실패를 반영할 OrderDetail을 찾지 못했습니다: OrderDetailSeq = {}", orderDetailSeq));
    }

    /** 선점했지만 발행처에 요청을 보내지 못한 건(스레드풀 포화 등)을 다음 회차에 다시 처리되도록 되돌린다. */
    @Transactional
    public void release(Long orderDetailSeq) {
        orderDetailRepository.findById(orderDetailSeq).ifPresent(detail -> {
            if (detail.getStatus() == OrderDetailStatus.PROCESSING) {
                detail.releaseToReady();
                log.info("발행 대기로 되돌림: OrderDetailSeq = {}", orderDetailSeq);
            }
        });
    }

    /** PROCESSING으로 너무 오래 머문 건을 READY로 복구한다. @return 복구한 건수 */
    @Transactional
    public int releaseStaleProcessing(LocalDateTime threshold) {
        return orderDetailRepository.releaseStaleProcessing(
                OrderDetailStatus.READY, OrderDetailStatus.PROCESSING, threshold, LocalDateTime.now());
    }

    /** 모든 상세의 발행 시도가 끝난 주문을 COMPLETED로 전환한다. @return 전환한 주문 수 */
    @Transactional
    public int completeFinishedOrders() {
        return orderRepository.completeFinishedOrders(
                OrderStatus.COMPLETED, OrderStatus.READY, OrderDetailStatus.PENDING, LocalDateTime.now());
    }
}
