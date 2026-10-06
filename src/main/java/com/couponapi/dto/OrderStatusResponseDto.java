package com.couponapi.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

/** 주문 조회 응답. 발행된 핀은 인증된 고객사 본인의 주문에 대해서만 내려간다. */
@Getter
@Builder
public class OrderStatusResponseDto {
    private String resCode;
    private String resMsg;

    private String trxId;
    private String customerTrxId;
    /** READY: 발행 진행 중, COMPLETED: 모든 건의 발행 시도 종료 (실패 건은 failedCount로 확인) */
    private String status;
    private int quantity;
    private int issuedCount;
    private int failedCount;
    private int pendingCount;
    private List<Item> items;

    @Getter
    @Builder
    public static class Item {
        private Long orderDetailSeq;
        private String status;
        private String pin;
        private LocalDate validStartDate;
        private LocalDate validEndDate;
    }
}
