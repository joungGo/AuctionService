package org.example.bidflow.domain.auction.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bidflow.domain.auction.entity.Auction;
import org.example.bidflow.domain.bid.entity.Bid;
import org.example.bidflow.domain.bid.repository.BidRepository;
import org.example.bidflow.global.utils.RedisCommon;
import org.springframework.stereotype.Service;

/**
 * 경매 관련 Redis 작업을 담당하는 서비스
 * 
 * 이 서비스는 경매의 실시간 입찰 정보, 현재가, 입찰자 정보 등을
 * Redis에서 조회하고 관리하는 책임을 가집니다.
 * 
 * @author AuctionService Team
 * @version 1.0
 * @since 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuctionRedisService {

    private final RedisCommon redisCommon;
    private final BidRepository bidRepository;

    /**
     * 경매의 현재 입찰 정보를 조회합니다. (Redis 우선, DB 폴백)
     * 
     * @param auctionId 경매 ID
     * @param auction 경매 엔티티
     * @return 현재 입찰가와 최고 입찰자 UUID
     */
    public BidInfo getCurrentBidInfo(Long auctionId, Auction auction) {
        String hashKey = "auction:" + auctionId;
        String highestBidderUUID = redisCommon.getFromHash(hashKey, "userUUID", String.class);
        Integer currentBid = redisCommon.getFromHash(hashKey, "amount", Integer.class);
        
        // Redis에 정보가 없으면 DB에서 조회 (폴백)
        if (currentBid == null) {
            Bid highestBid = bidRepository.findTopByAuctionOrderByAmountDesc(auction);
            currentBid = highestBid != null ? highestBid.getAmount() : auction.getStartPrice();
            highestBidderUUID = highestBid != null ? highestBid.getUser().getUserUUID() : null;
        }
        
        return new BidInfo(currentBid, highestBidderUUID);
    }

    /**
     * 경매의 현재 입찰가를 조회합니다.
     * 
     * @param auctionId 경매 ID
     * @param defaultPrice 기본 가격 (Redis에 데이터가 없을 때 사용)
     * @return 현재 입찰가
     */
    public Integer getCurrentBidAmount(Long auctionId, Integer defaultPrice) {
        String hashKey = "auction:" + auctionId;
        Integer amount = redisCommon.getFromHash(hashKey, "amount", Integer.class);
        return amount != null ? amount : defaultPrice;
    }

    /**
     * 경매의 최고 입찰자 UUID를 조회합니다.
     * 
     * @param auctionId 경매 ID
     * @return 최고 입찰자 UUID (없으면 null)
     */
    public String getHighestBidderUUID(Long auctionId) {
        String hashKey = "auction:" + auctionId;
        return redisCommon.getFromHash(hashKey, "userUUID", String.class);
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
