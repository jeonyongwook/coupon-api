package com.couponapi.controller;

import com.couponapi.dto.OrderRequestDto;
import com.couponapi.dto.OrderResponseDto;
import com.couponapi.entity.Order;
import com.couponapi.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// @CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * 쿠폰 발행 주문 요청 API
     */
    @PostMapping("/order")
    public ResponseEntity<OrderResponseDto> createOrder(@Valid @RequestBody OrderRequestDto requestDto) {
        OrderResponseDto response = orderService.createOrder(requestDto);
        return ResponseEntity.ok(response);
    }
}