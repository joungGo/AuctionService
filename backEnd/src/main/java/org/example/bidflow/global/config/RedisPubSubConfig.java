package org.example.bidflow.global.config;

import lombok.RequiredArgsConstructor;
import org.example.bidflow.global.messaging.listener.RedisEventListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

/**
 * Redis Pub/Sub 설정
 * - EventPublisher가 발행한 메시지를 STOMP 토픽으로 전달
 * - 관심사별 채널 구독 설정
 */
@Configuration
@RequiredArgsConstructor
public class RedisPubSubConfig {
    
    private final RedisEventListener redisEventListener;
    
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        
        // 메인 페이지 채널 구독
        container.addMessageListener(new MessageListenerAdapter(redisEventListener), 
                new ChannelTopic("main:new-auctions"));
        container.addMessageListener(new MessageListenerAdapter(redisEventListener), 
                new ChannelTopic("main:status-changes"));
        
        // 카테고리별 채널 구독 (동적 구독은 추후 구현)
        // container.addMessageListener(new MessageListenerAdapter(redisEventListener), 
        //         new ChannelTopic("category:*:new-auctions"));
        // container.addMessageListener(new MessageListenerAdapter(redisEventListener), 
        //         new ChannelTopic("category:*:status-changes"));
        
        // 경매별 채널 구독 (동적 구독은 추후 구현)
        // container.addMessageListener(new MessageListenerAdapter(redisEventListener), 
        //         new ChannelTopic("auction:*"));
        
        return container;
    }
}
