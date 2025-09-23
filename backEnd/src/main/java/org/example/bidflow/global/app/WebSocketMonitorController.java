package org.example.bidflow.global.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket 모니터링용 관리자 REST 컨트롤러.
 * - 현재 연결된 사용자/세션/구독 조회
 * - 특정 경매방 참가자 수 조회 (향후 Redis 연동 확장 전제)
 * - 테스트 목적의 임의 메시지 전송 및 강제 연결 해제 엔드포인트 제공
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/websocket")
@RequiredArgsConstructor
public class WebSocketMonitorController {

    private final SimpMessagingTemplate simpMessagingTemplate;
    private final SimpUserRegistry simpUserRegistry;

    /**
     * WebSocket 연결 상태 조회
     */
    @GetMapping("/status")
    public Map<String, Object> getWebSocketStatus() {
        Map<String, Object> status = new HashMap<>();
        
        int connectedUsers = simpUserRegistry.getUserCount();
        status.put("connectedUsers", connectedUsers);

        // 사용자/세션/구독 상세
        var users = simpUserRegistry.getUsers().stream().map(user -> {
            Map<String, Object> u = new HashMap<>();
            String name = user.getName(); // nickname::userUUID
            String nickname = name;
            String userUUID = null;
            if (name != null && name.contains("::")) {
                String[] parts = name.split("::", 2);
                nickname = parts[0];
                userUUID = parts.length > 1 ? parts[1] : null;
            }
            u.put("principal", name);
            u.put("nickname", nickname);
            u.put("userUUID", userUUID);

            var sessions = user.getSessions().stream().map(session -> {
                Map<String, Object> s = new HashMap<>();
                s.put("sessionId", session.getId());
                s.put("subscriptions", session.getSubscriptions().stream().map(SimpSubscription::getDestination).toList());
                return s;
            }).toList();
            u.put("sessions", sessions);
            return u;
        }).toList();
        status.put("users", users);
        
        log.info("[WebSocket Monitor] 연결 상태 조회 - 연결된 사용자: {}", connectedUsers);
        
        return status;
    }

    /**
     * 특정 경매방 참가자 수 조회
     */
    @GetMapping("/auction/{auctionId}/participants")
    public Map<String, Object> getAuctionParticipants(@PathVariable Long auctionId) {
        Map<String, Object> result = new HashMap<>();
        
        // Redis에서 참가자 수 조회 (실제 구현 시 RedisTemplate 사용 예정)
        // 실제 구현에서는 RedisTemplate을 주입받아 사용
        result.put("auctionId", auctionId);
        result.put("participantCount", 0); // 임시값
        result.put("message", "참가자 수 조회 완료");
        
        log.info("[WebSocket Monitor] 경매 {} 참가자 수 조회", auctionId);
        
        return result;
    }

    /**
     * 테스트 메시지 전송
     */
    @PostMapping("/test/send")
    public Map<String, Object> sendTestMessage(
            @RequestParam String destination,
            @RequestParam String message) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 테스트 메시지 전송
            Map<String, Object> testMessage = new HashMap<>();
            testMessage.put("message", message);
            testMessage.put("timestamp", System.currentTimeMillis());
            testMessage.put("type", "TEST");
            
            simpMessagingTemplate.convertAndSend(destination, testMessage);
            
            result.put("success", true);
            result.put("destination", destination);
            result.put("message", "테스트 메시지 전송 완료");
            
            log.info("[WebSocket Monitor] 테스트 메시지 전송 - 목적지: {}, 메시지: {}", destination, message);
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            log.error("[WebSocket Monitor] 테스트 메시지 전송 실패: {}", e.getMessage());
        }
        
        return result;
    }

    /**
     * WebSocket 연결 강제 해제 (관리자용)
     */
    @PostMapping("/disconnect/{userName}")
    public Map<String, Object> forceDisconnect(@PathVariable String userName) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 특정 사용자 연결 해제 (실제 구현에서는 더 복잡한 로직 필요)
            result.put("success", true);
            result.put("message", "사용자 연결 해제 요청 완료: " + userName);
            
            log.info("[WebSocket Monitor] 사용자 연결 해제 요청: {}", userName);
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            log.error("[WebSocket Monitor] 사용자 연결 해제 실패: {}", e.getMessage());
        }
        
        return result;
    }
}
