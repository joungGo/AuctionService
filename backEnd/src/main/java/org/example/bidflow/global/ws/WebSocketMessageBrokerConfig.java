package org.example.bidflow.global.ws;

import lombok.RequiredArgsConstructor;
import org.example.bidflow.global.config.OriginConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP 기반 WebSocket 메시지 브로커 설정 클래스.
 * - 클라이언트 엔드포인트: "/ws" (네이티브 WebSocket, SockJS 미사용)
 * - 브로커 경로: 서버 → 클라이언트 전송 "/sub", 클라이언트 → 서버 전송 prefix "/app"
 * - CORS 허용 오리진 설정 및 Handshake 단계에서 JWT 기반 Principal 설정/검증 적용
 */
@Configuration
@EnableWebSocketMessageBroker       // STOMP 사용 명시
@RequiredArgsConstructor

public class WebSocketMessageBrokerConfig implements WebSocketMessageBrokerConfigurer {

    private final StompHandshakeHandler stompHandshakeHandler;
    private final JwtPrincipalHandshakeHandler jwtPrincipalHandshakeHandler;
    private final OriginConfig originConfig;
    private final StompChannelLoggingInterceptor stompChannelLoggingInterceptor;
    private final AuthStompInterceptor authStompInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/sub"); // 구독(Subscribe) 경로 (서버 -> 클라이언트 로 메시지 보낼 때)
        config.setApplicationDestinationPrefixes("/app"); // 메시지 보낼 prefix (클라이언트 -> 서버)
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // 로깅 → 권한 검증 순서로 인터셉터 등록
        registration.interceptors(stompChannelLoggingInterceptor, authStompInterceptor);
    }
    
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 클라이언트가 연결할 엔드포인트
        registry.addEndpoint("/ws")         // -> ws://localhost:8080/ws
                .setAllowedOrigins(originConfig.getFrontend().toArray(new String[0])) // 실제 origin 목록 사용
                .setAllowedOriginPatterns("*") // 추가 패턴 허용
                .setHandshakeHandler(jwtPrincipalHandshakeHandler) // Principal 설정
                .addInterceptors(stompHandshakeHandler); // HandshakeInterceptor 추가 (JWT 검증)
        // 쿠키 기반 인증을 위해 SockJS 제거 - 네이티브 WebSocket 사용
    }
}
