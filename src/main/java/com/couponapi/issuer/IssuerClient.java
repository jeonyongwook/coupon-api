package com.couponapi.issuer;

/**
 * 발행처 연동 지점.
 *
 * 발행처마다 API 규격이 다르므로 발행처별 구현체를 두고, {@link IssuerClientRegistry}가
 * {@code issuerSeq}로 알맞은 구현체를 고른다. 새 발행처를 붙일 때는 이 인터페이스를 구현한
 * 빈을 추가하면 되고, 선점·타임아웃·지연 응답 반영 같은 배치 쪽 로직은 바꾸지 않는다.
 *
 * 구현체가 지켜야 할 것:
 * - {@link #issue}는 블로킹 호출이다. 배치가 스레드풀에서 실행하며 타임아웃은 배치가 건다.
 * - 실패는 런타임 예외로 던진다. 던져진 예외는 그 건만 ISSUE_FAIL로 처리된다.
 * - 실제 연동 시에는 {@code orderDetailSeq}를 발행처 요청의 멱등 키로 함께 보내, 응답을 못 받아도
 *   조회·취소 대상을 특정할 수 있게 한다. (README "한계와 다음 단계" 참고)
 */
public interface IssuerClient {

    /** 이 구현체가 해당 발행처를 담당하는지 여부. */
    boolean supports(Long issuerSeq);

    /** 발행처에 핀 발행을 요청한다. */
    IssuedPin issue(IssueRequest request);

    /** 발행 요청. {@code issuerGoodsCode}는 발행처 쪽 상품 코드다. */
    record IssueRequest(Long orderDetailSeq, Long issuerSeq, String issuerGoodsCode) {
    }

    /** 발행처가 돌려준 핀과 발행처 거래번호. */
    record IssuedPin(String pin, String issuerTrxId) {
    }
}
