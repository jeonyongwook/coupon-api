package com.couponapi.batch;

import com.common.config.SystemConfigService;
import com.couponapi.entity.OrderDetail;
import com.couponapi.repository.OrderDetailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
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

    private final OrderDetailRepository orderDetailRepository;

    private final SystemConfigService systemConfigService;

    private final CouponIssueResultWriter resultWriter;

    // AsyncConfig에 정의된 발행 전용 스레드풀 (CORE_POOL_SIZE / MAX_POOL_SIZE 설정과 연결됨)
    @Qualifier("couponExecutor")
    private final Executor couponExecutor;

    private static final String CONFIG_GROUP = "BATCH_COUPON_ISSUE";
    private static final int DEFAULT_ISSUE_TRY_COUNT = 10;
    private static final int DEFAULT_ISSUE_TIMEOUT_SEC = 5;

    // 10초마다 실행 (밀리초 단위)
    // 주의 1: 여기에 @Transactional을 걸지 않는다. 외부 발행처 응답을 기다리는 동안
    //         DB 커넥션을 계속 점유하게 되는 것을 막기 위해, 트랜잭션은
    //         CouponIssueResultWriter에서 결과 반영 단위로만 짧게 사용한다.
    // 주의 2: 반드시 fixedDelay를 유지해야 한다. issuePins()는 모든 발행 futures가
    //         (성공/실패/타임아웃으로) 완료될 때까지 블로킹으로 기다린 뒤에야 리턴하므로,
    //         fixedDelay 덕분에 이전 배치 회차가 끝나기 전에는 다음 회차가 시작되지 않는다.
    //         이 보장이 없으면(예: fixedRate로 변경하거나 스케줄러를 멀티스레드로 돌리면)
    //         아직 처리 중(status=READY)인 같은 주문을 다음 회차가 또 집어서 발행처에
    //         핀을 중복으로 요청할 수 있다.
    @Scheduled(fixedDelay = 3 * 1000)
    public void processReadyOrders() {
        // 1. 배치 설정 가져오기
        Map<String, String> config = systemConfigService.getConfigMap(CONFIG_GROUP);

        // 2. 대기 중인 상세 주문 가져오기 (ISSUE_TRY_COUNT 만큼, config를 파라미터로 전달)
        List<OrderDetail> readyDetails = getReadyOrders(config);

        if (readyDetails.isEmpty()) {
            return;
        }

        log.info("배치 시작: {}건의 주문을 처리합니다.", readyDetails.size());

        // 3. 핀 발행 시도 (병렬 처리 + 발행 지연 대비 타임아웃)
        issuePins(readyDetails, config);
    }

    /**
     * ISSUE_TRY_COUNT 설정값만큼 READY 상태 상세주문을 오래된 순으로 조회
     */
    private List<OrderDetail> getReadyOrders(Map<String, String> config) {
        int tryCount = systemConfigService.getIntOrDefault(config, "ISSUE_TRY_COUNT", DEFAULT_ISSUE_TRY_COUNT);
        Pageable pageable = PageRequest.of(0, tryCount, Sort.by(Sort.Direction.ASC, "regDate"));
        return orderDetailRepository.findByStatusOrderByRegDateAsc("READY", pageable);
    }

    /**
     * 대기 주문 리스트를 발행처에 병렬로 요청하고, 결과(성공/지연/실패)를 반영
     */
    private void issuePins(List<OrderDetail> readyDetails, Map<String, String> config) {
        long timeoutSec = systemConfigService.getIntOrDefault(config, "ISSUE_TIMEOUT_SEC", DEFAULT_ISSUE_TIMEOUT_SEC);

        // 3-1. 발행 요청을 먼저 모두 스레드풀에 던져서 병렬로 진행시킴
        //      (엔티티가 아니라 orderDetailSeq를 키로 사용 - 트랜잭션이 끝나면
        //       엔티티는 detached 상태가 되므로 이후에는 id로만 다뤄야 안전함)
        Map<Long, CompletableFuture<PinIssueResult>> futures = new LinkedHashMap<>();
        for (OrderDetail detail : readyDetails) {
            futures.put(detail.getOrderDetailSeq(), submitIssueRequest(detail, timeoutSec));
        }

        // 3-2. 결과를 기다리며 반영 (반영 자체는 CouponIssueResultWriter의 짧은 트랜잭션에서 수행)
        for (Map.Entry<Long, CompletableFuture<PinIssueResult>> entry : futures.entrySet()) {
            Long orderDetailSeq = entry.getKey();
            try {
                PinIssueResult result = entry.getValue().get();
                applySuccessSafely(orderDetailSeq, result);
            } catch (ExecutionException e) {
                // orTimeout()으로 인한 타임아웃도 ExecutionException으로 래핑되어 여기로 들어온다 (원인은 e.getCause()가 TimeoutException)
                boolean isTimeout = e.getCause() instanceof TimeoutException;
                log.error("쿠폰 발행 실패({}): OrderDetailSeq = {}, 사유 = {}",
                        isTimeout ? "지연" : "오류", orderDetailSeq, e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
                applyFailSafely(orderDetailSeq);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("쿠폰 발행 중단: OrderDetailSeq = {}", orderDetailSeq);
                applyFailSafely(orderDetailSeq);
            }
            // 주의: get()/orTimeout 관련 예외만 잡는 것으로는 부족하다. resultWriter.applySuccess/applyFail
            // 자체가 던지는 예외(예: pin/issuerTrxId 유니크 제약 위반, DB 커넥션 문제 등)는 위 catch 블록에
            // 걸리지 않으므로, 그 예외를 for문 밖으로 전파시키지 않도록 applySuccessSafely/applyFailSafely
            // 내부에서 한 번 더 감싼다. 그렇지 않으면 한 건의 DB 오류로 이번 배치의 나머지 건들 결과 반영이
            // 통째로 무산된다 (이미 발행처에서는 성공했는데 우리 시스템에는 반영이 안 되는 상태로 남아,
            // 다음 배치에서 같은 주문에 중복 발행 요청이 나갈 위험이 있다).
        }
    }

    /**
     * resultWriter.applySuccess 호출 자체에서 나는 예외(DB 제약 위반 등)를 이 건에서만 흡수하고,
     * 나머지 건들의 결과 반영에는 영향을 주지 않는다.
     */
    private void applySuccessSafely(Long orderDetailSeq, PinIssueResult result) {
        try {
            resultWriter.applySuccess(orderDetailSeq, result.pin(), result.issuerTrxId());
        } catch (Exception e) {
            log.error("발행 성공 결과 반영 중 오류: OrderDetailSeq = {}", orderDetailSeq, e);
        }
    }

    /**
     * resultWriter.applyFail 호출 자체에서 나는 예외를 이 건에서만 흡수하고,
     * 나머지 건들의 결과 반영에는 영향을 주지 않는다.
     */
    private void applyFailSafely(Long orderDetailSeq) {
        try {
            resultWriter.applyFail(orderDetailSeq);
        } catch (Exception e) {
            log.error("발행 실패 결과 반영 중 오류: OrderDetailSeq = {}", orderDetailSeq, e);
        }
    }

    /**
     * 발행 요청을 스레드풀에 제출한다.
     * - 스레드풀이 포화되어 RejectedExecutionException이 발생해도 이 건만 실패 처리하고
     *   나머지 건 제출에는 영향을 주지 않는다. (기존에는 예외가 for문 밖으로 전파되어
     *   이미 제출된 futures들의 결과 처리가 통째로 무산되는 문제가 있었음)
     * - 타임아웃으로 실패 처리된 뒤에도 발행처 응답이 실제로는 늦게 성공할 수 있다.
     *   orTimeout()은 원본 future 자체를 완료시켜버리므로, 원본(raw future)은
     *   그대로 살려두고 별도의 stage에만 타임아웃을 걸어, 원본이 늦게라도 성공하면
     *   그 결과를 자동으로 반영해 발행 결과가 유실되지 않도록 한다.
     */
    private CompletableFuture<PinIssueResult> submitIssueRequest(OrderDetail detail, long timeoutSec) {
        Long orderDetailSeq = detail.getOrderDetailSeq();

        // 작업이 스레드풀 큐에서 빠져나와 실제로 스레드를 잡고 "시작"되는 순간을 알리는 신호.
        // orTimeout()을 이 신호가 완료된 뒤(=실제 처리 시작 시점)에 걸어야, 타임아웃이
        // 제출(submit) 시점이 아니라 건별 실제 처리 시점부터 개별적으로 계산된다.
        // 그렇지 않으면 큐 대기 시간이 길어질수록 데드라인이 제출 시점 기준으로 고정되어
        // 여러 건이 한꺼번에(덩어리로) 타임아웃 나는 문제가 생긴다.
        CompletableFuture<Void> started = new CompletableFuture<>();

        CompletableFuture<PinIssueResult> rawFuture;
        try {
            rawFuture = CompletableFuture.supplyAsync(() -> {
                started.complete(null);
                return requestPinFromIssuer(detail);
            }, couponExecutor);
        } catch (RejectedExecutionException e) {
            log.error("발행 요청 제출 실패(스레드풀 포화): OrderDetailSeq = {}", orderDetailSeq, e);
            return CompletableFuture.failedFuture(e);
        }

        // rawFuture를 직접 orTimeout()에 넘기면 rawFuture 자신이 타임아웃으로 완료되어버려
        // 이후 실제 응답이 와도 반영할 방법이 없어진다. thenApply로 별도 stage를 만들어
        // 거기에만 타임아웃을 건다. 이 stage 생성 및 orTimeout() 호출 자체를 started 완료
        // 이후로 미뤄서(thenCompose), 데드라인 계산 시작점을 "실제 처리 시작 시점"으로 맞춘다.
        CompletableFuture<PinIssueResult> guarded = started.thenCompose(v ->
                rawFuture.thenApply(Function.identity()).orTimeout(timeoutSec, TimeUnit.SECONDS));

        rawFuture.whenComplete((result, ex) -> {
            if (ex == null && guarded.isCompletedExceptionally()) {
                // 이미 타임아웃으로 ISSUE_FAIL 처리된 뒤 실제로는 성공 응답이 늦게 도착한 경우.
                // 이 콜백은 이미 issuePins()의 for문이 끝난 뒤, 별도 워커 스레드에서 호출될 수
                // 있으므로 여기서 예외가 나면 아무도 잡아주지 않고 조용히 사라진다. 반드시 흡수한다.
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
    private PinIssueResult requestPinFromIssuer(OrderDetail detail) {
        try {
            long fakeLatencyMs = 200 + (long) (Math.random() * 1800); // 발행처 응답 지연 시뮬레이션 (0.2~2초)
            Thread.sleep(fakeLatencyMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("발행처 호출이 중단되었습니다.", e);
        }

        String mockPin = "PIN-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        String mockIssuerTrxId = "ISS-" + System.currentTimeMillis();
        return new PinIssueResult(mockPin, mockIssuerTrxId);
    }

    /**
     * 발행처로부터 받은 핀 발행 결과
     */
    private record PinIssueResult(String pin, String issuerTrxId) {
    }
}