package com.couponapi.repository;

import com.couponapi.entity.OrderDetail;
import com.couponapi.entity.OrderDetailStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderDetailRepository extends JpaRepository<OrderDetail, Long> {

    List<OrderDetail> findByOrderSeq(Long orderSeq);

    /**
     * 발행 대기 건을 오래된 순으로 가져오면서 행 잠금을 건다 (SELECT ... FOR UPDATE SKIP LOCKED).
     * 다른 인스턴스가 이미 잠근 행은 건너뛰므로, 서버를 여러 대 띄워도 같은 건을 두 인스턴스가
     * 동시에 집어가지 않는다. (lock.timeout = -2 가 SKIP LOCKED. MariaDB 10.6+ / MySQL 8+ 필요)
     * 반드시 트랜잭션 안에서 호출해야 한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    List<OrderDetail> findByStatusOrderByRegDateAscOrderDetailSeqAsc(OrderDetailStatus status, Pageable pageable);

    /**
     * PROCESSING 상태로 너무 오래 머문 건(선점한 서버가 죽은 경우 등)을 다시 READY로 되돌린다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update OrderDetail d set d.status = :ready, d.modDate = :now " +
            "where d.status = :processing and d.modDate < :threshold")
    int releaseStaleProcessing(@Param("ready") OrderDetailStatus ready,
                               @Param("processing") OrderDetailStatus processing,
                               @Param("threshold") LocalDateTime threshold,
                               @Param("now") LocalDateTime now);
}
