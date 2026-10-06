package com.couponapi.entity;

import java.util.List;

/**
 * 주문 상세(쿠폰 1장) 상태.
 * READY      : 발행 대기
 * PROCESSING : 배치가 선점해서 발행처에 요청 중 (다중 인스턴스 중복 발행 방지용)
 * UNUSED     : 발행 성공, 미사용
 * ISSUE_FAIL : 발행 실패 (타임아웃 포함)
 * 나머지     : 발행 이후의 사용/만료/취소 상태
 */
public enum OrderDetailStatus {
    READY, PROCESSING, UNUSED, USED, PART_USED, EXPIRED, CANCELED, ISSUE_FAIL;

    /** 아직 발행 결과가 확정되지 않은 상태들. */
    public static final List<OrderDetailStatus> PENDING = List.of(READY, PROCESSING);
}
