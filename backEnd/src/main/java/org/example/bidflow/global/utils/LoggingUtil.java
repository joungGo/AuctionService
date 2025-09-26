package org.example.bidflow.global.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 개발자 디버깅을 위한 로깅 유틸리티 클래스
 * 표준화된 로그 형식으로 디버깅 정보를 제공합니다.
 */
@Slf4j
@Component
public class LoggingUtil {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /**
     * 메서드 시작 로그
     */
    public static void logMethodStart(String className, String methodName, Object... params) {
        StringBuilder sb = new StringBuilder();
        sb.append("[메서드 시작] ").append(className).append(".").append(methodName).append("(");
        
        if (params != null && params.length > 0) {
            for (int i = 0; i < params.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(params[i]);
            }
        }
        sb.append(")");
        
        log.debug(sb.toString());
    }

    /**
     * 메서드 종료 로그
     */
    public static void logMethodEnd(String className, String methodName, long executionTimeMs) {
        log.debug("[메서드 종료] {}.{} - 실행시간: {}ms", className, methodName, executionTimeMs);
    }

    /**
     * 메서드 종료 로그 (결과 포함)
     */
    public static void logMethodEnd(String className, String methodName, long executionTimeMs, Object result) {
        log.debug("[메서드 종료] {}.{} - 실행시간: {}ms, 결과: {}", className, methodName, executionTimeMs, result);
    }

    /**
     * 비즈니스 로직 단계별 로그
     */
    public static void logBusinessStep(String stepName, String description, Object... context) {
        StringBuilder sb = new StringBuilder();
        sb.append("[비즈니스 단계] ").append(stepName).append(" - ").append(description);
        
        if (context != null && context.length > 0) {
            sb.append(" | 컨텍스트: ");
            for (int i = 0; i < context.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(context[i]);
            }
        }
        
        log.info(sb.toString());
    }

    /**
     * 데이터베이스 작업 로그
     */
    public static void logDatabaseOperation(String operation, String entityName, Object entityId, String details) {
        log.info("[DB 작업] {} - 엔티티: {}, ID: {}, 상세: {}", operation, entityName, entityId, details);
    }

    /**
     * Redis 작업 로그
     */
    public static void logRedisOperation(String operation, String key, Object value, String details) {
        log.debug("[Redis 작업] {} - Key: {}, Value: {}, 상세: {}", operation, key, value, details);
    }

    /**
     * 에러 발생 시 컨텍스트 정보와 함께 로그
     */
    public static void logError(String errorType, String message, Throwable throwable, Map<String, Object> context) {
        StringBuilder sb = new StringBuilder();
        sb.append("[에러 발생] ").append(errorType).append(" - ").append(message);
        
        if (context != null && !context.isEmpty()) {
            sb.append(" | 컨텍스트: ");
            context.forEach((key, value) -> sb.append(key).append("=").append(value).append(", "));
        }
        
        log.error(sb.toString(), throwable);
    }

    /**
     * 성능 임계치 초과 경고
     */
    public static void logPerformanceWarning(String operation, long executionTimeMs, long thresholdMs) {
        log.warn("[성능 경고] {} - 실행시간: {}ms (임계치: {}ms 초과)", operation, executionTimeMs, thresholdMs);
    }

    /**
     * 사용자 액션 로그 (보안/감사용)
     */
    public static void logUserAction(String userUUID, String action, String details, String ipAddress) {
        log.info("[사용자 액션] 사용자: {}, 액션: {}, 상세: {}, IP: {}, 시간: {}", 
                userUUID, action, details, ipAddress, LocalDateTime.now().format(FORMATTER));
    }

    /**
     * 외부 API 호출 로그
     */
    public static void logExternalApiCall(String apiName, String url, int statusCode, long responseTime) {
        log.info("[외부 API] {} - URL: {}, 상태코드: {}, 응답시간: {}ms", apiName, url, statusCode, responseTime);
    }

    /**
     * 시스템 상태 로그
     */
    public static void logSystemStatus(String component, String status, String details) {
        log.info("[시스템 상태] {} - 상태: {}, 상세: {}", component, status, details);
    }

    /**
     * 디버깅용 변수 덤프
     */
    public static void debugVariableDump(String variableName, Object value) {
        log.debug("[변수 덤프] {} = {}", variableName, value);
    }

    /**
     * 조건부 로깅 (특정 조건에서만 로그 출력)
     */
    public static void logConditional(boolean condition, String message, Object... params) {
        if (condition) {
            log.debug("[조건부 로그] " + message, params);
        }
    }
} 