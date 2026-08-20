package com.common.exception;

import com.couponapi.dto.ErrorResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleAll(Exception e) {
        log.error("Unhandled Exception: ", e);

        return ResponseEntity.internalServerError().body(
                ErrorResponseDto.builder()
                        .resCode("E999")
                        .resMsg("Internal Server Error")
                        .build()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidationException(MethodArgumentNotValidException e) {
        String errorMessage = e.getBindingResult()
                .getAllErrors()
                .get(0)
                .getDefaultMessage();

        log.error("Validation failed: {}", errorMessage);

        return ResponseEntity.badRequest().body(
                ErrorResponseDto.builder()
                        .resCode("E001")
                        .resMsg(errorMessage)
                        .build()
        );
    }

    /**
     * X-API-KEY 헤더가 아예 없는 경우. (형식은 맞지만 값이 틀린 경우는 CouponApiException(E010)에서 처리)
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponseDto> handleMissingHeader(MissingRequestHeaderException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ErrorResponseDto.builder()
                        .resCode("E010")
                        .resMsg("Missing API Key")
                        .build()
        );
    }

    @ExceptionHandler(CouponApiException.class)
    public ResponseEntity<ErrorResponseDto> handleCouponApiException(CouponApiException e) {
        return ResponseEntity.status(e.getHttpStatus()).body(
                ErrorResponseDto.builder()
                        .resCode(e.getErrorCode())
                        .resMsg(e.getMessage())
                        .build()
        );
    }

    /**
     * 조회 후 저장(check-then-act) 사이의 경쟁 상태로 유니크 제약을 위반한 경우를 위한 최종 방어선.
     * OrderService에서 대부분 걸러 E003으로 변환하지만, 혹시 놓친 경로가 있어도
     * 500 Internal Server Error 대신 의미 있는 응답(409 + E003)을 준다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDto> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        log.warn("Data integrity violation: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ErrorResponseDto.builder()
                        .resCode("E003")
                        .resMsg("Duplicate Transaction ID")
                        .build()
        );
    }
}
