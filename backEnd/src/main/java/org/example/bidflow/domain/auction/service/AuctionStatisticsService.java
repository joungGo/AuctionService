package org.example.bidflow.domain.auction.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bidflow.domain.auction.dto.AuctionStatisticsResponse;
import org.example.bidflow.domain.auction.repository.AuctionRepository;
import org.example.bidflow.domain.bid.repository.BidRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 경매 통계 관련 비즈니스 로직을 담당하는 서비스
 * 
 * 이 서비스는 경매 통계 데이터를 조회하고 계산하는 책임을 가집니다.
 * 진행 중인 경매 수, 오늘의 입찰 수, 평균 대비 최고가 비율 등의 통계를 제공합니다.
 * 
 * @author AuctionService Team
 * @version 1.0
 * @since 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuctionStatisticsService {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;

    /**
     * 전체 경매 통계를 조회합니다.
     * 
     * @return 경매 통계 응답 DTO
     */
    public AuctionStatisticsResponse getAuctionStatistics() {
        // 현재 진행 중인 경매 수
        Long ongoingAuctions = auctionRepository.countOngoingAuctions();
        
        // 오늘의 총 입찰 수
        Long todayBids = bidRepository.countTodayBids();
        
        // 평균 입찰가 대비 최고가 비율 계산
        Double avgToMaxBidRatio = calculateAvgToMaxBidRatio();
        
        // 카테고리별 활성 경매 분포
        List<AuctionStatisticsResponse.CategoryAuctionCount> categoryDistribution = 
            getCategoryAuctionDistribution();
        
        return AuctionStatisticsResponse.builder()
                .ongoingAuctions(ongoingAuctions)
                .todayBids(todayBids)
                .avgToMaxBidRatio(avgToMaxBidRatio)
                .categoryDistribution(categoryDistribution)
                .build();
    }
    
    /**
     * 평균 입찰가 대비 최고가 비율을 계산합니다.
     * 
     * @return 평균 대비 최고가 비율 (소수점 둘째 자리까지)
     */
    private Double calculateAvgToMaxBidRatio() {
        List<Object[]> results = bidRepository.findAvgAndMaxBidAmountsForOngoingAuctions();
        
        if (results.isEmpty() || results.get(0)[0] == null || results.get(0)[1] == null) {
            return 0.0;
        }
        
        Double avgAmount = ((Number) results.get(0)[0]).doubleValue();
        Double maxAmount = ((Number) results.get(0)[1]).doubleValue();
        
        if (avgAmount == 0) {
            return 0.0;
        }
        
        return Math.round((maxAmount / avgAmount) * 100.0) / 100.0; // 소수점 둘째 자리까지
    }
    
    /**
     * 카테고리별 활성 경매 분포를 조회합니다.
     * 
     * @return 카테고리별 경매 수 목록
     */
    private List<AuctionStatisticsResponse.CategoryAuctionCount> getCategoryAuctionDistribution() {
        List<Object[]> results = auctionRepository.countOngoingAuctionsByCategory();
        
        return results.stream()
                .map(result -> AuctionStatisticsResponse.CategoryAuctionCount.builder()
                        .categoryId(((Number) result[0]).longValue())
                        .categoryName((String) result[1])
                        .auctionCount(((Number) result[2]).longValue())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 진행 중인 경매 수를 조회합니다.
     * 
     * @return 진행 중인 경매 수
     */
    public Long getOngoingAuctionsCount() {
        return auctionRepository.countOngoingAuctions();
    }

    /**
     * 오늘의 입찰 수를 조회합니다.
     * 
     * @return 오늘의 입찰 수
     */
    public Long getTodayBidsCount() {
        return bidRepository.countTodayBids();
    }
}
