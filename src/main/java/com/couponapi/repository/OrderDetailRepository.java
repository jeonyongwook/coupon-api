package com.couponapi.repository;

import com.couponapi.entity.OrderDetail;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderDetailRepository extends JpaRepository<OrderDetail, Long> {
    List<OrderDetail> findByOrderSeq(Long orderSeq);

    Optional<OrderDetail> findByPin(String pin);

    // ISSUE_TRY_COUNT 설정값만큼 개수를 가변으로 지정하기 위해 Pageable 사용
    List<OrderDetail> findByStatusOrderByRegDateAsc(String status, Pageable pageable);
}