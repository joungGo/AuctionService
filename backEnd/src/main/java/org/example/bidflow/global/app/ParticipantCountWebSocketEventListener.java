package org.example.bidflow.global.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Slf4j
@Component
@RequiredArgsConstructor
public class ParticipantCountWebSocketEventListener {
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final RedisTemplate<String, String> redisTemplate;

    // 연결 시 참여자 추가
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        // StompHeaderAccessor: STOMP 프로토콜 헤더에 접근하기 위한 래퍼 클래스
        // event.getMessage(): WebSocket 연결 이벤트가 발생했을 때의 메시지 정보를 반환 (헤더, 페이로드 등 포함)
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());

        String auctionId = getAuctionIdFromHeaders(accessor);
        String userUUID = getUserUUIDFromHeaders(accessor);
        if (auctionId != null && userUUID != null) {
            String key = "auction:" + auctionId + ":participants";
            // redisTemplate.opsForSet(): Redis의 Set 자료구조에 대한 연산을 수행하는 객체를 반환
            // add(): Redis Set에 새로운 멤버(userUUID)를 추가
            redisTemplate.opsForSet().add(key, userUUID);
            broadcastParticipantCount(auctionId, key);
            log.info("[WebSocket] 참여자 추가 - auctionId: {}, userUUID: {}", auctionId, userUUID);
        }
    }

    // 해제 시 참여자 제거
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        // StompHeaderAccessor: STOMP 프로토콜 헤더에 접근하기 위한 래퍼 클래스
        // event.getMessage(): WebSocket 연결 해제 이벤트가 발생했을 때의 메시지 정보를 반환
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());

        String auctionId = getAuctionIdFromHeaders(accessor);
        String userUUID = getUserUUIDFromHeaders(accessor);
        if (auctionId != null && userUUID != null) {
            String key = "auction:" + auctionId + ":participants";
            // redisTemplate.opsForSet(): Redis의 Set 자료구조에 대한 연산을 수행하는 객체를 반환
            // remove(): Redis Set에서 특정 멤버(userUUID)를 제거
            redisTemplate.opsForSet().remove(key, userUUID);
            broadcastParticipantCount(auctionId, key);
            log.info("[WebSocket] 참여자 제거 - auctionId: {}, userUUID: {}", auctionId, userUUID);
        }
    }

    private void broadcastParticipantCount(String auctionId, String key) {
        // redisTemplate.opsForSet(): Redis의 Set 자료구조에 대한 연산을 수행하는 객체를 반환
        // size(): Redis Set의 크기(멤버 개수)를 반환
        Long count = redisTemplate.opsForSet().size(key);

        // simpMessagingTemplate.convertAndSend(): 메시지를 특정 주제(topic)로 브로드캐스트
        // "/sub/auction/" + auctionId: 클라이언트가 구독하는 주제 경로
        // Collections.singletonMap(): 단일 key-value 쌍을 가진 불변 Map을 생성
        simpMessagingTemplate.convertAndSend("/sub/auction/" + auctionId,
                java.util.Collections.singletonMap("participantCount", count != null ? count : 0));
    }

    // 헤더에서 auctionId 추출 (구현 상황에 따라 조정 필요)
    private String getAuctionIdFromHeaders(StompHeaderAccessor accessor) {
        String auctionId = accessor.getFirstNativeHeader("auctionId");
        if (auctionId == null) {
            log.warn("[WebSocket] auctionId 헤더 없음. accessor 전체 정보: {}", accessor);
        }
        return auctionId;
    }

    // 헤더에서 userUUID 추출 (구현 상황에 따라 조정 필요)
    private String getUserUUIDFromHeaders(StompHeaderAccessor accessor) {
        String userUUID = accessor.getFirstNativeHeader("userUUID");
        if (userUUID == null) {
            log.warn("[WebSocket] userUUID 헤더 없음. accessor 전체 정보: {}", accessor);
        }
        return userUUID;
    }
}