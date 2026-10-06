package com.couponapi;

import com.couponapi.entity.OrderDetail;
import com.couponapi.entity.OrderDetailStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** 상태 전이 규칙을 스프링·DB 없이 검증하는 단위 테스트. */
class OrderDetailTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final LocalDate END = LocalDate.of(2026, 1, 31);

    private OrderDetail newDetail() {
        return OrderDetail.builder().orderSeq(1L).build();
    }

    @Test
    @DisplayName("READY만 선점할 수 있고, 이미 선점한 건은 다시 선점되지 않는다")
    void onlyReadyCanBeClaimed() {
        OrderDetail detail = newDetail();

        assertThat(detail.markProcessing()).isTrue();
        assertThat(detail.getStatus()).isEqualTo(OrderDetailStatus.PROCESSING);
        assertThat(detail.markProcessing()).isFalse();
    }

    @Test
    @DisplayName("PROCESSING만 READY로 되돌릴 수 있다")
    void onlyProcessingCanBeReleased() {
        OrderDetail detail = newDetail();
        assertThat(detail.releaseToReady()).isFalse();

        detail.markProcessing();
        assertThat(detail.releaseToReady()).isTrue();
        assertThat(detail.getStatus()).isEqualTo(OrderDetailStatus.READY);
    }

    @Test
    @DisplayName("실패 건에 늦게 온 성공은 UNUSED로 바뀌고, 성공한 건은 실패로 덮어쓰지 않는다")
    void lateSuccessUpgradesFailButSuccessIsFinal() {
        OrderDetail detail = newDetail();
        detail.markProcessing();

        assertThat(detail.issueFail()).isTrue();
        assertThat(detail.getStatus()).isEqualTo(OrderDetailStatus.ISSUE_FAIL);

        assertThat(detail.issueSuccess("PIN-1", "ISS-1", START, END)).isTrue();
        assertThat(detail.getStatus()).isEqualTo(OrderDetailStatus.UNUSED);

        assertThat(detail.issueFail()).isFalse();
        assertThat(detail.getStatus()).isEqualTo(OrderDetailStatus.UNUSED);
    }

    @Test
    @DisplayName("성공이 중복으로 와도 처음 받은 핀이 유지된다")
    void duplicateSuccessKeepsFirstPin() {
        OrderDetail detail = newDetail();
        detail.issueSuccess("PIN-FIRST", "ISS-FIRST", START, END);

        assertThat(detail.issueSuccess("PIN-SECOND", "ISS-SECOND", START, END)).isFalse();
        assertThat(detail.getPin()).isEqualTo("PIN-FIRST");
        assertThat(detail.getIssuerTrxId()).isEqualTo("ISS-FIRST");
    }
}
