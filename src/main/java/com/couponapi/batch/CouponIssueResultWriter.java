package com.couponapi.batch;

import com.couponapi.entity.OrderDetail;
import com.couponapi.repository.OrderDetailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 핀 발행 결과를 엔티티에 반영하는 책임만 담당.
 * CouponIssueBatch와 분리된 별도 빈이어야
 * @Transactional이 프록시를 통해 정상적으로 걸린다.
 * (같은 클래스 내부에서 this.applySuccess(...) 식으로 호출하면 AOP 프록시를
 *  거치지 않아 트랜잭션이 적용되지 않는 self-invocation 문제가 생기기 때문)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueResultWriter {

    private final OrderDetailRepository orderDetailRepository;

    /**
     * 발행 성공 결과 반영. 정상 흐름(타임아웃 전 성공)과
     * 타임아웃 이후 지연 성공 두 경우 모두에서 재사용된다.
     * 이미 처리(UNUSED)된 건이면 중복 반영하지 않는다.
     */
    @Transactional
    public void applySuccess(Long orderDetailSeq, String pin, String issuerTrxId) {
        orderDetailRepository.findById(orderDetailSeq).ifPresentOrElse(detail -> {
            if ("UNUSED".equals(detail.getStatus())) {
                log.warn("이미 처리된 발행 건이라 재반영을 건너뜁니다: OrderDetailSeq = {}", orderDetailSeq);
                return;
            }
            detail.issueSuccess(pin, issuerTrxId, LocalDate.now(), LocalDate.now().plusDays(30));
            log.info("쿠폰 발행 성공 반영: OrderDetailSeq = {}", orderDetailSeq);
        }, () -> log.warn("발행 결과를 반영할 OrderDetail을 찾지 못했습니다: OrderDetailSeq = {}", orderDetailSeq));
    }

    /**
     * 발행 실패 결과 반영.
     * 현재 CouponIssueBatch 구조상 fail은 항상 success보다 먼저 반영되고(늦게 도착한
     * 성공 응답이 fail을 success로 "업그레이드"하는 방향으로만 흐름) 그 반대는 없지만,
     * 그건 호출 순서에 대한 암묵적 전제일 뿐 이 메서드 자체가 보장하는 것은 아니다.
     * 향후 재시도 로직 등이 추가되어 순서가 바뀌더라도 이미 발행에 성공한 건(UNUSED)이
     * 실패로 잘못 덮어써지지 않도록 명시적으로 가드를 건다.
     */
    @Transactional
    public void applyFail(Long orderDetailSeq) {
        orderDetailRepository.findById(orderDetailSeq).ifPresentOrElse(detail -> {
            if ("UNUSED".equals(detail.getStatus())) {
                log.warn("이미 발행 성공 처리된 건이라 실패 반영을 건너뜁니다: OrderDetailSeq = {}", orderDetailSeq);
                return;
            }
            detail.issueFail();
            log.info("쿠폰 발행 실패 반영: OrderDetailSeq = {}", orderDetailSeq);
        }, () -> log.warn("발행 실패를 반영할 OrderDetail을 찾지 못했습니다: OrderDetailSeq = {}", orderDetailSeq));
    }
}