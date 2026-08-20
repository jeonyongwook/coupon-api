package com.couponapi.repository;

import com.couponapi.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {

    // status까지 함께 체크해야 STOP/DEL 상태(판매 중지·삭제)된 쿠폰 상품으로는 주문이 들어오지 않는다.
    // (status 없이 customerGoodsCode만으로 조회하면 판매 중지된 상품도 그대로 주문 가능한 구멍이 생김)
    Optional<Coupon> findByCustomerGoodsCodeAndStatus(String customerGoodsCode, String status);
}
