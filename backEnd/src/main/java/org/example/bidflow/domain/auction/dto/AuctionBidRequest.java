package org.example.bidflow.domain.auction.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AuctionBidRequest {
    private final Long auctionId;
    private final Integer amount;
    // 쿠키 기반 인증으로 변경하여 token 필드 제거
    // WebSocket 세션에서 사용자 정보를 가져옴
}
