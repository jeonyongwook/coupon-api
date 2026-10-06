package com.couponapi.repository;

import com.couponapi.entity.Order;
import com.couponapi.entity.OrderDetailStatus;
import com.couponapi.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByTrxId(String trxId);

    // Order 테이블의 유니크 제약(customerSeq + customerTrxId)과 동일한 범위로 중복을 체크하기 위한 조회.
    Optional<Order> findByCustomerSeqAndCustomerTrxId(Long customerSeq, String customerTrxId);

    /**
     * 발행 대기(READY/PROCESSING) 상세가 하나도 남지 않은 READY 주문을 COMPLETED로 일괄 전환한다.
     * 건별로 "마지막 상세인지" 판단하면 동시에 끝나는 두 건이 서로의 미반영 상태를 보고 둘 다
     * 완료 처리를 놓치는 경쟁 상태가 생기므로, 배치 회차마다 집합 단위로 한 번에 처리한다.
     * (멱등: 몇 번을 실행해도 결과가 같다)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update OrderEntity o set o.status = :completed, o.modDate = :now " +
            "where o.status = :ready and not exists (" +
            "  select 1 from OrderDetail d where d.orderSeq = o.orderSeq and d.status in :pending)")
    int completeFinishedOrders(@Param("completed") OrderStatus completed,
                               @Param("ready") OrderStatus ready,
                               @Param("pending") Collection<OrderDetailStatus> pending,
                               @Param("now") LocalDateTime now);
}
