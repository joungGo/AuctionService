package org.example.bidflow.global.messaging.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bidflow.global.logging.StructuredLogger;
import org.example.bidflow.global.messaging.dto.AuctionEventPayload;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis Pub/Sub 메시지를 STOMP 토픽으로 전달하는 리스너
 * - Redis 채널 → STOMP 토픽 매핑
 * - 관심사별 타겟팅으로 불필요한 메시지 전송 방지
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisEventListener implements MessageListener {
    
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = null;
        String payload = null;
        long startTime = System.currentTimeMillis();
        
        try {
            channel = new String(message.getChannel());
            payload = new String(message.getBody());
            
            // 구조화된 로깅: 메시지 수신
            StructuredLogger.logRedisMessageReceived(channel, payload.length());
            
            // JSON 파싱 및 검증
            AuctionEventPayload eventPayload = parseAndValidatePayload(payload, channel);
            if (eventPayload == null) {
                return; // 파싱/검증 실패 시 이미 구조화된 로그 출력됨
            }
            
            // Redis 채널을 STOMP 토픽으로 매핑
            String stompTopic = mapChannelToStompTopic(channel);
            if (stompTopic != null) {
                try {
                    // STOMP 토픽으로 메시지 전송
                    messagingTemplate.convertAndSend(stompTopic, eventPayload);
                    
                    // 구조화된 로깅: 메시지 처리 성공
                    long processingTime = System.currentTimeMillis() - startTime;
                    StructuredLogger.logRedisEventSuccess(channel, eventPayload.getEventType(), 
                                                         eventPayload.getAuctionId(), stompTopic, processingTime);
                } catch (Exception e) {
                    // 구조화된 로깅: STOMP 전송 실패
                    StructuredLogger.logStompSendFailure(stompTopic, eventPayload.getEventType(), 
                                                        eventPayload.getAuctionId(), e.getMessage());
                    throw e;
                }
            } else {
                // 구조화된 로깅: 알 수 없는 채널
                StructuredLogger.logUnknownChannel(channel);
            }
            
        } catch (Exception e) {
            log.error("❌ [Redis] 메시지 처리 실패: channel={}, error={}", 
                     channel, e.getMessage(), e);
        }
    }
    
    /**
     * JSON 파싱 및 스키마 검증
     */
    private AuctionEventPayload parseAndValidatePayload(String payload, String channel) {
        try {
            // JSON 파싱
            AuctionEventPayload eventPayload = objectMapper.readValue(payload, AuctionEventPayload.class);
            
            // 필수 필드 검증
            if (!isValidEventPayload(eventPayload)) {
                // 구조화된 로깅: 검증 실패
                StructuredLogger.logValidationFailure(
                    eventPayload.getEventType(), 
                    eventPayload.getAuctionId(), 
                    "필수 필드 검증 실패"
                );
                return null;
            }
            
            return eventPayload;
            
        } catch (Exception e) {
            // 구조화된 로깅: 파싱 실패
            StructuredLogger.logParsingFailure(channel, payload, e.getMessage());
            return null;
        }
    }
    
    /**
     * 이벤트 페이로드 스키마 검증
     */
    private boolean isValidEventPayload(AuctionEventPayload payload) {
        if (payload == null) {
            return false;
        }
        
        // 필수 필드 검증
        if (payload.getEventType() == null || payload.getEventType().trim().isEmpty()) {
            StructuredLogger.logValidationFailure(null, payload.getAuctionId(), "eventType 누락");
            return false;
        }
        
        if (payload.getAuctionId() == null || payload.getAuctionId() <= 0) {
            StructuredLogger.logValidationFailure(payload.getEventType(), payload.getAuctionId(), "auctionId 유효하지 않음");
            return false;
        }
        
        // 이벤트 타입별 필수 필드 검증
        switch (payload.getEventType()) {
            case "NEW_AUCTION":
                return isValidNewAuctionPayload(payload);
            case "AUCTION_STATUS_CHANGE":
                return isValidStatusChangePayload(payload);
            case "BID_UPDATE":
                return isValidBidUpdatePayload(payload);
            case "AUCTION_END":
                return isValidAuctionEndPayload(payload);
            default:
                StructuredLogger.logValidationFailure(payload.getEventType(), payload.getAuctionId(), "알 수 없는 이벤트 타입");
                return false;
        }
    }
    
    /**
     * 새 경매 이벤트 검증
     */
    private boolean isValidNewAuctionPayload(AuctionEventPayload payload) {
        return payload.getProductName() != null && !payload.getProductName().trim().isEmpty() &&
               payload.getStartPrice() != null && payload.getStartPrice() >= 0;
    }
    
    /**
     * 상태 변경 이벤트 검증
     */
    private boolean isValidStatusChangePayload(AuctionEventPayload payload) {
        return payload.getStatus() != null && !payload.getStatus().trim().isEmpty() &&
               payload.getCurrentBid() != null && payload.getCurrentBid() >= 0;
    }
    
    /**
     * 입찰 업데이트 이벤트 검증
     */
    private boolean isValidBidUpdatePayload(AuctionEventPayload payload) {
        return payload.getCurrentBid() != null && payload.getCurrentBid() > 0 &&
               payload.getBidderNickname() != null && !payload.getBidderNickname().trim().isEmpty();
    }
    
    /**
     * 경매 종료 이벤트 검증
     */
    private boolean isValidAuctionEndPayload(AuctionEventPayload payload) {
        return payload.getWinnerNickname() != null && !payload.getWinnerNickname().trim().isEmpty() &&
               payload.getWinningBid() != null && payload.getWinningBid() > 0;
    }
    
    /**
     * Redis 채널을 STOMP 토픽으로 매핑
     */
    private String mapChannelToStompTopic(String channel) {
        if (channel.equals("main:new-auctions")) {
            return "/sub/main/new-auctions";
        } else if (channel.equals("main:status-changes")) {
            return "/sub/main/status-changes";
        } else if (channel.startsWith("category:") && channel.endsWith(":new-auctions")) {
            // category:1:new-auctions → /sub/category/1/new-auctions
            String categoryId = channel.substring(9, channel.lastIndexOf(":"));
            return "/sub/category/" + categoryId + "/new-auctions";
        } else if (channel.startsWith("category:") && channel.endsWith(":status-changes")) {
            // category:1:status-changes → /sub/category/1/status-changes
            String categoryId = channel.substring(9, channel.lastIndexOf(":"));
            return "/sub/category/" + categoryId + "/status-changes";
        } else if (channel.startsWith("auction:")) {
            // auction:123 → /sub/auction/123
            String auctionId = channel.substring(8);
            return "/sub/auction/" + auctionId;
        }
        
        // 알 수 없는 채널은 onMessage에서 구조화된 로깅으로 처리됨
        return null;
    }
}
