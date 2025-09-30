package org.example.bidflow.global.messaging.publisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bidflow.global.messaging.dto.AuctionEventPayload;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis Pub/Sub 기반 EventPublisher 구현체 (v1)
 * - 추후 KafkaEventPublisher로 교체 가능
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisEventPublisher implements EventPublisher {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    // Redis 채널명 상수
    private static final String MAIN_NEW_AUCTIONS_CHANNEL = "main:new-auctions";
    private static final String MAIN_STATUS_CHANGES_CHANNEL = "main:status-changes";
    private static final String CATEGORY_NEW_AUCTIONS_CHANNEL_PREFIX = "category:";
    private static final String CATEGORY_STATUS_CHANGES_CHANNEL_PREFIX = "category:";
    private static final String AUCTION_EVENTS_CHANNEL_PREFIX = "auction:";
    
    @Override
    public void publishNewAuction(Long auctionId, Long categoryId, String productName, 
                                 Long startPrice, String imageUrl) {
        try {
            // Mini DTO 생성 (목록 렌더링용)
            AuctionEventPayload payload = AuctionEventPayload.createMiniPayload(
                "NEW_AUCTION", auctionId, productName, imageUrl, startPrice, startPrice, "UPCOMING", categoryId
            );
            
            // 메인 페이지 구독자에게 전송
            redisTemplate.convertAndSend(MAIN_NEW_AUCTIONS_CHANNEL, payload);
            log.info("📢 [Redis] 새 경매 알림 발행 - 메인: auctionId={}, productName={}", auctionId, productName);
            
            // 카테고리 페이지 구독자에게 전송 (카테고리가 있는 경우)
            if (categoryId != null) {
                String categoryChannel = CATEGORY_NEW_AUCTIONS_CHANNEL_PREFIX + categoryId + ":new-auctions";
                redisTemplate.convertAndSend(categoryChannel, payload);
                log.info("📢 [Redis] 새 경매 알림 발행 - 카테고리: categoryId={}, auctionId={}", categoryId, auctionId);
            }
            
        } catch (Exception e) {
            log.error("❌ [Redis] 새 경매 알림 발행 실패: auctionId={}, error={}", auctionId, e.getMessage());
        }
    }
    
    @Override
    public void publishAuctionStatusChange(Long auctionId, Long categoryId, String status, Long currentBid) {
        try {
            // Mini DTO 생성 (상태 변경용)
            AuctionEventPayload payload = AuctionEventPayload.builder()
                .eventType("AUCTION_STATUS_CHANGE")
                .auctionId(auctionId)
                .status(status)
                .currentBid(currentBid)
                .categoryId(categoryId)
                .timestamp(System.currentTimeMillis())
                .build();
            
            // 메인 페이지 구독자에게 전송
            redisTemplate.convertAndSend(MAIN_STATUS_CHANGES_CHANNEL, payload);
            log.info("📢 [Redis] 경매 상태 변경 알림 발행 - 메인: auctionId={}, status={}", auctionId, status);
            
            // 카테고리 페이지 구독자에게 전송 (카테고리가 있는 경우)
            if (categoryId != null) {
                String categoryChannel = CATEGORY_STATUS_CHANGES_CHANNEL_PREFIX + categoryId + ":status-changes";
                redisTemplate.convertAndSend(categoryChannel, payload);
                log.info("📢 [Redis] 경매 상태 변경 알림 발행 - 카테고리: categoryId={}, auctionId={}, status={}", 
                        categoryId, auctionId, status);
            }
            
        } catch (Exception e) {
            log.error("❌ [Redis] 경매 상태 변경 알림 발행 실패: auctionId={}, error={}", auctionId, e.getMessage());
        }
    }
    
    @Override
    public void publishBidUpdate(Long auctionId, Long currentBid, String bidderNickname, String bidderUUID) {
        try {
            // Full DTO 생성 (입찰 상세 정보 포함)
            AuctionEventPayload payload = AuctionEventPayload.builder()
                .eventType("BID_UPDATE")
                .auctionId(auctionId)
                .currentBid(currentBid)
                .bidderNickname(bidderNickname)
                .bidderUUID(bidderUUID)
                .timestamp(System.currentTimeMillis())
                .build();
            
            // 해당 경매 구독자에게만 전송 (상세/입찰 페이지)
            String auctionChannel = AUCTION_EVENTS_CHANNEL_PREFIX + auctionId;
            redisTemplate.convertAndSend(auctionChannel, payload);
            log.info("📢 [Redis] 입찰 업데이트 알림 발행: auctionId={}, currentBid={}, bidder={}", 
                    auctionId, currentBid, bidderNickname);
            
        } catch (Exception e) {
            log.error("❌ [Redis] 입찰 업데이트 알림 발행 실패: auctionId={}, error={}", auctionId, e.getMessage());
        }
    }
    
    @Override
    public void publishAuctionEnd(Long auctionId, String winnerNickname, Long winningBid) {
        try {
            // 종료 DTO 생성
            AuctionEventPayload payload = AuctionEventPayload.createEndPayload(auctionId, winnerNickname, winningBid);
            
            // 해당 경매 구독자에게만 전송 (상세/입찰 페이지)
            String auctionChannel = AUCTION_EVENTS_CHANNEL_PREFIX + auctionId;
            redisTemplate.convertAndSend(auctionChannel, payload);
            log.info("📢 [Redis] 경매 종료 알림 발행: auctionId={}, winner={}, winningBid={}", 
                    auctionId, winnerNickname, winningBid);
            
        } catch (Exception e) {
            log.error("❌ [Redis] 경매 종료 알림 발행 실패: auctionId={}, error={}", auctionId, e.getMessage());
        }
    }
}
