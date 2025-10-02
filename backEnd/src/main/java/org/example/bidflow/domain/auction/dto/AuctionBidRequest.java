package org.example.bidflow.domain.auction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuctionBidRequest {
    private Long auctionId;
    private Integer amount;
    // 쿠키 기반 인증으로 변경하여 token 필드 제거
    // WebSocket 세션에서 사용자 정보를 가져옴
}
