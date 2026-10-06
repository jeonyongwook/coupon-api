package com.couponapi.issuer;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 발행처(issuerSeq)에 맞는 {@link IssuerClient}를 찾는다.
 * 스프링이 주입하는 목록은 {@code @Order} 순서이므로, 먼저 {@link IssuerClient#supports}에 응답하는
 * 구현체가 선택된다. (특정 발행처 전용 어댑터는 높은 우선순위, 범용 mock은 가장 낮은 우선순위)
 */
@Component
public class IssuerClientRegistry {

    private final List<IssuerClient> clients;

    public IssuerClientRegistry(List<IssuerClient> clients) {
        this.clients = List.copyOf(clients);
    }

    public IssuerClient resolve(Long issuerSeq) {
        return clients.stream()
                .filter(client -> client.supports(issuerSeq))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("발행처를 담당하는 클라이언트가 없습니다: issuerSeq = " + issuerSeq));
    }
}
