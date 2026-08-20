package com.couponapi.controller;

import com.couponapi.dto.OrderRequestDto;
import com.couponapi.dto.OrderResponseDto;
import com.couponapi.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// CORS를 열어야 한다면 origins에 "*" 대신 허용할 도메인을 명시할 것.
// "*"은 자격증명(API 키)을 다루는 API에서는 어떤 출처에서든 요청을 보낼 수 있게 되어 위험하다.
// @CrossOrigin(origins = {"https://your-allowed-domain.com"})
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * 쿠폰 발행 주문 요청 API
     * X-API-KEY 헤더에 고객사 발급 시크릿 키를 함께 전달해야 한다.
     */
    @PostMapping("/order")
    public ResponseEntity<OrderResponseDto> createOrder(
            @Valid @RequestBody OrderRequestDto requestDto,
            @RequestHeader("X-API-KEY") String apiKey) {
        OrderResponseDto response = orderService.createOrder(requestDto, apiKey);
        return ResponseEntity.ok(response);
    }
}
