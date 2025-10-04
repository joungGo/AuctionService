package org.example.bidflow.global.monitoring;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bidflow.global.logging.StructuredLogger;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Redis 연결 상태 모니터링
 * - 주기적으로 Redis 연결 상태 체크
 * - 연결 실패 시 로깅 및 연속 실패 횟수 추적
 * - 연결 복구 시 다운타임 기록
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisHealthChecker {
    
    private final RedisTemplate<String, Object> redisTemplate;
    
    // 연결 상태 추적
    private boolean wasConnected = true;
    private int consecutiveFailures = 0;
    private long lastFailureTime = 0;
    
    /**
     * Redis 연결 상태를 30초마다 체크
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 10000)
    public void checkConnection() {
        long startTime = System.currentTimeMillis();
        
        try {
            // PING 명령으로 연결 확인
            redisTemplate.getConnectionFactory()
                    .getConnection()
                    .ping();
            
            long responseTime = System.currentTimeMillis() - startTime;
            
            // 연결 성공 처리
            handleConnectionSuccess(responseTime);
            
        } catch (Exception e) {
            // 연결 실패 처리
            handleConnectionFailure(e);
        }
    }
    
    /**
     * 연결 성공 처리
     */
    private void handleConnectionSuccess(long responseTime) {
        // 이전에 연결 실패 상태였다면 복구 로그 출력
        if (!wasConnected) {
            long downTime = System.currentTimeMillis() - lastFailureTime;
            StructuredLogger.logRedisConnectionRestored(downTime);
        }
        
        // 연결 성공 로그 (DEBUG 레벨로 자주 출력되지 않도록)
        if (!wasConnected || consecutiveFailures > 0) {
            StructuredLogger.logRedisConnectionSuccess(responseTime);
        }
        
        // 상태 초기화
        wasConnected = true;
        consecutiveFailures = 0;
    }
    
    /**
     * 연결 실패 처리
     */
    private void handleConnectionFailure(Exception e) {
        consecutiveFailures++;
        
        // 첫 실패 시점 기록
        if (wasConnected) {
            lastFailureTime = System.currentTimeMillis();
        }
        
        // 연결 실패 로그 출력
        StructuredLogger.logRedisConnectionFailure(e.getMessage(), consecutiveFailures);
        
        // 연속 3회 이상 실패 시 경고 로그
        if (consecutiveFailures == 3) {
            log.error("⚠️ [Redis Health] 연속 3회 연결 실패! Redis 서버 상태를 확인하세요.");
        }
        
        // 상태 업데이트
        wasConnected = false;
    }
}

