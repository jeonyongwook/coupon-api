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
 * 운영에서는 SEED_ENABLED=false 로 끈다.
 *
 * 데모 고객사: customerKey = DEMO_CUSTOMER, X-API-KEY = demo-secret-key-1234, goodsCode = GOODS001
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataInitializer implements ApplicationRunner {

    public static final String DEMO_CUSTOMER_KEY = "DEMO_CUSTOMER";
    public static final String DEMO_API_KEY = "demo-secret-key-1234";
    public static final String DEMO_GOODS_CODE = "GOODS001";

    private static final String CONFIG_GROUP = "BATCH_COUPON_ISSUE";

    private final CustomerRepository customerRepository;
    private final IssuerRepository issuerRepository;
    private final CouponRepository couponRepository;
    private final SystemConfigRepository systemConfigRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedCustomer();
        seedCoupon();
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

    private void seedCoupon() {
        if (couponRepository.findByCustomerGoodsCodeAndStatus(DEMO_GOODS_CODE, UseStatus.OK).isPresent()) {
            return;
        }
        Issuer issuer = issuerRepository.save(Issuer.builder()
                .name("데모 발행처")
                .businessNo("000-00-00000")
                .status(UseStatus.OK)
                .build());

        couponRepository.save(Coupon.builder()
                .issuerSeq(issuer.getIssuerSeq())
                .name("데모 아메리카노 쿠폰")
                .price(BigDecimal.valueOf(4500))
                .validDays(30)
                .status(UseStatus.OK)
                .issuerGoodsCode("ISSUER-GOODS-001")
                .customerGoodsCode(DEMO_GOODS_CODE)
                .build());
        log.info("시드 쿠폰 상품 생성: customerGoodsCode={}", DEMO_GOODS_CODE);
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
