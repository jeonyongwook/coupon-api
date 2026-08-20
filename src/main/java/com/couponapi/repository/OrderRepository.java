package com.couponapi.repository;

import com.couponapi.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByTrxId(String trxId);

    // Order 테이블의 유니크 제약(customerSeq + customerTrxId)과 동일한 범위로 중복을 체크하기 위한 조회.
    // customerTrxId만으로 전역 조회하면 테이블 제약(고객사별 유니크)과 애플리케이션 검증 범위가
    // 어긋나 서로 다른 고객사의 동일한 customerTrxId를 중복으로 오판할 수 있으므로 이 메서드만 사용한다.
    Optional<Order> findByCustomerSeqAndCustomerTrxId(Long customerSeq, String customerTrxId);
}
