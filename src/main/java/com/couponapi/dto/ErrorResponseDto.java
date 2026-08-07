package com.couponapi.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ErrorResponseDto {
    // 에러코드 정의
    //
    private String resCode;
    
    private String resMsg;
}