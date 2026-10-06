package com.couponapi.entity;

/**
 * 주문 상태.
 * READY     : 접수됨 (상세 중 아직 발행이 끝나지 않은 건이 있음)
 * COMPLETED : 모든 상세의 발행 시도가 끝남 (성공/실패 여부는 상세 상태로 확인)
 */
public enum OrderStatus {
    READY, COMPLETED
}
