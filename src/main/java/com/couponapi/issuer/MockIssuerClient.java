package com.couponapi.issuer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 실제 발행처 연동 전까지 모든 발행처를 대신하는 가상 클라이언트.
 * 가상의 핀을 만들어 주고, 발행처 응답 지연을 흉내 내기 위해 임의 시간 동안 잠든다.
 * 지연 범위는 app.issuer.mock.min-latency-ms / max-latency-ms 로 조절한다. (기본 0.2~2초)
 *
 * 가장 낮은 우선순위라, 같은 발행처를 담당하는 실제 어댑터가 있으면 그쪽이 먼저 선택된다.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class MockIssuerClient implements IssuerClient {

    private final long minLatencyMs;
    private final long maxLatencyMs;

    public MockIssuerClient(@Value("${app.issuer.mock.min-latency-ms:200}") long minLatencyMs,
                            @Value("${app.issuer.mock.max-latency-ms:2000}") long maxLatencyMs) {
        if (minLatencyMs < 0 || maxLatencyMs < minLatencyMs) {
            throw new IllegalArgumentException("mock 지연 범위가 올바르지 않습니다: " + minLatencyMs + " ~ " + maxLatencyMs);
        }
        this.minLatencyMs = minLatencyMs;
        this.maxLatencyMs = maxLatencyMs;
    }

    @Override
    public boolean supports(Long issuerSeq) {
        return true;
    }

    @Override
    public IssuedPin issue(IssueRequest request) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(minLatencyMs, maxLatencyMs + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("발행처 호출이 중단되었습니다.", e);
        }

        String pin = "PIN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        // 밀리초 시각은 병렬 스레드에서 충돌해 유니크 제약(issuerTrxId)을 깨뜨릴 수 있어 UUID를 사용한다.
        String issuerTrxId = "ISS-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        return new IssuedPin(pin, issuerTrxId);
    }
}
