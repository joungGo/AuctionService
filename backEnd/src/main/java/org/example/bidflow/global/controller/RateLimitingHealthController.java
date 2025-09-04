package org.example.bidflow.global.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bidflow.global.config.RateLimitingConfig;
import org.example.bidflow.global.service.RateLimitingMetricsService;
import org.example.bidflow.global.service.RateLimitingMetricsService.RateLimitingStats;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Rate Limiting 상태 모니터링 컨트롤러
 * 관리자가 Rate Limiting 상태를 확인할 수 있는 엔드포인트를 제공합니다.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/rate-limiting")
@RequiredArgsConstructor
public class RateLimitingHealthController implements HealthIndicator {

    /** Rate Limiting 설정 정보 - 현재 적용된 제한 정책 조회용 */
    private final RateLimitingConfig rateLimitingConfig;
    
    /** Rate Limiting 메트릭 서비스 - 통계 및 성능 정보 조회용 */
    private final RateLimitingMetricsService metricsService;

    /**
     * Rate Limiting 전체 상태 조회
     * 관리자가 현재 Rate Limiting 시스템의 전반적인 상태를 확인할 수 있는 엔드포인트
     * 설정 정보, 통계, API별 적중률 등 종합적인 정보를 제공
     * 
     * @return Rate Limiting 상태 정보가 담긴 응답
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getRateLimitingStatus() {
        try {
            Map<String, Object> status = new HashMap<>();
            
            // 기본 설정 정보 (현재 적용된 Rate Limiting 정책)
            status.put("enabled", rateLimitingConfig.isEnabled());
            status.put("defaultIpLimit", rateLimitingConfig.getDefaultIpLimit());
            status.put("userLimit", rateLimitingConfig.getUserLimit());
            
            // 실시간 통계 정보 (Prometheus 메트릭 기반)
            RateLimitingStats stats = metricsService.getOverallStats();
            status.put("statistics", Map.of(
                "totalRequests", stats.getTotalRequests(), // 전체 요청 수
                "totalHits", stats.getTotalHits(), // Rate Limit 적중 수
                "totalMisses", stats.getTotalMisses(), // Rate Limit 통과 수
                "totalErrors", stats.getTotalErrors(), // 처리 오류 수
                "hitRate", String.format("%.2f%%", stats.getHitRate()) // 적중률 (백분율)
            ));
            
            // 주요 API별 적중률 분석 (모니터링 대상 API들)
            Map<String, String> apiHitRates = new HashMap<>();
            String[] monitoredApis = {
                "/api/auth/login", "/api/auth/signup", "/api/auth/send-code", // 인증 관련 API
                "/api/auctions", "/api/admin/auctions" // 핵심 비즈니스 API
            };
            
            for (String api : monitoredApis) {
                double hitRate = metricsService.getApiHitRate(api);
                apiHitRates.put(api, String.format("%.2f%%", hitRate));
            }
            status.put("apiHitRates", apiHitRates);
            
            log.debug("[Rate Limiting Health] 상태 조회 완료 - 활성화: {}, 전체 적중률: {}", 
                    rateLimitingConfig.isEnabled(), stats.getHitRate());
            
            return ResponseEntity.ok(status);
            
        } catch (Exception e) {
            log.error("[Rate Limiting Health] 상태 조회 실패: {}", e.getMessage(), e);
            
            // 오류 발생 시에도 기본 정보는 제공
            Map<String, Object> errorStatus = new HashMap<>();
            errorStatus.put("enabled", rateLimitingConfig.isEnabled());
            errorStatus.put("error", "상태 조회 중 오류가 발생했습니다.");
            errorStatus.put("errorMessage", e.getMessage());
            errorStatus.put("timestamp", String.valueOf(System.currentTimeMillis()));
            
            return ResponseEntity.status(500).body(errorStatus);
        }
    }

    /**
     * Rate Limiting 설정 조회
     * 현재 적용된 모든 Rate Limiting 정책 설정을 조회
     * 관리자가 설정 확인 및 디버깅 시 사용
     * 
     * @return 현재 Rate Limiting 설정 정보
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getRateLimitingConfig() {
        try {
            Map<String, Object> config = new HashMap<>();
            config.put("enabled", rateLimitingConfig.isEnabled()); // 전역 활성화 상태
            config.put("defaultIpLimit", rateLimitingConfig.getDefaultIpLimit()); // IP 기반 기본 제한
            config.put("userLimit", rateLimitingConfig.getUserLimit()); // 사용자 기반 제한
            config.put("apiLimits", rateLimitingConfig.getApiLimits()); // API별 개별 제한 정책
            
            return ResponseEntity.ok(config);
            
        } catch (Exception e) {
            log.error("[Rate Limiting Health] 설정 조회 실패: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "설정 조회 중 오류가 발생했습니다.",
                "errorMessage", e.getMessage(),
                "timestamp", String.valueOf(System.currentTimeMillis())
            ));
        }
    }

    /**
     * Spring Boot Actuator Health Indicator 구현
     * /actuator/health 엔드포인트에서 Rate Limiting 시스템의 건강 상태를 체크
     * 적중률이나 오류율이 높으면 DOWN 상태로 표시하여 모니터링 알림 발생
     * 
     * @return Rate Limiting 시스템의 건강 상태
     */
    @Override
    public Health health() {
        try {
            // Rate Limiting이 비활성화된 경우 (정상 상태로 간주)
            if (!rateLimitingConfig.isEnabled()) {
                return Health.up()
                        .withDetail("status", "disabled")
                        .withDetail("message", "Rate Limiting이 비활성화되어 있습니다.")
                        .build();
            }

            RateLimitingStats stats = metricsService.getOverallStats();
            
            // 적중률이 너무 높으면 경고 상태 (과도한 요청 차단 중)
            if (stats.getHitRate() > 50.0) {
                return Health.down()
                        .withDetail("status", "high_hit_rate")
                        .withDetail("hitRate", String.format("%.2f%%", stats.getHitRate()))
                        .withDetail("message", "Rate Limiting 적중률이 높습니다. 설정 검토가 필요할 수 있습니다.")
                        .withDetail("totalRequests", stats.getTotalRequests())
                        .withDetail("totalHits", stats.getTotalHits())
                        .withDetail("recommendation", "API 제한 설정을 완화하거나 DDoS 공격 여부를 확인하세요.")
                        .build();
            }

            // 처리 오류가 많으면 경고 상태 (시스템 문제 가능성)
            if (stats.getTotalErrors() > 100) {
                return Health.down()
                        .withDetail("status", "high_error_rate")
                        .withDetail("totalErrors", stats.getTotalErrors())
                        .withDetail("message", "Rate Limiting 처리 오류가 많이 발생하고 있습니다.")
                        .withDetail("recommendation", "Redis 연결 상태나 시스템 리소스를 확인하세요.")
                        .build();
            }

            // 정상 상태 (모든 지표가 양호)
            return Health.up()
                    .withDetail("status", "healthy")
                    .withDetail("hitRate", String.format("%.2f%%", stats.getHitRate()))
                    .withDetail("totalRequests", stats.getTotalRequests())
                    .withDetail("totalErrors", stats.getTotalErrors())
                    .withDetail("message", "Rate Limiting이 정상적으로 작동하고 있습니다.")
                    .build();

        } catch (Exception e) {
            log.error("[Rate Limiting Health] Health Check 실패: {}", e.getMessage(), e);
            return Health.down()
                    .withDetail("status", "error")
                    .withDetail("error", e.getMessage())
                    .withDetail("message", "Rate Limiting 상태 확인 중 오류가 발생했습니다.")
                    .withDetail("timestamp", String.valueOf(System.currentTimeMillis()))
                    .withDetail("recommendation", "시스템 로그를 확인하고 필요시 서비스를 재시작하세요.")
                    .build();
        }
    }
}
