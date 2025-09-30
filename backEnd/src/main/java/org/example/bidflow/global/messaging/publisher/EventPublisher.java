package org.example.bidflow.global.messaging.publisher;

/**
 * 경매 이벤트 발행을 위한 인터페이스
 * - Redis Pub/Sub 기반 구현체 (v1)
 * - 추후 Kafka 기반 구현체로 교체 가능 (v2)
 */
public interface EventPublisher {
    
    /**
     * 새 경매 등록 이벤트 발행
     * @param auctionId 경매 ID
     * @param categoryId 카테고리 ID
     * @param productName 상품명
     * @param startPrice 시작가
     * @param imageUrl 이미지 URL
     */
    void publishNewAuction(Long auctionId, Long categoryId, String productName, Long startPrice, String imageUrl);
    
    /**
     * 경매 상태 변경 이벤트 발행
     * @param auctionId 경매 ID
     * @param categoryId 카테고리 ID
     * @param status 변경된 상태 (UPCOMING, ONGOING, FINISHED)
     * @param currentBid 현재 입찰가 (상태가 ONGOING일 때)
     */
    void publishAuctionStatusChange(Long auctionId, Long categoryId, String status, Long currentBid);
    
    /**
     * 입찰 업데이트 이벤트 발행
     * @param auctionId 경매 ID
     * @param currentBid 현재 입찰가
     * @param bidderNickname 입찰자 닉네임
     * @param bidderUUID 입찰자 UUID
     */
    void publishBidUpdate(Long auctionId, Long currentBid, String bidderNickname, String bidderUUID);
    
    /**
     * 경매 종료 이벤트 발행
     * @param auctionId 경매 ID
     * @param winnerNickname 낙찰자 닉네임
     * @param winningBid 낙찰가
     */
    void publishAuctionEnd(Long auctionId, String winnerNickname, Long winningBid);
}
