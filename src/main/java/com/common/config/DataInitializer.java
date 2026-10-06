package com.common.config;

import com.common.entity.SystemConfig;
import com.common.repository.SystemConfigRepository;
import com.common.security.ApiKeyHasher;
import com.couponapi.entity.Coupon;
import com.couponapi.entity.Customer;
import com.couponapi.entity.Issuer;
import com.couponapi.entity.UseStatus;
import com.couponapi.repository.CouponRepository;
import com.couponapi.repository.CustomerRepository;
import com.couponapi.repository.IssuerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 로컬 실행/데모용 시드 데이터. app.seed.enabled=true 일 때만 동작하며 여러 번 실행해도 중복 생성하지 않는다.
 * 기본값은 false이고 local 프로파일에서만 켜진다. (알려진 데모 키가 운영에 생기지 않도록)
 *
 * 데모 고객사: customerKey = DEMO_CUSTOMER, X-API-KEY = demo-secret-key-1234
 * 데모 상품: GOODS001 (발행처 A, 유효 30일), GOODS002 (발행처 B, 유효 7일) - 다중 발행처 예시
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataInitializer implements ApplicationRunner {

    public static final String DEMO_CUSTOMER_KEY = "DEMO_CUSTOMER";
    public static final String DEMO_API_KEY = "demo-secret-key-1234";
    public static final String DEMO_GOODS_CODE = "GOODS001";
    public static final String DEMO_GOODS_CODE_B = "GOODS002";

    private static final String CONFIG_GROUP = "BATCH_COUPON_ISSUE";

    private final CustomerRepository customerRepository;
    private final IssuerRepository issuerRepository;
    private final CouponRepository couponRepository;
    private final SystemConfigRepository systemConfigRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedCustomer();
        seedCoupon("데모 발행처 A", "000-00-00000", "데모 아메리카노 쿠폰", 4500, 30, "ISSUER-GOODS-001", DEMO_GOODS_CODE);
        seedCoupon("데모 발행처 B", "000-00-00001", "데모 케이크 쿠폰", 6000, 7, "ISSUER-GOODS-002", DEMO_GOODS_CODE_B);
        seedConfig("ISSUE_TRY_COUNT", "10", "배치 1회차에 선점해서 발행할 최대 건수");
        seedConfig("ISSUE_TIMEOUT_SEC", "5", "발행처 응답 대기 타임아웃(초)");
        seedConfig("CORE_POOL_SIZE", "5", "발행 스레드풀 core 크기 (1분마다 재반영)");
        seedConfig("MAX_POOL_SIZE", "10", "발행 스레드풀 max 크기 (큐가 가득 찬 뒤에만 사용됨)");
        seedConfig("STUCK_PROCESSING_SEC", "60", "PROCESSING으로 멈춘 건을 READY로 복구하는 기준(초)");
    }

    private void seedCustomer() {
        if (customerRepository.findByCustomerKey(DEMO_CUSTOMER_KEY).isPresent()) {
            return;
        }
        customerRepository.save(Customer.builder()
                .customerKey(DEMO_CUSTOMER_KEY)
                .secretKeyHash(ApiKeyHasher.sha256Hex(DEMO_API_KEY))
                .name("데모 고객사")
                .status(UseStatus.OK)
                .build());
        log.info("시드 고객사 생성: customerKey={}", DEMO_CUSTOMER_KEY);
    }

    private void seedCoupon(String issuerName, String businessNo, String couponName, int price, int validDays,
                            String issuerGoodsCode, String customerGoodsCode) {
        if (couponRepository.findByCustomerGoodsCodeAndStatus(customerGoodsCode, UseStatus.OK).isPresent()) {
            return;
        }
        Issuer issuer = issuerRepository.save(Issuer.builder()
                .name(issuerName)
                .businessNo(businessNo)
                .status(UseStatus.OK)
                .build());

        couponRepository.save(Coupon.builder()
                .issuerSeq(issuer.getIssuerSeq())
                .name(couponName)
                .price(BigDecimal.valueOf(price))
                .validDays(validDays)
                .status(UseStatus.OK)
                .issuerGoodsCode(issuerGoodsCode)
                .customerGoodsCode(customerGoodsCode)
                .build());
        log.info("시드 쿠폰 상품 생성: customerGoodsCode={}, issuer={}", customerGoodsCode, issuerName);
    }

    private void seedConfig(String key, String value, String description) {
        if (systemConfigRepository.findById_GroupKeyAndId_ConfigKey(CONFIG_GROUP, key).isPresent()) {
            return;
        }
        systemConfigRepository.save(SystemConfig.builder()
                .groupKey(CONFIG_GROUP)
                .configKey(key)
                .configValue(value)
                .description(description)
                .build());
    }
}
