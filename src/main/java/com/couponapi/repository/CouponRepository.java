package com.couponapi.repository;

import com.couponapi.entity.Coupon;
import com.couponapi.entity.UseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {

    // status까지 함께 체크해야 STOP/DEL 상태(판매 중지·삭제)된 쿠폰 상품으로는 주문이 들어오지 않는다.
    Optional<Coupon> findByCustomerGoodsCodeAndStatus(String customerGoodsCode, UseStatus status);
}
