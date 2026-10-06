package com.common.exception;

import com.couponapi.dto.ErrorResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 최후의 방어선. 단, Spring MVC가 이미 알맞은 HTTP 상태를 정해둔 예외
     * (404 경로 없음, 405 메서드 불가, 필수 파라미터 누락 등 - ErrorResponse 구현체)는
     * 500으로 뭉개지 않고 그 상태를 그대로 돌려준다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleAll(Exception e) {
        if (e instanceof ErrorResponse errorResponse) {
            int status = errorResponse.getStatusCode().value();
            String detail = errorResponse.getBody().getDetail();
            return ResponseEntity.status(errorResponse.getStatusCode()).body(
                    build("E" + status, detail != null ? detail : "Request Error"));
        }

        log.error("Unhandled Exception: ", e);
        return build(ErrorCode.INTERNAL_ERROR);
    }

    @ExceptionHandler(CouponApiException.class)
    public ResponseEntity<ErrorResponseDto> handleCouponApiException(CouponApiException e) {
        return ResponseEntity.status(e.getHttpStatus()).body(build(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidationException(MethodArgumentNotValidException e) {
        String errorMessage = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        log.warn("Validation failed: {}", errorMessage);
        return ResponseEntity.status(ErrorCode.INVALID_PARAMETER.getHttpStatus())
                .body(build(ErrorCode.INVALID_PARAMETER.getCode(), errorMessage));
    }

    /** JSON 파싱 실패 등 요청 본문 자체가 잘못된 경우 (예전에는 500으로 응답되던 케이스). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDto> handleUnreadableBody(HttpMessageNotReadableException e) {
        log.warn("Unreadable request body: {}", e.getMessage());
        return ResponseEntity.status(ErrorCode.INVALID_PARAMETER.getHttpStatus())
                .body(build(ErrorCode.INVALID_PARAMETER.getCode(), "Malformed request body"));
    }

    /** X-API-KEY 헤더가 아예 없는 경우. */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponseDto> handleMissingHeader(MissingRequestHeaderException e) {
        return ResponseEntity.status(ErrorCode.INVALID_API_KEY.getHttpStatus())
                .body(build(ErrorCode.INVALID_API_KEY.getCode(), "Missing API Key"));
    }

    /**
     * 서비스 계층에서 처리하지 못한 제약 위반의 최후 방어선.
     * 주문 중복(customerTrxId)은 OrderService가 멱등 처리하므로 여기까지 오는 건 예상 밖의 충돌이다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDto> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        log.warn("Data integrity violation: {}", e.getMessage());
        return build(ErrorCode.DATA_CONFLICT);
    }

    private ResponseEntity<ErrorResponseDto> build(ErrorCode errorCode) {
        return ResponseEntity.status(errorCode.getHttpStatus()).body(build(errorCode.getCode(), errorCode.getMessage()));
    }

    private ErrorResponseDto build(String code, String message) {
        return ErrorResponseDto.builder().resCode(code).resMsg(message).build();
    }
}
