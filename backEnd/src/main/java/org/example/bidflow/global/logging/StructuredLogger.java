package org.example.bidflow.global.logging;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;

/**
 * 구조화된 로깅 유틸리티
 * - JSON 형태의 구조화된 로그 메시지 생성
 * - 메트릭 수집을 위한 로그 데이터 구조화
 */
@Slf4j
public class StructuredLogger {
    
    /**
     * Redis 이벤트 처리 성공 로그
     */
    public static void logRedisEventSuccess(String channel, String eventType, Long auctionId, String stompTopic, long processingTimeMs) {
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "redis_event_processed");
        logData.put("status", "success");
        logData.put("channel", channel);
        logData.put("eventType", eventType);
        logData.put("auctionId", auctionId);
        logData.put("stompTopic", stompTopic);
        logData.put("processingTimeMs", processingTimeMs);
        logData.put("timestamp", System.currentTimeMillis());
        
        log.info("📤 [Redis→STOMP] 메시지 처리 성공: {}", toJsonString(logData));
    }
    
    /**
     * Redis 메시지 파싱 실패 로그
     */
    public static void logParsingFailure(String channel, String payload, String errorMessage) {
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "redis_message_parsing_failed");
        logData.put("status", "failure");
        logData.put("channel", channel);
        logData.put("payloadLength", payload != null ? payload.length() : 0);
        logData.put("error", errorMessage);
        logData.put("timestamp", System.currentTimeMillis());
        
        log.warn("⚠️ [Redis] 메시지 파싱 실패: {}", toJsonString(logData));
    }
    
    /**
     * Redis 메시지 검증 실패 로그
     */
    public static void logValidationFailure(String eventType, Long auctionId, String reason) {
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "redis_message_validation_failed");
        logData.put("status", "failure");
        logData.put("eventType", eventType);
        logData.put("auctionId", auctionId);
        logData.put("reason", reason);
        logData.put("timestamp", System.currentTimeMillis());
        
        log.warn("⚠️ [Redis] 메시지 검증 실패: {}", toJsonString(logData));
    }
    
    /**
     * STOMP 전송 실패 로그
     */
    public static void logStompSendFailure(String topic, String eventType, Long auctionId, String errorMessage) {
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "stomp_send_failed");
        logData.put("status", "failure");
        logData.put("topic", topic);
        logData.put("eventType", eventType);
        logData.put("auctionId", auctionId);
        logData.put("error", errorMessage);
        logData.put("timestamp", System.currentTimeMillis());
        
        log.error("❌ [STOMP] 메시지 전송 실패: {}", toJsonString(logData));
    }
    
    /**
     * Redis 메시지 수신 로그
     */
    public static void logRedisMessageReceived(String channel, int payloadSize) {
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "redis_message_received");
        logData.put("channel", channel);
        logData.put("payloadSize", payloadSize);
        logData.put("timestamp", System.currentTimeMillis());
        
        log.debug("📨 [Redis] 메시지 수신: {}", toJsonString(logData));
    }
    
    /**
     * 알 수 없는 채널 로그
     */
    public static void logUnknownChannel(String channel) {
        Map<String, Object> logData = new HashMap<>();
        logData.put("event", "unknown_redis_channel");
        logData.put("status", "warning");
        logData.put("channel", channel);
        logData.put("timestamp", System.currentTimeMillis());
        
        log.warn("⚠️ [Redis] 알 수 없는 채널: {}", toJsonString(logData));
    }
    
    /**
     * Map을 예쁘게 포맷팅된 JSON 문자열로 변환
     */
    private static String toJsonString(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder("{\n");
        int index = 0;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (index > 0) {
                sb.append(",\n");
            }
            sb.append("  \"").append(entry.getKey()).append("\": ");
            Object value = entry.getValue();
            if (value instanceof String) {
                sb.append("\"").append(escapeJson((String) value)).append("\"");
            } else if (value instanceof Number || value instanceof Boolean) {
                sb.append(value);
            } else if (value == null) {
                sb.append("null");
            } else {
                sb.append("\"").append(escapeJson(value.toString())).append("\"");
            }
            index++;
        }
        sb.append("\n}");
        return sb.toString();
    }
    
    /**
     * JSON 문자열 이스케이프
     */
    private static String escapeJson(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    /**
     * 메트릭 수집을 위한 MDC 설정
     */
    public static void setMDC(String key, String value) {
        MDC.put(key, value);
    }
    
    /**
     * MDC 클리어
     */
    public static void clearMDC() {
        MDC.clear();
    }
}

