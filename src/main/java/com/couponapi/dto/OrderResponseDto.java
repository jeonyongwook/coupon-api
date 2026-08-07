package com.couponapi.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class OrderResponseDto {
    private String resCode;
    private String resMsg;
    private String trxId;

}