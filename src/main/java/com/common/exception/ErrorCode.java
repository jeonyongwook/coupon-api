package com.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** API 에러 코드 정의. 코드/기본 메시지/HTTP 상태를 한 곳에서 관리한다. */
@Getter
public enum ErrorCode {

    INVALID_PARAMETER("E001", "Invalid Parameter", HttpStatus.BAD_REQUEST),
    INVALID_GOODS_CODE("E002", "Invalid Goods Code", HttpStatus.BAD_REQUEST),
    DUPLICATE_TRANSACTION("E003", "Duplicate Transaction ID", HttpStatus.CONFLICT),
    INACTIVE_CUSTOMER("E004", "Inactive Customer", HttpStatus.FORBIDDEN),
    ORDER_NOT_FOUND("E005", "Order Not Found", HttpStatus.NOT_FOUND),
    DATA_CONFLICT("E009", "Data Conflict", HttpStatus.CONFLICT),
    INVALID_API_KEY("E010", "Invalid API Key", HttpStatus.UNAUTHORIZED),
    INTERNAL_ERROR("E999", "Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
