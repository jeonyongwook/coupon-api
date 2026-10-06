package com.couponapi;

import com.couponapi.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class OrderApiTest extends IntegrationTestSupport {

    @Autowired MockMvc mockMvc;
    @Autowired OrderService orderService;

    private String body(String trxId, int quantity) {
        return """
                {"customerKey":"%s","customerTrxId":"%s","customerGoodsCode":"%s","quantity":%d}
                """.formatted(CUSTOMER_KEY, trxId, GOODS_CODE, quantity);
    }

    @Test
    @DisplayName("POST /orders: 정상 접수는 202 + trxId")
    void createOrderReturnsAccepted() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("X-API-KEY", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("A-001", 2)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.resCode").value("0000"))
                .andExpect(jsonPath("$.trxId").isNotEmpty());
    }

    @Test
    @DisplayName("POST /orders: API 키 헤더가 없으면 401 E010")
    void missingApiKeyHeader() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("A-002", 1)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.resCode").value("E010"));
    }

    @Test
    @DisplayName("POST /orders: 수량이 범위를 벗어나면 400 E001")
    void invalidQuantity() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("X-API-KEY", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("A-003", 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resCode").value("E001"));
    }

    @Test
    @DisplayName("POST /orders: 깨진 JSON은 500이 아니라 400 E001")
    void malformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .header("X-API-KEY", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resCode").value("E001"));
    }

    @Test
    @DisplayName("GET /orders/{trxId}: 본인 주문은 진행 상황과 상세 목록을 돌려준다")
    void getOwnOrder() throws Exception {
        String trxId = orderService.createOrder(request("A-004", 2), API_KEY).getTrxId();

        mockMvc.perform(get("/api/v1/orders/{trxId}", trxId)
                        .param("customerKey", CUSTOMER_KEY)
                        .header("X-API-KEY", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.pendingCount").value(2))
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    @DisplayName("GET /orders/{trxId}: 다른 고객사의 주문은 존재하지 않는 것처럼 404")
    void cannotReadOtherCustomersOrder() throws Exception {
        String trxId = orderService.createOrder(request("A-005", 1), API_KEY).getTrxId();

        mockMvc.perform(get("/api/v1/orders/{trxId}", trxId)
                        .param("customerKey", OTHER_CUSTOMER_KEY)
                        .header("X-API-KEY", OTHER_API_KEY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resCode").value("E005"));
    }

    @Test
    @DisplayName("GET /orders/{trxId}: API 키가 틀리면 401")
    void getWithWrongKey() throws Exception {
        String trxId = orderService.createOrder(request("A-006", 1), API_KEY).getTrxId();

        mockMvc.perform(get("/api/v1/orders/{trxId}", trxId)
                        .param("customerKey", CUSTOMER_KEY)
                        .header("X-API-KEY", "wrong-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.resCode").value("E010"));
    }
}
