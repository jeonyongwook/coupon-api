package com.couponapi.dto;

/**
 * 선점한 주문 상세 1건을 발행하는 데 필요한 정보. (주문 상세 → 주문 → 쿠폰 상품에서 한 번에 조회)
 */
public record IssueTarget(Long orderDetailSeq, Long issuerSeq, String issuerGoodsCode, Integer validDays) {

    private static final int DEFAULT_VALID_DAYS = 30;

    /** 쿠폰 상품에 유효일수가 없거나 잘못된 값이면 기본값(30일)을 쓴다. */
    public int validDaysOrDefault() {
        return validDays != null && validDays > 0 ? validDays : DEFAULT_VALID_DAYS;
    }
}
