package org.example.bidflow.global.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bidflow.global.messaging.listener.RedisEventListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

/**
 * Redis Pub/Sub 설정
 * - EventPublisher가 발행한 메시지를 STOMP 토픽으로 전달
 * - 관심사별 채널 구독 설정
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisPubSubConfig {
    
    private final RedisEventListener redisEventListener;
    
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        
        // 에러 핸들러 설정
        container.setErrorHandler(throwable -> {
            log.error("❌ [Redis Listener] 메시지 처리 중 에러 발생: {}", throwable.getMessage(), throwable);
            // Spring Data Redis의 자동 재연결 메커니즘이 작동하므로 추가 처리 불필요
        });
        
        // 메인 페이지 채널 구독
        container.addMessageListener(new MessageListenerAdapter(redisEventListener), 
                new ChannelTopic("main:new-auctions"));
        container.addMessageListener(new MessageListenerAdapter(redisEventListener), 
                new ChannelTopic("main:status-changes"));
        
        // 카테고리별 채널 구독 (패턴 구독)
        container.addMessageListener(new MessageListenerAdapter(redisEventListener),
                new PatternTopic("category:*:new-auctions"));
        container.addMessageListener(new MessageListenerAdapter(redisEventListener),
                new PatternTopic("category:*:status-changes"));
        
        // 경매별 채널 구독 (패턴 구독)
        container.addMessageListener(new MessageListenerAdapter(redisEventListener),
                new PatternTopic("auction:*"));
        
        log.info("✅ [Redis Pub/Sub] 리스너 컨테이너 초기화 완료");
        
        return container;
    }
}
