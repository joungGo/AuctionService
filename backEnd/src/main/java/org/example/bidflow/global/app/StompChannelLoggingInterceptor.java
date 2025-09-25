package org.example.bidflow.global.app;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

/**
 * STOMP 구독/해제/연결해제 이벤트를 로깅하는 인터셉터
 * - SUBSCRIBE, UNSUBSCRIBE, DISCONNECT 시 상세 로그 출력
 * - destination, sessionId, user 정보 포함
 */
@Slf4j
@Component
public class StompChannelLoggingInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.wrap(message);
        String messageType = accessor.getMessageType() != null ? accessor.getMessageType().toString() : null;
        String destination = accessor.getDestination();
        String sessionId = accessor.getSessionId();
        String user = accessor.getUser() != null ? accessor.getUser().getName() : null;

        if ("SUBSCRIBE".equals(messageType)) {
            log.info("📡 [STOMP 구독] SUBSCRIBE - destination: {}, sessionId: {}, user: {}", 
                destination, sessionId, user);
        } else if ("UNSUBSCRIBE".equals(messageType)) {
            log.info("📡 [STOMP 구독] UNSUBSCRIBE - destination: {}, sessionId: {}, user: {}", 
                destination, sessionId, user);
        } else if ("DISCONNECT".equals(messageType)) {
            log.info("🔌 [STOMP 연결] DISCONNECT - sessionId: {}, user: {}", 
                sessionId, user);
        }

        return message;
    }
}
