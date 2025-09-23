package org.example.bidflow.domain.auction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuctionStatisticsResponse {
    
    // 현재 진행 중인 경매 수
    private Long ongoingAuctions;
    
    // 오늘의 총 입찰 수
    private Long todayBids;
    
    // 평균 입찰가 대비 최고가 비율 (백분율)
    private Double avgToMaxBidRatio;
    
    // 카테고리별 활성 경매 분포
    private List<CategoryAuctionCount> categoryDistribution;
    
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CategoryAuctionCount {
        private Long categoryId;
        private String categoryName;
        private Long auctionCount;
    }
}
