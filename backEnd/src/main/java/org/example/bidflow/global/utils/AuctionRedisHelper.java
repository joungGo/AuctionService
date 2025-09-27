package org.example.bidflow.global.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bidflow.domain.auction.entity.Auction;
import org.example.bidflow.domain.bid.entity.Bid;
import org.example.bidflow.domain.bid.repository.BidRepository;
import org.springframework.stereotype.Component;

/**
 * 경매 관련 Redis 조회 공통 유틸리티
 * 
 * 이 클래스는 경매와 관련된 Redis 조회 로직을 중앙화하여
 * 코드 중복을 제거하고 일관성을 보장합니다.
 * 
 * @author AuctionService Team
 * @version 1.0
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionRedisHelper {

    private final RedisCommon redisCommon;
    private final BidRepository bidRepository;

    /**
     * 경매의 현재 최고가를 조회합니다. (Redis 우선, DB 폴백)
     * 
     * @param auctionId 경매 ID
     * @param auction 경매 엔티티 (DB 폴백용)
     * @return 현재 최고가
     */
    public Integer getCurrentHighestAmount(Long auctionId, Auction auction) {
        String hashKey = getAuctionRedisKey(auctionId);
        Integer currentHighestAmount = redisCommon.getFromHash(hashKey, "amount", Integer.class);
        
        // DB에서 최고가 조회 (Redis 폴백)
        if (currentHighestAmount == null) {
            currentHighestAmount = bidRepository.findMaxAmountByAuction(auction)
                    .orElse(auction.getStartPrice());
            log.debug("[Redis 폴백] 경매 {} 최고가 DB에서 조회: {}", auctionId, currentHighestAmount);
        }
        
        return currentHighestAmount;
    }

    /**
     * 경매의 현재 최고 입찰자 UUID를 조회합니다.
     * 
     * @param auctionId 경매 ID
     * @return 최고 입찰자 UUID (없으면 null)
     */
    public String getHighestBidderUUID(Long auctionId) {
        String hashKey = getAuctionRedisKey(auctionId);
        return redisCommon.getFromHash(hashKey, "userUUID", String.class);
    }

    /**
     * 경매의 현재 입찰 정보를 조회합니다. (Redis 우선, DB 폴백)
     * 
     * @param auctionId 경매 ID
     * @param auction 경매 엔티티 (DB 폴백용)
     * @return 현재 입찰 정보
     */
    public BidInfo getCurrentBidInfo(Long auctionId, Auction auction) {
        String hashKey = getAuctionRedisKey(auctionId);
        String highestBidderUUID = redisCommon.getFromHash(hashKey, "userUUID", String.class);
        Integer currentBid = redisCommon.getFromHash(hashKey, "amount", Integer.class);
        
        // Redis에 정보가 없으면 DB에서 조회 (폴백)
        if (currentBid == null) {
            Bid highestBid = bidRepository.findTopByAuctionOrderByAmountDesc(auction);
            currentBid = highestBid != null ? highestBid.getAmount() : auction.getStartPrice();
            highestBidderUUID = highestBid != null ? highestBid.getUser().getUserUUID() : null;
            log.debug("[Redis 폴백] 경매 {} 입찰 정보 DB에서 조회: amount={}, userUUID={}", 
                    auctionId, currentBid, highestBidderUUID);
        }
        
        return new BidInfo(currentBid, highestBidderUUID);
    }

    /**
     * 경매의 Redis 키를 생성합니다.
     * 
     * @param auctionId 경매 ID
     * @return Redis 키
     */
    public String getAuctionRedisKey(Long auctionId) {
        return "auction:" + auctionId;
    }

    /**
     * Redis에 입찰 정보를 업데이트합니다.
     * 
     * @param auctionId 경매 ID
     * @param amount 입찰 금액
     * @param userUUID 입찰자 UUID
     */
    public void updateBidInfo(Long auctionId, Integer amount, String userUUID) {
        String hashKey = getAuctionRedisKey(auctionId);
        redisCommon.putInHash(hashKey, "amount", amount);
        redisCommon.putInHash(hashKey, "userUUID", userUUID);
        
        log.info("[Redis 업데이트] 경매 {} 입찰 정보 갱신 - 금액: {}, 입찰자: {}", 
                auctionId, amount, userUUID);
    }

    /**
     * 입찰 정보를 담는 내부 클래스
     */
    public static class BidInfo {
        private final Integer currentBid;
        private final String highestBidderUUID;

        public BidInfo(Integer currentBid, String highestBidderUUID) {
            this.currentBid = currentBid;
            this.highestBidderUUID = highestBidderUUID;
        }

        public Integer getCurrentBid() { return currentBid; }
        public String getHighestBidderUUID() { return highestBidderUUID; }
    }
}
