package com.common.exception;

import com.couponapi.dto.ErrorResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
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

    @ExceptionHandler(CouponApiException.class)
    public ResponseEntity<ErrorResponseDto> handleCouponApiException(CouponApiException e) {

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ErrorResponseDto.builder()
                        .resCode(e.getErrorCode())
                        .resMsg(e.getMessage())
                        .build()
        );
    }
}