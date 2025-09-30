package org.example.bidflow.global.messaging.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String channel = new String(message.getChannel());
            String payload = new String(message.getBody());
            
            log.debug("📨 [Redis] 메시지 수신: channel={}, payload={}", channel, payload);
            
            // Redis 채널을 STOMP 토픽으로 매핑
            String stompTopic = mapChannelToStompTopic(channel);
            if (stompTopic != null) {
                // STOMP 토픽으로 메시지 전송
                messagingTemplate.convertAndSend(stompTopic, payload);
                log.debug("📤 [STOMP] 메시지 전송: topic={}", stompTopic);
            }
            
        } catch (Exception e) {
            log.error("❌ [Redis] 메시지 처리 실패: error={}", e.getMessage());
        }
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
        
        log.warn("⚠️ [Redis] 알 수 없는 채널: {}", channel);
        return null;
    }
}
