package com.common.exception;

import lombok.Getter;

@Getter
public class CouponApiException extends RuntimeException {
    private final String errorCode;

    public CouponApiException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}