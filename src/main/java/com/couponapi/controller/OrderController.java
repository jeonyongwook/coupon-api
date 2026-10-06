package com.couponapi.controller;

import com.couponapi.dto.OrderRequestDto;
import com.couponapi.dto.OrderResponseDto;
import com.couponapi.dto.OrderStatusResponseDto;
import com.couponapi.service.OrderQueryService;
import com.couponapi.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// CORS를 열어야 한다면 origins에 "*" 대신 허용할 도메인을 명시할 것.
// "*"은 자격증명(API 키)을 다루는 API에서는 어떤 출처에서든 요청을 보낼 수 있게 되어 위험하다.
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderQueryService orderQueryService;

    /**
     * 쿠폰 발행 주문 접수 API.
     * 발행은 비동기 배치가 처리하므로 "접수 완료"를 의미하는 202 Accepted로 응답한다.
     * 같은 customerTrxId로 같은 내용을 다시 보내면 처음 접수된 trxId를 그대로 돌려준다(멱등).
     * X-API-KEY 헤더에 고객사 발급 시크릿 키를 함께 전달해야 한다.
     */
    @PostMapping("/orders")
    public ResponseEntity<OrderResponseDto> createOrder(
            @Valid @RequestBody OrderRequestDto requestDto,
            @RequestHeader("X-API-KEY") String apiKey) {
        return ResponseEntity.accepted().body(orderService.createOrder(requestDto, apiKey));
    }

    /**
     * 주문 조회 API. 발행 진행 상황과 발행된 핀을 확인한다.
     * 본인 고객사의 주문만 조회할 수 있다.
     */
    @GetMapping("/orders/{trxId}")
    public ResponseEntity<OrderStatusResponseDto> getOrder(
            @PathVariable("trxId") String trxId,
            @RequestParam("customerKey") String customerKey,
            @RequestHeader("X-API-KEY") String apiKey) {
        return ResponseEntity.ok(orderQueryService.getOrder(customerKey, apiKey, trxId));
    }
}
