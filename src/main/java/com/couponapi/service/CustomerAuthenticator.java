package com.couponapi.service;

import com.common.exception.CouponApiException;
import com.common.exception.ErrorCode;
import com.common.security.ApiKeyHasher;
import com.couponapi.entity.Customer;
import com.couponapi.entity.UseStatus;
import com.couponapi.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * customerKey(식별자) + X-API-KEY(시크릿) 쌍으로 고객사를 인증한다.
 * 존재하지 않는 customerKey와 틀린 API 키를 같은 응답(E010/401)으로 돌려줘서
 * 유효한 customerKey를 추측할 수 없게 한다.
 */
@Component
@RequiredArgsConstructor
public class CustomerAuthenticator {

    // 존재하지 않는 고객사일 때도 해시 비교를 한 번 수행해 응답 시간 차이를 줄이기 위한 더미 값
    private static final String DUMMY_HASH = ApiKeyHasher.sha256Hex("dummy-secret-for-unknown-customer");

    private final CustomerRepository customerRepository;

    public Customer authenticate(String customerKey, String apiKey) {
        Customer customer = customerRepository.findByCustomerKey(customerKey).orElse(null);

        String expectedHash = customer != null ? customer.getSecretKeyHash() : DUMMY_HASH;
        boolean keyMatches = apiKey != null
                && ApiKeyHasher.constantTimeEquals(ApiKeyHasher.sha256Hex(apiKey), expectedHash);

        if (customer == null || !keyMatches) {
            throw new CouponApiException(ErrorCode.INVALID_API_KEY);
        }
        if (customer.getStatus() != UseStatus.OK) {
            throw new CouponApiException(ErrorCode.INACTIVE_CUSTOMER);
        }
        return customer;
    }
}
