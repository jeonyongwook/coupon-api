package com.couponapi;

import com.couponapi.issuer.IssuerClient;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 테스트용 가짜 발행처. 테스트가 발행처(issuerSeq)별로 응답 동작(지연, 실패, 핀 값)을 직접 정한다.
 * 등록하지 않은 발행처는 담당하지 않으므로(supports=false) 기본 MockIssuerClient가 처리한다.
 * 가장 높은 우선순위라, 등록된 발행처에 한해 mock보다 먼저 선택된다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ScriptedIssuerClient implements IssuerClient {

    @FunctionalInterface
    public interface Handler {
        IssuedPin handle(IssueRequest request);
    }

    private final Map<Long, Handler> handlers = new ConcurrentHashMap<>();

    public void on(Long issuerSeq, Handler handler) {
        handlers.put(issuerSeq, handler);
    }

    public void reset() {
        handlers.clear();
    }

    @Override
    public boolean supports(Long issuerSeq) {
        return handlers.containsKey(issuerSeq);
    }

    @Override
    public IssuedPin issue(IssueRequest request) {
        return handlers.get(request.issuerSeq()).handle(request);
    }

    /** 발행처 응답 지연을 흉내 내는 sleep. */
    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
