package com.couponapi.batch;

import com.common.config.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueBatch {

    private final SystemConfigService systemConfigService;

    private final CouponIssueClaimer claimer;

    private final CouponIssueResultWriter resultWriter;

    // AsyncConfig에 정의된 발행 전용 스레드풀 (CORE_POOL_SIZE / MAX_POOL_SIZE 설정과 연결됨)
    @Qualifier("couponExecutor")
    private final Executor couponExecutor;

    private static final String CONFIG_GROUP = "BATCH_COUPON_ISSUE";
    private static final int DEFAULT_ISSUE_TRY_COUNT = 10;
    private static final int DEFAULT_ISSUE_TIMEOUT_SEC = 5;
    // PROCESSING 상태로 이 시간(초) 넘게 머물면 선점한 서버가 죽은 것으로 보고 READY로 되돌린다.
    // 발행 타임아웃(기본 5초) + 큐 대기 시간보다 충분히 길어야 정상 처리 중인 건을 건드리지 않는다.
    private static final int DEFAULT_STUCK_PROCESSING_SEC = 60;

    // 3초마다 실행 (이전 회차가 끝난 시점 기준)
    // 주의 1: 여기에 @Transactional을 걸지 않는다. 외부 발행처 응답을 기다리는 동안
    //         DB 커넥션을 계속 점유하게 되는 것을 막기 위해, 트랜잭션은
    //         CouponIssueClaimer(선점)와 CouponIssueResultWriter(결과 반영)에서만 짧게 사용한다.
    // 주의 2: 같은 건의 중복 발행은 fixedDelay에 기대지 않고 "선점"으로 막는다.
    //         선점(READY -> PROCESSING, SKIP LOCKED)이 커밋된 건은 다른 회차/인스턴스가 집을 수 없다.
    //         fixedDelay는 이제 한 인스턴스 안에서 회차가 겹쳐 스레드풀이 과부하되는 것을 막는 용도다.
    @Scheduled(fixedDelay = 3 * 1000)
    public void processReadyOrders() {
        // 1. 배치 설정 가져오기
        Map<String, String> config = systemConfigService.getConfigMap(CONFIG_GROUP);

        // 2. 선점한 채 멈춰버린 건 복구 (서버 비정상 종료 대비)
        int stuckSec = systemConfigService.getIntOrDefault(config, "STUCK_PROCESSING_SEC", DEFAULT_STUCK_PROCESSING_SEC);
        int recovered = resultWriter.releaseStaleProcessing(LocalDateTime.now().minusSeconds(stuckSec));
        if (recovered > 0) {
            log.warn("PROCESSING 상태로 {}초 넘게 멈춰 있던 {}건을 READY로 복구했습니다.", stuckSec, recovered);
        }

        // 3. 대기 건 선점 (ISSUE_TRY_COUNT 만큼, 오래된 순)
        int tryCount = systemConfigService.getIntOrDefault(config, "ISSUE_TRY_COUNT", DEFAULT_ISSUE_TRY_COUNT);
        List<Long> claimedIds = claimer.claim(tryCount);

        // 4. 핀 발행 시도 (병렬 처리 + 발행 지연 대비 타임아웃)
        if (!claimedIds.isEmpty()) {
            log.info("배치 시작: {}건의 주문을 처리합니다.", claimedIds.size());
            issuePins(claimedIds, config);
        }

        // 5. 모든 상세가 끝난 주문을 COMPLETED로 전환 (집합 단위, 멱등)
        int completed = resultWriter.completeFinishedOrders();
        if (completed > 0) {
            log.info("주문 {}건을 COMPLETED로 전환했습니다.", completed);
        }
    }

    /**
     * 선점한 건들을 발행처에 병렬로 요청하고, 결과(성공/지연/실패)를 반영
     */
    private void issuePins(List<Long> orderDetailSeqs, Map<String, String> config) {
        long timeoutSec = systemConfigService.getIntOrDefault(config, "ISSUE_TIMEOUT_SEC", DEFAULT_ISSUE_TIMEOUT_SEC);

        // 발행 요청을 먼저 모두 스레드풀에 던져서 병렬로 진행시킴
        Map<Long, CompletableFuture<PinIssueResult>> futures = new LinkedHashMap<>();
        for (Long orderDetailSeq : orderDetailSeqs) {
            futures.put(orderDetailSeq, submitIssueRequest(orderDetailSeq, timeoutSec));
        }

        // 결과를 기다리며 반영 (반영 자체는 CouponIssueResultWriter의 짧은 트랜잭션에서 수행)
        for (Map.Entry<Long, CompletableFuture<PinIssueResult>> entry : futures.entrySet()) {
            Long orderDetailSeq = entry.getKey();
            try {
                PinIssueResult result = entry.getValue().get();
                applySuccessSafely(orderDetailSeq, result);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RejectedExecutionException) {
                    // 발행처에 요청 자체를 보내지 못했으므로 실패가 아니라 "대기"로 되돌려 다음 회차에 재시도
                    log.warn("발행 요청 미전송(스레드풀 포화), 대기로 되돌립니다: OrderDetailSeq = {}", orderDetailSeq);
                    releaseSafely(orderDetailSeq);
                    continue;
                }
                // orTimeout()으로 인한 타임아웃도 ExecutionException으로 래핑되어 여기로 들어온다
                boolean isTimeout = cause instanceof TimeoutException;
                log.error("쿠폰 발행 실패({}): OrderDetailSeq = {}, 사유 = {}",
                        isTimeout ? "지연" : "오류", orderDetailSeq, cause != null ? cause.getMessage() : e.getMessage());
                applyFailSafely(orderDetailSeq);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("쿠폰 발행 중단: OrderDetailSeq = {}", orderDetailSeq);
                applyFailSafely(orderDetailSeq);
            }
            // 주의: get()/orTimeout 관련 예외만 잡는 것으로는 부족하다. resultWriter가 던지는 예외
            // (DB 제약 위반, 커넥션 문제 등)가 for문 밖으로 전파되면 나머지 건의 결과 반영이 통째로 무산되므로
            // *Safely 메서드 안에서 건별로 흡수한다.
        }
    }

    /** 결과 반영 중 나는 예외(DB 제약 위반 등)를 이 건에서만 흡수한다. */
    private void applySuccessSafely(Long orderDetailSeq, PinIssueResult result) {
        try {
            resultWriter.applySuccess(orderDetailSeq, result.pin(), result.issuerTrxId());
        } catch (Exception e) {
            log.error("발행 성공 결과 반영 중 오류: OrderDetailSeq = {}", orderDetailSeq, e);
        }
    }

    private void applyFailSafely(Long orderDetailSeq) {
        try {
            resultWriter.applyFail(orderDetailSeq);
        } catch (Exception e) {
            log.error("발행 실패 결과 반영 중 오류: OrderDetailSeq = {}", orderDetailSeq, e);
        }
    }

    private void releaseSafely(Long orderDetailSeq) {
        try {
            resultWriter.release(orderDetailSeq);
        } catch (Exception e) {
            log.error("대기 복귀 처리 중 오류: OrderDetailSeq = {}", orderDetailSeq, e);
        }
    }

    /**
     * 발행 요청을 스레드풀에 제출한다.
     * - 스레드풀이 포화되어 RejectedExecutionException이 발생해도 이 건만 처리하고
     *   나머지 건 제출에는 영향을 주지 않는다. (호출부가 READY로 되돌린다)
     * - 타임아웃으로 실패 처리된 뒤에도 발행처 응답이 실제로는 늦게 성공할 수 있다.
     *   orTimeout()은 원본 future 자체를 완료시켜버리므로, 원본(raw future)은
     *   그대로 살려두고 별도의 stage에만 타임아웃을 걸어, 원본이 늦게라도 성공하면
     *   그 결과를 자동으로 반영해 발행 결과가 유실되지 않도록 한다.
     */
    private CompletableFuture<PinIssueResult> submitIssueRequest(Long orderDetailSeq, long timeoutSec) {

        // 작업이 스레드풀 큐에서 빠져나와 실제로 스레드를 잡고 "시작"되는 순간을 알리는 신호.
        // orTimeout()을 이 신호가 완료된 뒤(=실제 처리 시작 시점)에 걸어야, 타임아웃이
        // 제출(submit) 시점이 아니라 건별 실제 처리 시점부터 개별적으로 계산된다.
        CompletableFuture<Void> started = new CompletableFuture<>();

        CompletableFuture<PinIssueResult> rawFuture;
        try {
            rawFuture = CompletableFuture.supplyAsync(() -> {
                started.complete(null);
                return requestPinFromIssuer(orderDetailSeq);
            }, couponExecutor);
        } catch (RejectedExecutionException e) {
            log.error("발행 요청 제출 실패(스레드풀 포화): OrderDetailSeq = {}", orderDetailSeq, e);
            return CompletableFuture.failedFuture(e);
        }

        // rawFuture를 직접 orTimeout()에 넘기면 rawFuture 자신이 타임아웃으로 완료되어버려
        // 이후 실제 응답이 와도 반영할 방법이 없어진다. thenApply로 별도 stage를 만들어
        // 거기에만 타임아웃을 건다.
        CompletableFuture<PinIssueResult> guarded = started.thenCompose(v ->
                rawFuture.thenApply(Function.identity()).orTimeout(timeoutSec, TimeUnit.SECONDS));

        rawFuture.whenComplete((result, ex) -> {
            if (ex == null && guarded.isCompletedExceptionally()) {
                // 이미 타임아웃으로 ISSUE_FAIL 처리된 뒤 실제로는 성공 응답이 늦게 도착한 경우.
                // 이 콜백은 issuePins()의 for문이 끝난 뒤 별도 워커 스레드에서 호출될 수 있으므로
                // 여기서 예외가 나면 아무도 잡아주지 않는다. 반드시 흡수한다.
                log.warn("지연 응답 도착(타임아웃 이후 발행 성공): OrderDetailSeq = {}", orderDetailSeq);
                applySuccessSafely(orderDetailSeq, result);
            }
        });

        return guarded;
    }

    /**
     * 발행처 API 연동 전이므로 가상의 핀을 생성하는 것으로 대체.
     * 실제 발행처는 응답이 늦어질 수 있어 임의 지연을 흉내낸다.
     */
    private PinIssueResult requestPinFromIssuer(Long orderDetailSeq) {
        try {
            long fakeLatencyMs = 200 + (long) (Math.random() * 1800); // 발행처 응답 지연 시뮬레이션 (0.2~2초)
            Thread.sleep(fakeLatencyMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("발행처 호출이 중단되었습니다.", e);
        }

        String mockPin = "PIN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        // 밀리초 시각은 병렬 스레드에서 충돌해 유니크 제약(issuerTrxId)을 깨뜨릴 수 있어 UUID를 사용한다.
        String mockIssuerTrxId = "ISS-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        return new PinIssueResult(mockPin, mockIssuerTrxId);
    }

    /**
     * 발행처로부터 받은 핀 발행 결과
     */
    private record PinIssueResult(String pin, String issuerTrxId) {
    }
}
