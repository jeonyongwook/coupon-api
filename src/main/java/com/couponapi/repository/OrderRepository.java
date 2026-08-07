package com.couponapi.repository;

import com.couponapi.entity.Order;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByTrxId(String trxId);

    Optional<Order> findByCustomerSeqAndCustomerTrxId(Long customerSeq, String customerTrxId);

    Optional<Order> findByCustomerTrxId(String customerTrxId);
}