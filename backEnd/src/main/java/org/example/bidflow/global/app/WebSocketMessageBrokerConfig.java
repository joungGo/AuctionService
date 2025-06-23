package org.example.bidflow.global.app;

import lombok.RequiredArgsConstructor;
import org.example.bidflow.global.config.OriginConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker       // STOMP 사용 명시
@RequiredArgsConstructor

public class WebSocketMessageBrokerConfig implements WebSocketMessageBrokerConfigurer {

    private final StompHandshakeHandler stompHandshakeHandler;
    private final OriginConfig originConfig;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/sub"); // 구독(Subscribe) 경로 (서버 -> 클라이언트 로 메시지 보낼 때)
        config.setApplicationDestinationPrefixes("/app"); // 메시지 보낼 prefix (클라이언트 -> 서버)
    }
    
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 클라이언트가 연결할 엔드포인트
        registry.addEndpoint("/ws")         // -> ws://localhost:8080/ws
                .setAllowedOrigins(originConfig.getFrontend().toArray(new String[0])) // 실제 origin 목록 사용
                .setAllowedOriginPatterns("*") // 추가 패턴 허용
                .addInterceptors(stompHandshakeHandler) // HandshakeInterceptor 추가 (JWT 검증)
                .withSockJS()  // socket fallback 지원
                .setStreamBytesLimit(512 * 1024) // ALB를 위한 설정
                .setHttpMessageCacheSize(1000)
                .setDisconnectDelay(30 * 1000)
                .setWebSocketEnabled(true) // WebSocket 명시적 활성화
                .setTransportHandlers(); // 기본 transport 핸들러 사용
    }
}
