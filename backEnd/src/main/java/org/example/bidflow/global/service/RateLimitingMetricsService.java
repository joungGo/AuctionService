package org.example.bidflow.global.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rate Limiting 메트릭 수집 서비스
 * Prometheus와 연동하여 Rate Limiting 상태를 모니터링합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitingMetricsService {

    /** Micrometer 메트릭 레지스트리 - Prometheus 연동 */
    private final MeterRegistry meterRegistry;
    

    
    // API별 통계 추적을 위한 인메모리 저장소
    /** API별 Rate Limit 적중 횟수 (스레드 안전) */
    private final ConcurrentHashMap<String, AtomicLong> apiHitCounts = new ConcurrentHashMap<>();
    
    /** API별 전체 요청 횟수 (스레드 안전) */
    private final ConcurrentHashMap<String, AtomicLong> apiRequestCounts = new ConcurrentHashMap<>();

    // Burst Attack 감지를 위한 타이머 (태그 없는 메트릭)
    private Timer burstDetectionTimer;

    /**
     * 애플리케이션 시작 시 Prometheus 메트릭 초기화
     * Spring Bean 생성 후 자동 실행되어 타이머를 등록
     * Counter는 태그와 함께 동적으로 생성되므로 여기서 초기화하지 않음
     */
    @PostConstruct
    public void initializeMetrics() {
        // Burst 감지 성능 타이머만 초기화 (태그가 없는 메트릭)
        burstDetectionTimer = Timer.builder("burst_attack_detection_duration")
                .description("Time taken to detect burst attacks")
                .register(meterRegistry);

        log.info("[Rate Limiting Metrics] 3단계 Rate Limiting 및 Burst Protection 메트릭 초기화 완료");
    }

    /**
     * Rate Limit 적중 기록 (3단계 제한 지원)
     * 요청이 Rate Limiting에 걸렸을 때 호출되어 메트릭을 업데이트
     * 초/분/시간 단위 제한을 구분하여 메트릭 수집
     * 
     * @param limitType 제한 타입 (IP_SECOND, IP_MINUTE, IP_HOUR, USER_SECOND, API_SECOND 등)
     * @param identifier 제한 대상 식별자 (IP 주소, 사용자 ID 등)
     * @param apiPath 요청된 API 경로
     */
    public void recordRateLimitHit(String limitType, String identifier, String apiPath) {
        try {
            // 기본 Rate Limit 적중 메트릭
            Counter.builder("rate_limit_hits_total")
                    .description("Total number of rate limit hits")
                    .tag("type", limitType) // 제한 타입 태그
                    .tag("api", sanitizeApiPath(apiPath)) // API 경로 태그
                    .register(meterRegistry)
                    .increment();

            // 초 단위 제한 적중 시 전용 메트릭 추가
            if (limitType.endsWith("_SECOND")) {
                Counter.builder("rate_limit_second_hits_total")
                        .description("Total number of second-level rate limit hits")
                        .tag("type", limitType.replace("_SECOND", ""))
                        .tag("api", sanitizeApiPath(apiPath))
                        .register(meterRegistry)
                        .increment();
                
                // Burst Attack 감지 메트릭
                recordBurstAttack(limitType, identifier, apiPath);
            }

            // API별 적중률 추적을 위한 인메모리 카운터 업데이트
            String key = sanitizeApiPath(apiPath);
            apiHitCounts.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();

            log.debug("[Rate Limiting Metrics] 제한 적중 기록 - 타입: {}, API: {}, 식별자: {}", 
                    limitType, apiPath, identifier);

        } catch (Exception e) {
            log.error("[Rate Limiting Metrics] 적중 메트릭 기록 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * Rate Limit 통과 기록
     * 요청이 Rate Limiting을 통과했을 때 호출되어 메트릭을 업데이트
     * 
     * @param limitType 제한 타입 (IP, USER, API 등)
     * @param identifier 제한 대상 식별자 (IP 주소, 사용자 ID 등)
     * @param apiPath 요청된 API 경로
     */
    public void recordRateLimitMiss(String limitType, String identifier, String apiPath) {
        try {
            // 태그와 함께 카운터 생성 및 증가 (요청 통과 시)
            Counter.builder("rate_limit_misses_total")
                    .description("Total number of requests that passed rate limiting")
                    .tag("type", limitType) // 제한 타입 태그
                    .tag("api", sanitizeApiPath(apiPath)) // API 경로 태그
                    .register(meterRegistry)
                    .increment();

            // API별 전체 요청 수 추적 (적중률 계산용)
            String key = sanitizeApiPath(apiPath);
            apiRequestCounts.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();

            log.debug("[Rate Limiting Metrics] 제한 통과 기록 - 타입: {}, API: {}, 식별자: {}", 
                    limitType, apiPath, identifier);

        } catch (Exception e) {
            log.error("[Rate Limiting Metrics] 통과 메트릭 기록 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * Rate Limit 처리 오류 기록
     * Redis 연결 실패, 설정 오류 등 Rate Limiting 처리 중 발생한 오류를 기록
     * 
     * @param errorType 오류 타입 (REDIS_ERROR, CONFIG_ERROR 등)
     * @param apiPath 오류가 발생한 API 경로
     * @param errorMessage 오류 메시지
     */
    public void recordRateLimitError(String errorType, String apiPath, String errorMessage) {
        try {
            // 태그와 함께 오류 카운터 생성 및 증가
            Counter.builder("rate_limit_errors_total")
                    .description("Total number of rate limiting processing errors")
                    .tag("error_type", errorType) // 오류 타입 태그
                    .tag("api", sanitizeApiPath(apiPath)) // API 경로 태그
                    .register(meterRegistry)
                    .increment();

            log.warn("[Rate Limiting Metrics] 처리 오류 기록 - 타입: {}, API: {}, 메시지: {}", 
                    errorType, apiPath, errorMessage);

        } catch (Exception e) {
            log.error("[Rate Limiting Metrics] 오류 메트릭 기록 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * Rate Limit 검사 시간 측정 시작
     * 성능 모니터링을 위해 Rate Limiting 검사에 소요되는 시간 측정을 시작
     * 
     * @return 시간 측정 샘플 객체
     */
    public Timer.Sample startRateLimitTimer() {
        return Timer.start(meterRegistry);
    }

    /**
     * Rate Limit 검사 시간 측정 종료 및 기록
     * 시작된 시간 측정을 종료하고 결과를 Prometheus에 기록
     * 
     * @param sample 시간 측정 샘플 객체 (startRateLimitTimer()에서 반환된 값)
     * @param limitType 제한 타입 (성능 분석용 태그)
     * @param apiPath API 경로 (성능 분석용 태그)
     */
    public void stopRateLimitTimer(Timer.Sample sample, String limitType, String apiPath) {
        try {
            // 태그와 함께 타이머 생성 및 시간 측정 종료
            sample.stop(Timer.builder("rate_limit_check_duration")
                    .description("Time taken to check rate limits")
                    .tag("type", limitType) // 제한 타입 태그
                    .tag("api", sanitizeApiPath(apiPath)) // API 경로 태그
                    .register(meterRegistry));

        } catch (Exception e) {
            log.error("[Rate Limiting Metrics] 타이머 메트릭 기록 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * API별 제한 적중률 조회
     * 특정 API의 Rate Limiting 적중률을 백분율로 계산하여 반환
     * 
     * @param apiPath 조회할 API 경로
     * @return 적중률 (0.0 ~ 100.0 사이의 백분율)
     */
    public double getApiHitRate(String apiPath) {
        String key = sanitizeApiPath(apiPath);
        long hits = apiHitCounts.getOrDefault(key, new AtomicLong(0)).get(); // Rate Limit 적중 횟수
        long requests = apiRequestCounts.getOrDefault(key, new AtomicLong(0)).get(); // 전체 요청 횟수
        
        // 요청이 없으면 적중률 0%
        if (requests == 0) {
            return 0.0;
        }
        
        // 적중률 계산: (적중 횟수 / 전체 요청 수) * 100
        return (double) hits / requests * 100.0;
    }

    /**
     * Burst Attack 감지 및 기록
     * 초 단위 Rate Limiting에 걸렸을 때 Burst Attack으로 분류하여 메트릭 수집
     * 
     * @param attackType 공격 타입 (IP_SECOND, USER_SECOND, API_SECOND 등)
     * @param identifier 공격자 식별자
     * @param apiPath 공격 대상 API
     */
    public void recordBurstAttack(String attackType, String identifier, String apiPath) {
        try {
            Timer.Sample sample = Timer.start(meterRegistry);
            
            Counter.builder("rate_limit_burst_attacks_total")
                    .description("Total number of detected burst attacks")
                    .tag("attack_type", attackType)
                    .tag("api", sanitizeApiPath(apiPath))
                    .register(meterRegistry)
                    .increment();
            
            sample.stop(burstDetectionTimer);
            
            log.warn("[Burst Attack Detected] 타입: {}, 식별자: {}, API: {} - 초 단위 제한 초과로 Burst Attack 감지", 
                    attackType, identifier, apiPath);

        } catch (Exception e) {
            log.error("[Rate Limiting Metrics] Burst Attack 메트릭 기록 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 전체 Rate Limiting 통계 조회 (3단계 제한 및 Burst Attack 지원)
     * 시스템 전체의 Rate Limiting 성능 지표를 집계하여 반환
     * 태그가 있는 메트릭들의 합계를 계산하여 정확한 통계 제공
     * 
     * @return Rate Limiting 전체 통계 정보 (Burst Attack 포함)
     */
    public RateLimitingStats getOverallStats() {
        // 레지스트리에서 카운터 조회
        Counter hitCounter = meterRegistry.find("rate_limit_hits_total").counter();
        Counter missCounter = meterRegistry.find("rate_limit_misses_total").counter();
        Counter errorCounter = meterRegistry.find("rate_limit_errors_total").counter();
        
        double totalHits = hitCounter != null ? hitCounter.count() : 0.0; // 전체 Rate Limit 적중 횟수
        double totalMisses = missCounter != null ? missCounter.count() : 0.0; // 전체 Rate Limit 통과 횟수
        double totalErrors = errorCounter != null ? errorCounter.count() : 0.0; // 전체 처리 오류 횟수
        double totalRequests = totalHits + totalMisses; // 전체 처리된 요청 수
        
        // 태그가 있는 메트릭들의 합계 계산 (정확한 통계를 위해)
        double totalBurstAttacks = meterRegistry.find("rate_limit_burst_attacks_total")
                .counters()
                .stream()
                .mapToDouble(Counter::count)
                .sum();
                
        double totalSecondHits = meterRegistry.find("rate_limit_second_hits_total")
                .counters()
                .stream()
                .mapToDouble(Counter::count)
                .sum();
        
        // 전체 적중률 계산 (백분율)
        double hitRate = totalRequests > 0 ? (totalHits / totalRequests) * 100.0 : 0.0;
        
        return new RateLimitingStats(totalHits, totalMisses, totalErrors, hitRate, totalBurstAttacks, totalSecondHits);
    }

    /**
     * API 경로를 메트릭 태그에 안전한 형태로 변환
     * Prometheus 태그로 사용하기 위해 API 경로를 정제하고 표준화
     * 동적 경로 파라미터를 일반화하여 메트릭 카디널리티 제어
     * 
     * @param apiPath 원본 API 경로
     * @return 정제된 API 경로
     */
    private String sanitizeApiPath(String apiPath) {
        if (apiPath == null) {
            return "unknown";
        }
        
        // 동적 경로 매개변수를 일반화하여 메트릭 카디널리티 제어
        return apiPath
                .replaceAll("/\\d+", "/{id}") // 숫자 ID를 {id}로 치환
                .replaceAll("/[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}", "/{uuid}") // UUID를 {uuid}로 치환
                .replaceAll("[^a-zA-Z0-9/_-]", "_"); // 특수문자를 _로 치환 (Prometheus 태그 규칙 준수)
    }

    /**
     * Rate Limiting 통계 데이터 클래스 (3단계 제한 및 Burst Attack 지원)
     * 시스템 전체의 Rate Limiting 성능 지표를 담는 불변 객체
     */
    public static class RateLimitingStats {
        /** 전체 Rate Limit 적중 횟수 */
        private final double totalHits;
        
        /** 전체 Rate Limit 통과 횟수 */
        private final double totalMisses;
        
        /** 전체 처리 오류 횟수 */
        private final double totalErrors;
        
        /** 전체 적중률 (백분율) */
        private final double hitRate;
        
        /** 전체 Burst Attack 감지 횟수 */
        private final double totalBurstAttacks;
        
        /** 전체 초 단위 제한 적중 횟수 */
        private final double totalSecondHits;

        /**
         * Rate Limiting 통계 생성자 (기존 호환성)
         * 
         * @param totalHits 전체 Rate Limit 적중 횟수
         * @param totalMisses 전체 Rate Limit 통과 횟수
         * @param totalErrors 전체 처리 오류 횟수
         * @param hitRate 전체 적중률 (백분율)
         */
        public RateLimitingStats(double totalHits, double totalMisses, double totalErrors, double hitRate) {
            this(totalHits, totalMisses, totalErrors, hitRate, 0.0, 0.0);
        }

        /**
         * Rate Limiting 통계 생성자 (3단계 제한 및 Burst Attack 지원)
         * 
         * @param totalHits 전체 Rate Limit 적중 횟수
         * @param totalMisses 전체 Rate Limit 통과 횟수
         * @param totalErrors 전체 처리 오류 횟수
         * @param hitRate 전체 적중률 (백분율)
         * @param totalBurstAttacks 전체 Burst Attack 감지 횟수
         * @param totalSecondHits 전체 초 단위 제한 적중 횟수
         */
        public RateLimitingStats(double totalHits, double totalMisses, double totalErrors, double hitRate, 
                               double totalBurstAttacks, double totalSecondHits) {
            this.totalHits = totalHits;
            this.totalMisses = totalMisses;
            this.totalErrors = totalErrors;
            this.hitRate = hitRate;
            this.totalBurstAttacks = totalBurstAttacks;
            this.totalSecondHits = totalSecondHits;
        }

        /** @return 전체 Rate Limit 적중 횟수 */
        public double getTotalHits() { return totalHits; }
        
        /** @return 전체 Rate Limit 통과 횟수 */
        public double getTotalMisses() { return totalMisses; }
        
        /** @return 전체 처리 오류 횟수 */
        public double getTotalErrors() { return totalErrors; }
        
        /** @return 전체 적중률 (백분율) */
        public double getHitRate() { return hitRate; }
        
        /** @return 전체 Burst Attack 감지 횟수 */
        public double getTotalBurstAttacks() { return totalBurstAttacks; }
        
        /** @return 전체 초 단위 제한 적중 횟수 */
        public double getTotalSecondHits() { return totalSecondHits; }
        
        /** @return 전체 처리된 요청 수 (적중 + 통과) */
        public double getTotalRequests() { return totalHits + totalMisses; }
        
        /** @return Burst Attack 비율 (백분율) */
        public double getBurstAttackRate() {
            return totalSecondHits > 0 ? (totalBurstAttacks / totalSecondHits) * 100.0 : 0.0;
        }

        /**
         * 통계 정보의 문자열 표현
         * 로깅 및 디버깅 목적으로 사용
         */
        @Override
        public String toString() {
            return String.format("RateLimitingStats{hits=%.0f, misses=%.0f, errors=%.0f, hitRate=%.2f%%, " +
                               "burstAttacks=%.0f, secondHits=%.0f, burstRate=%.2f%%}", 
                    totalHits, totalMisses, totalErrors, hitRate, 
                    totalBurstAttacks, totalSecondHits, getBurstAttackRate());
        }
    }
}
