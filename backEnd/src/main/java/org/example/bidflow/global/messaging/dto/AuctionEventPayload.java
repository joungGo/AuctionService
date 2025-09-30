package org.example.bidflow.global.messaging.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 경매 이벤트 페이로드 DTO
 * - 목록용 Mini DTO와 상세용 Full DTO로 구분
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuctionEventPayload {
    
    // 공통 필드
    private String eventType;        // NEW_AUCTION, AUCTION_STATUS_CHANGE, BID_UPDATE, AUCTION_END
    private Long auctionId;
    private Long timestamp;
    
    // 경매 기본 정보
    private String productName;
    private String imageUrl;
    private Long startPrice;
    private Long currentBid;
    private String status;           // UPCOMING, ONGOING, FINISHED
    private Long categoryId;
    
    // 입찰 관련 정보
    private String bidderNickname;
    private String bidderUUID;
    
    // 경매 종료 정보
    private String winnerNickname;
    private Long winningBid;
    
    /**
     * 목록용 Mini DTO 생성 (렌더링에 필요한 최소 필드만)
     */
    public static AuctionEventPayload createMiniPayload(String eventType, Long auctionId, 
                                                       String productName, String imageUrl, 
                                                       Long startPrice, Long currentBid, 
                                                       String status, Long categoryId) {
        return AuctionEventPayload.builder()
                .eventType(eventType)
                .auctionId(auctionId)
                .productName(productName)
                .imageUrl(imageUrl)
                .startPrice(startPrice)
                .currentBid(currentBid)
                .status(status)
                .categoryId(categoryId)
                .timestamp(System.currentTimeMillis())
                .build();
    }
    
    /**
     * 상세/입찰용 Full DTO 생성
     */
    public static AuctionEventPayload createFullPayload(String eventType, Long auctionId,
                                                       String productName, String imageUrl,
                                                       Long startPrice, Long currentBid,
                                                       String status, Long categoryId,
                                                       String bidderNickname, String bidderUUID) {
        return AuctionEventPayload.builder()
                .eventType(eventType)
                .auctionId(auctionId)
                .productName(productName)
                .imageUrl(imageUrl)
                .startPrice(startPrice)
                .currentBid(currentBid)
                .status(status)
                .categoryId(categoryId)
                .bidderNickname(bidderNickname)
                .bidderUUID(bidderUUID)
                .timestamp(System.currentTimeMillis())
                .build();
    }
    
    /**
     * 경매 종료용 DTO 생성
     */
    public static AuctionEventPayload createEndPayload(Long auctionId, String winnerNickname, Long winningBid) {
        return AuctionEventPayload.builder()
                .eventType("AUCTION_END")
                .auctionId(auctionId)
                .winnerNickname(winnerNickname)
                .winningBid(winningBid)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
