package org.example.bidflow.global.config;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Redis 기반 분산 Rate Limiting 설정
 * Bucket4j와 Redis를 연동하여 다중 인스턴스 환경에서 일관된 Rate Limiting 제공
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisRateLimitingConfig {

    /** Redis 서버 호스트 주소 (기본값: localhost) */
    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    /** Redis 서버 포트 번호 (기본값: 6379) */
    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    /** Redis 서버 패스워드 (설정되지 않으면 빈 문자열) */
    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    /**
     * Bucket4j용 별도 Redis 클라이언트 생성
     * 기존 Spring Data Redis와 독립적으로 동작하여 Rate Limiting 전용 연결 제공
     * Lettuce 클라이언트를 사용하여 비동기 처리 지원
     */
    @Bean
    public RedisClient bucket4jRedisClient() {
        try {
            // Redis 연결 URI 구성 (호스트, 포트, 타임아웃 설정)
            RedisURI.Builder uriBuilder = RedisURI.Builder
                    .redis(redisHost, redisPort)
                    .withTimeout(Duration.ofSeconds(5)); // 연결 타임아웃 5초

            // Redis 패스워드가 설정된 경우에만 인증 정보 추가
            if (redisPassword != null && !redisPassword.trim().isEmpty()) {
                uriBuilder.withPassword(redisPassword.toCharArray());
            }

            // Redis URI 빌드 및 클라이언트 생성
            RedisURI redisURI = uriBuilder.build();
            RedisClient client = RedisClient.create(redisURI);
            
            log.info("[Rate Limiting] Redis 클라이언트 초기화 완료 - Host: {}, Port: {}", redisHost, redisPort);
            return client;
            
        } catch (Exception e) {
            log.error("[Rate Limiting] Redis 클라이언트 초기화 실패: {}", e.getMessage(), e);
            throw new RuntimeException("Rate Limiting Redis 연결 실패", e);
        }
    }

    /**
     * Bucket4j ProxyManager 생성
     * Redis를 백엔드로 사용하는 분산 버킷 관리자
     * 다중 인스턴스 환경에서 토큰 버킷의 상태를 공유하여 일관된 Rate Limiting 제공 목적
     */
    @Bean
    public LettuceBasedProxyManager bucket4jProxyManager(RedisClient redisClient) {
        try {
            // Lettuce 기반 프록시 매니저 생성 및 만료 전략 설정
            LettuceBasedProxyManager proxyManager = LettuceBasedProxyManager.builderFor(redisClient)
                    .withExpirationStrategy(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofHours(2))) // 2시간 후 자동 만료
                    .build();
            
            log.info("[Rate Limiting] Bucket4j ProxyManager 초기화 완료");
            return proxyManager;
            
        } catch (Exception e) {
            log.error("[Rate Limiting] ProxyManager 초기화 실패: {}", e.getMessage(), e);
            throw new RuntimeException("Rate Limiting ProxyManager 초기화 실패", e);
        }
    }

    /**
     * Rate Limiting 전용 키 생성 유틸리티
     * Redis에 저장될 토큰 버킷의 고유 키를 생성하는 정적 유틸리티 클래스
     */
    public static class RateLimitKeyBuilder {
        
        /** Rate Limiting 키의 공통 접두사 */
        private static final String KEY_PREFIX = "rate_limit:";
        
        /** IP 기반 키 접두사 */
        private static final String IP_PREFIX = "ip:";
        
        /** 사용자 기반 키 접두사 */
        private static final String USER_PREFIX = "user:";
        
        /** API 기반 키 접두사 */
        private static final String API_PREFIX = "api:";

        /**
         * IP 기반 Rate Limiting 키 생성
         * 특정 IP 주소와 시간 윈도우에 대한 고유 키 생성
         * 
         * @param ipAddress 클라이언트 IP 주소
         * @param timeWindow 시간 윈도우 ("minute", "hour" 등)
         * @return IP 기반 Rate Limiting 키 (예: "rate_limit:ip:192.168.1.1:minute")
         */
        public static String buildIpKey(String ipAddress, String timeWindow) {
            return KEY_PREFIX + IP_PREFIX + ipAddress + ":" + timeWindow;
        }

        /**
         * 사용자 기반 Rate Limiting 키 생성
         * 인증된 사용자와 시간 윈도우에 대한 고유 키 생성
         * 
         * @param userUUID 사용자 고유 식별자
         * @param timeWindow 시간 윈도우 ("minute", "hour" 등)
         * @return 사용자 기반 Rate Limiting 키 (예: "rate_limit:user:uuid123:minute")
         */
        public static String buildUserKey(String userUUID, String timeWindow) {
            return KEY_PREFIX + USER_PREFIX + userUUID + ":" + timeWindow;
        }

        /**
         * API 기반 Rate Limiting 키 생성
         * 특정 API 엔드포인트, 식별자, 시간 윈도우에 대한 고유 키 생성
         * 
         * @param apiPath API 경로 (예: "/api/auth/login")
         * @param identifier 요청자 식별자 (IP 또는 사용자 UUID)
         * @param timeWindow 시간 윈도우 ("minute", "hour" 등)
         * @return API 기반 Rate Limiting 키 (예: "rate_limit:api:/api/auth/login:192.168.1.1:minute")
         */
        public static String buildApiKey(String apiPath, String identifier, String timeWindow) {
            // API 경로를 Redis 키에 안전한 형태로 변환 (특수문자 제거)
            String safeApiPath = apiPath.replaceAll("[^a-zA-Z0-9/_-]", "_");
            return KEY_PREFIX + API_PREFIX + safeApiPath + ":" + identifier + ":" + timeWindow;
        }

        /**
         * 복합 키 생성 (IP + API)
         * IP 주소와 API 경로를 조합한 복합적인 Rate Limiting 키 생성
         * IP별, API별 세밀한 제한 적용 시 사용
         * 
         * @param ipAddress 클라이언트 IP 주소
         * @param apiPath API 경로
         * @param timeWindow 시간 윈도우
         * @return 복합 Rate Limiting 키 (예: "rate_limit:composite:192.168.1.1:/api/auth/login:minute")
         */
        public static String buildCompositeKey(String ipAddress, String apiPath, String timeWindow) {
            String safeApiPath = apiPath.replaceAll("[^a-zA-Z0-9/_-]", "_");
            return KEY_PREFIX + "composite:" + ipAddress + ":" + safeApiPath + ":" + timeWindow;
        }
    }

    /**
     * Redis 연결 상태 체크
     * Rate Limiting 시스템의 Redis 연결 상태를 확인하는 헬스체크 메서드
     *
     * @param redisClient 체크할 Redis 클라이언트
     * @return Redis 연결 가능 여부
     */
    public boolean isRedisAvailable(RedisClient redisClient) {
        try {
            // Redis PING 명령으로 연결 상태 확인
            redisClient.connect().sync().ping();
            return true; // 연결 성공
        } catch (Exception e) {
            log.warn("[Rate Limiting] Redis 연결 상태 확인 실패: {}", e.getMessage());
            return false; // 연결 실패
        }
    }
}
