package com.couponapi.batch;

import com.couponapi.entity.OrderDetail;
import com.couponapi.entity.OrderDetailStatus;
import com.couponapi.repository.OrderDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 발행 대기(READY) 건을 선점(READY -> PROCESSING)한다.
 *
 * 조회 시 SELECT ... FOR UPDATE SKIP LOCKED로 행을 잠그고, 같은 짧은 트랜잭션 안에서
 * 상태를 PROCESSING으로 바꿔 커밋한다. 커밋 이후에는 다른 인스턴스/회차의 조회 조건(READY)에
 * 걸리지 않으므로, 발행처 호출처럼 오래 걸리는 작업 동안 DB 락을 쥐고 있지 않으면서도
 * 같은 건을 두 번 집어가지 않는다.
 * (별도 빈이어야 @Transactional이 프록시를 통해 적용된다)
 */
@Component
@RequiredArgsConstructor
public class CouponIssueClaimer {

    private final OrderDetailRepository orderDetailRepository;

    /** @return 선점한 OrderDetail의 PK 목록 (엔티티는 트랜잭션 종료 후 detached라 id만 넘긴다) */
    @Transactional
    public List<Long> claim(int limit) {
        List<OrderDetail> targets = orderDetailRepository.findByStatusOrderByRegDateAscOrderDetailSeqAsc(
                OrderDetailStatus.READY, PageRequest.of(0, limit));

        targets.forEach(OrderDetail::markProcessing);

        return targets.stream().map(OrderDetail::getOrderDetailSeq).toList();
    }
}
