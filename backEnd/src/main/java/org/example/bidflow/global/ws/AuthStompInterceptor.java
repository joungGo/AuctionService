package org.example.bidflow.global.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.regex.Pattern;

/**
 * STOMP 구독 권한 검증 인터셉터
 * - SUBSCRIBE 프레임에서 JWT Principal 검증
 * - 경로별 접근 제어 (인증 필수 정책)
 */
@Slf4j
@Component
public class AuthStompInterceptor implements ChannelInterceptor {
    
    // 경로 패턴 (미리 컴파일)
    private static final Pattern AUCTION_PATTERN = Pattern.compile("^/sub/auction/\\d+$");
    private static final Pattern CATEGORY_PATTERN = Pattern.compile("^/sub/category/\\d+/.*$");
    private static final Pattern MAIN_PATTERN = Pattern.compile("^/sub/main/.*$");
    
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        
        // SUBSCRIBE 메시지만 검증
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            Principal user = accessor.getUser();
            
            log.debug("🔐 [STOMP Auth] 구독 요청 검증 - destination: {}, user: {}", 
                    destination, user != null ? user.getName() : "null");
            
            // 인증이 필요한 경로 검증
            if (requiresAuthentication(destination)) {
                if (user == null) {
                    log.warn("❌ [STOMP Auth] 구독 거부 - 인증되지 않은 사용자: destination={}", destination);
                    throw new MessagingException("구독 권한이 없습니다. 로그인이 필요합니다.");
                }
                
                // Principal 형식 검증 (nickname::userUUID)
                String principalName = user.getName();
                if (principalName == null || !principalName.contains("::")) {
                    log.warn("❌ [STOMP Auth] 구독 거부 - 잘못된 Principal 형식: destination={}, principal={}", 
                            destination, principalName);
                    throw new MessagingException("잘못된 인증 정보입니다.");
                }
                
                log.info("✅ [STOMP Auth] 구독 승인 - destination: {}, user: {}", destination, principalName);
            }
        }
        
        return message;
    }
    
    /**
     * 경로가 인증을 필요로 하는지 검사
     */
    private boolean requiresAuthentication(String destination) {
        if (destination == null) return false;
        
        // 현재 정책: 모든 /sub/** 경로는 인증 필요
        return AUCTION_PATTERN.matcher(destination).matches() ||
               CATEGORY_PATTERN.matcher(destination).matches() ||
               MAIN_PATTERN.matcher(destination).matches();
    }
}

