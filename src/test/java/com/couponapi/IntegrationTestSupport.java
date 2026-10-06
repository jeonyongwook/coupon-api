package com.couponapi;

import com.common.security.ApiKeyHasher;
import com.couponapi.dto.OrderRequestDto;
import com.couponapi.entity.Coupon;
import com.couponapi.entity.Customer;
import com.couponapi.entity.Issuer;
import com.couponapi.entity.UseStatus;
import com.couponapi.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

/**
 * 통합 테스트 공통 기반. 인메모리 H2에 실제 스프링 컨텍스트를 띄우고,
 * 테스트마다 데이터를 비운 뒤 고객사 2곳 + 쿠폰 상품 1개를 만든다.
 * (트랜잭션 커밋/롤백과 유니크 제약 동작을 그대로 검증해야 하므로 @Transactional 롤백 방식은 쓰지 않는다)
 */
@SpringBootTest
abstract class IntegrationTestSupport {

    protected static final String CUSTOMER_KEY = "TEST_CUSTOMER_1";
    protected static final String API_KEY = "test-secret-key-1";
    protected static final String OTHER_CUSTOMER_KEY = "TEST_CUSTOMER_2";
    protected static final String OTHER_API_KEY = "test-secret-key-2";
    protected static final String GOODS_CODE = "GOODS-T1";

    @Autowired protected CustomerRepository customerRepository;
    @Autowired protected IssuerRepository issuerRepository;
    @Autowired protected CouponRepository couponRepository;
    @Autowired protected OrderRepository orderRepository;
    @Autowired protected OrderDetailRepository orderDetailRepository;

    @BeforeEach
    void resetData() {
        orderDetailRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        couponRepository.deleteAllInBatch();
        issuerRepository.deleteAllInBatch();
        customerRepository.deleteAllInBatch();

        saveCustomer(CUSTOMER_KEY, API_KEY, UseStatus.OK);
        saveCustomer(OTHER_CUSTOMER_KEY, OTHER_API_KEY, UseStatus.OK);

        Issuer issuer = issuerRepository.save(Issuer.builder()
                .name("테스트 발행처").businessNo("000-00-00000").status(UseStatus.OK).build());
        couponRepository.save(Coupon.builder()
                .issuerSeq(issuer.getIssuerSeq())
                .name("테스트 쿠폰")
                .price(BigDecimal.valueOf(1000))
                .validDays(30)
                .status(UseStatus.OK)
                .issuerGoodsCode("ISSUER-T1")
                .customerGoodsCode(GOODS_CODE)
                .build());
    }

    protected Customer saveCustomer(String customerKey, String rawApiKey, UseStatus status) {
        return customerRepository.save(Customer.builder()
                .customerKey(customerKey)
                .secretKeyHash(ApiKeyHasher.sha256Hex(rawApiKey))
                .name(customerKey)
                .status(status)
                .build());
    }

    protected OrderRequestDto request(String customerTrxId, int quantity) {
        OrderRequestDto dto = new OrderRequestDto();
        dto.setCustomerKey(CUSTOMER_KEY);
        dto.setCustomerTrxId(customerTrxId);
        dto.setCustomerGoodsCode(GOODS_CODE);
        dto.setQuantity(quantity);
        return dto;
    }
}
