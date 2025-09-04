package org.example.bidflow.global.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;
import java.util.HashMap;

/**
 * Rate Limiting 설정 클래스
 * API별, 사용자별 요청 제한 정책을 정의합니다.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "rate-limiting")
public class RateLimitingConfig {

    /**
     * Rate Limiting 전역 활성화/비활성화
     * false로 설정 시 모든 Rate Limiting이 비활성화됨
     * 환경변수 RATE_LIMITING_ENABLED로 제어 가능
     */
    private boolean enabled = true;

    /**
     * 기본 IP 기반 제한 설정
     * 모든 IP에 대해 적용되는 기본 요청 제한
     * 익명 사용자 및 인증되지 않은 요청에 적용
     */
    private IpBasedLimit defaultIpLimit = new IpBasedLimit();

    /**
     * API별 제한 설정
     * 특정 API 경로에 대한 개별적인 Rate Limiting 정책
     */
    private Map<String, ApiLimit> apiLimits = new HashMap<>();

    /**
     * 사용자별 제한 설정
     * 인증된 사용자에게 적용되는 요청 제한
     * IP 기반 제한보다 더 관대한 제한을 적용
     */
    private UserBasedLimit userLimit = new UserBasedLimit();

    public RateLimitingConfig() {
        initializeDefaultLimits();
    }

    /**
     * 기본 제한 정책 초기화
     * 애플리케이션 시작 시 각 API별 기본 Rate Limiting 정책을 설정
     * 보안 수준에 따라 엄격함 정도를 차등 적용
     * 3단계 제한 (초/분/시간) 적용으로 Burst Attack 완전 방지
     */
    private void initializeDefaultLimits() {
        // 기본 IP 제한: 10회/초, 100회/분, 1000회/시간 (익명 사용자 대상)
        defaultIpLimit.setRequestsPerSecond(10);   // Burst Attack 방지
        defaultIpLimit.setRequestsPerMinute(100);
        defaultIpLimit.setRequestsPerHour(1000);

        // 인증 관련 API 제한 (매우 엄격) - 브루트포스 공격 완전 차단
        apiLimits.put("/api/auth/login", new ApiLimit(
            2,   // 초당 2회 (Burst 완전 차단)
            5,   // 분당 5회
            20,  // 시간당 20회
            Duration.ofSeconds(1), Duration.ofMinutes(1), Duration.ofHours(1)
        ));
        
        apiLimits.put("/api/auth/signup", new ApiLimit(
            1,   // 초당 1회 (매우 엄격)
            3,   // 분당 3회
            10,  // 시간당 10회
            Duration.ofSeconds(1), Duration.ofMinutes(1), Duration.ofHours(1)
        ));
        
        apiLimits.put("/api/auth/send-code", new ApiLimit(
            1,   // 초당 1회 (이메일 스팸 완전 차단)
            3,   // 분당 3회
            5,   // 시간당 5회
            Duration.ofSeconds(1), Duration.ofMinutes(1), Duration.ofHours(1)
        ));

        // 입찰 관련 API 제한 (중간) - 경매 시스템 안정성 확보
        apiLimits.put("/api/bids/**", new ApiLimit(
            5,   // 초당 5회
            30,  // 분당 30회
            200, // 시간당 200회
            Duration.ofSeconds(1), Duration.ofMinutes(1), Duration.ofHours(1)
        ));
        
        apiLimits.put("/api/auctions/*/bids", new ApiLimit(
            10,  // 초당 10회 (조회는 좀 더 관대)
            50,  // 분당 50회
            300, // 시간당 300회
            Duration.ofSeconds(1), Duration.ofMinutes(1), Duration.ofHours(1)
        ));

        // 일반 조회 API 제한 (완화) - 사용자 편의성 고려
        apiLimits.put("/api/auctions", new ApiLimit(
            20,   // 초당 20회
            200,  // 분당 200회
            2000, // 시간당 2000회
            Duration.ofSeconds(1), Duration.ofMinutes(1), Duration.ofHours(1)
        ));
        
        apiLimits.put("/api/auctions/*", new ApiLimit(
            30,   // 초당 30회
            300,  // 분당 300회
            3000, // 시간당 3000회
            Duration.ofSeconds(1), Duration.ofMinutes(1), Duration.ofHours(1)
        ));

        // 관리자 API 제한 - 관리 기능 남용 방지
        apiLimits.put("/api/admin/**", new ApiLimit(
            10,  // 초당 10회
            100, // 분당 100회
            500, // 시간당 500회
            Duration.ofSeconds(1), Duration.ofMinutes(1), Duration.ofHours(1)
        ));

        // 사용자별 제한 설정 - 인증된 사용자에게 더 관대한 제한
        userLimit.setAuthenticatedUserRequestsPerSecond(20);   // 초당 20회
        userLimit.setAuthenticatedUserRequestsPerMinute(500);  // 분당 500회
        userLimit.setAuthenticatedUserRequestsPerHour(5000);   // 시간당 5000회
    }

    /**
     * IP 기반 Rate Limiting 설정 클래스
     * 클라이언트 IP 주소를 기준으로 한 요청 제한 정책
     * 3단계 제한: 초 → 분 → 시간 (Burst Attack 방지)
     */
    @Data
    public static class IpBasedLimit {
        /** 초당 허용 요청 수 (Burst Attack 방지) */
        private int requestsPerSecond = 10;
        
        /** 분당 허용 요청 수 */
        private int requestsPerMinute = 100;
        
        /** 시간당 허용 요청 수 */
        private int requestsPerHour = 1000;
        
        /** 초 단위 시간 윈도우 크기 */
        private Duration windowSizeSecond = Duration.ofSeconds(1);
        
        /** 분 단위 시간 윈도우 크기 */
        private Duration windowSizeMinute = Duration.ofMinutes(1);
        
        /** 시간 단위 시간 윈도우 크기 */
        private Duration windowSizeHour = Duration.ofHours(1);
    }

    /**
     * API별 Rate Limiting 설정 클래스
     * 특정 API 엔드포인트에 대한 개별적인 제한 정책
     * 3단계 제한: 초 → 분 → 시간 (Burst Attack 방지)
     */
    @Data
    public static class ApiLimit {
        /** 초당 허용 요청 수 (Burst Attack 방지) */
        private int requestsPerSecond;
        
        /** 분당 허용 요청 수 */
        private int requestsPerMinute;
        
        /** 시간당 허용 요청 수 */
        private int requestsPerHour;
        
        /** 초 단위 시간 윈도우 크기 */
        private Duration windowSizeSecond = Duration.ofSeconds(1);
        
        /** 분 단위 시간 윈도우 크기 */
        private Duration windowSizeMinute;
        
        /** 시간 단위 시간 윈도우 크기 */
        private Duration windowSizeHour;
        
        /** 이 API 제한 활성화 여부 */
        private boolean enabled = true;

        /**
         * API 제한 설정 생성자 (3단계 제한)
         * 
         * @param requestsPerSecond 초당 허용 요청 수
         * @param requestsPerMinute 분당 허용 요청 수
         * @param requestsPerHour 시간당 허용 요청 수
         * @param windowSizeSecond 초 단위 윈도우 크기
         * @param windowSizeMinute 분 단위 윈도우 크기
         * @param windowSizeHour 시간 단위 윈도우 크기
         */
        public ApiLimit(int requestsPerSecond, int requestsPerMinute, int requestsPerHour, 
                       Duration windowSizeSecond, Duration windowSizeMinute, Duration windowSizeHour) {
            this.requestsPerSecond = requestsPerSecond;
            this.requestsPerMinute = requestsPerMinute;
            this.requestsPerHour = requestsPerHour;
            this.windowSizeSecond = windowSizeSecond;
            this.windowSizeMinute = windowSizeMinute;
            this.windowSizeHour = windowSizeHour;
        }

        /**
         * API 제한 설정 생성자 (기존 호환성)
         * 초 단위 제한은 기본값(5초) 적용
         * 
         * @param requestsPerMinute 분당 허용 요청 수
         * @param requestsPerHour 시간당 허용 요청 수
         * @param windowSizeMinute 분 단위 윈도우 크기
         * @param windowSizeHour 시간 단위 윈도우 크기
         */
        public ApiLimit(int requestsPerMinute, int requestsPerHour, Duration windowSizeMinute, Duration windowSizeHour) {
            this.requestsPerSecond = 5; // 기본값: 초당 5회
            this.requestsPerMinute = requestsPerMinute;
            this.requestsPerHour = requestsPerHour;
            this.windowSizeSecond = Duration.ofSeconds(1);
            this.windowSizeMinute = windowSizeMinute;
            this.windowSizeHour = windowSizeHour;
        }
    }

    /**
     * 사용자 기반 Rate Limiting 설정 클래스
     * 인증된 사용자에 대한 요청 제한 정책
     * 3단계 제한: 초 → 분 → 시간 (인증된 사용자에게 더 관대한 제한)
     */
    @Data
    public static class UserBasedLimit {
        /** 인증된 사용자의 초당 허용 요청 수 */
        private int authenticatedUserRequestsPerSecond = 20;
        
        /** 인증된 사용자의 분당 허용 요청 수 */
        private int authenticatedUserRequestsPerMinute = 500;
        
        /** 인증된 사용자의 시간당 허용 요청 수 */
        private int authenticatedUserRequestsPerHour = 5000;
        
        /** 초 단위 시간 윈도우 크기 */
        private Duration windowSizeSecond = Duration.ofSeconds(1);
        
        /** 분 단위 시간 윈도우 크기 */
        private Duration windowSizeMinute = Duration.ofMinutes(1);
        
        /** 시간 단위 시간 윈도우 크기 */
        private Duration windowSizeHour = Duration.ofHours(1);
    }

    /**
     * 특정 API 패턴에 대한 제한 설정 조회
     * 정확한 매칭을 우선하고, 와일드카드 패턴 매칭을 지원
     * 
     * @param apiPath 조회할 API 경로
     * @return 해당 API에 적용될 제한 설정, 없으면 null (기본 제한 적용)
     */
    public ApiLimit getApiLimit(String apiPath) {
        // 정확한 매칭 우선 (예: /api/auth/login)
        if (apiLimits.containsKey(apiPath)) {
            return apiLimits.get(apiPath);
        }

        // 패턴 매칭 (와일드카드 지원)
        for (Map.Entry<String, ApiLimit> entry : apiLimits.entrySet()) {
            String pattern = entry.getKey();
            // 패턴 매칭 (예: /api/auth/** → /api/auth/login)
            if (pattern.endsWith("/**") && apiPath.startsWith(pattern.substring(0, pattern.length() - 3))) {
                return entry.getValue();
            }
            // 단일 * 패턴 매칭 (예: /api/auctions/* → /api/auctions/123)
            if (pattern.contains("*") && matchesWildcard(apiPath, pattern)) {
                return entry.getValue();
            }
        }

        return null; // 매칭되는 패턴이 없으면 null 반환 (기본 제한 적용)
    }

    /**
     * 정규표현식을 사용하여 * 와일드카드 패턴을 실제 경로와 매칭
     */
    private boolean matchesWildcard(String path, String pattern) {
        // * → [^/]* (슬래시 제외한 모든 문자), /** → /.* (모든 경로)
        String regex = pattern.replace("*", "[^/]*").replace("/**", "/.*");
        return path.matches(regex);
    }
}
