package com.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class CouponApiException extends RuntimeException {
    private final String errorCode;
    private final HttpStatus httpStatus;

    public CouponApiException(String errorCode, String message) {
        this(errorCode, message, HttpStatus.BAD_REQUEST);
    }

    public CouponApiException(String errorCode, String message, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
}
