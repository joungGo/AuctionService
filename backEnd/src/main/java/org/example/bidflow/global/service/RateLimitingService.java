package org.example.bidflow.global.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.EstimationProbe;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bidflow.global.config.RateLimitingConfig;
import org.example.bidflow.global.config.RedisRateLimitingConfig.RateLimitKeyBuilder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Rate Limiting 핵심 서비스
 * Redis 기반 분산 버킷을 관리하고 요청 제한을 처리합니다.
 *
 * 검사 목적: Bucket에 토큰이 남아있는지 확인하고, 남아있다면 요청을 허용하고, 없으면 거부
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitingService {

    /** Redis 기반 분산 Bucket4j 프록시 매니저 - 토큰 버킷 관리 */
    private final LettuceBasedProxyManager proxyManager;
    
    /** Rate Limiting 설정 - 제한 정책 및 임계값 관리 */
    private final RateLimitingConfig rateLimitingConfig;
    

    /**
     * IP 기반 요청 제한 검사 (3단계: 초 → 분 → 시간)
     * 클라이언트 IP 주소를 기준으로 초/분/시간당 요청 제한을 확인
     * 익명 사용자와 인증된 사용자 모두에게 적용되는 기본 제한
     * Burst Attack 완전 차단을 위한 초 단위 제한 추가
     */
    public RateLimitResult checkIpLimit(String ipAddress) {
        if (!rateLimitingConfig.isEnabled()) {
            return RateLimitResult.allowed();
        }

        try {
            // 1단계: 초당 제한 검사 (최우선 - Burst Attack 완전 차단)
            String secondKey = RateLimitKeyBuilder.buildIpKey(ipAddress, "second");
            RateLimitResult secondResult = checkLimit(
                secondKey,
                () -> createIpBucketConfiguration(rateLimitingConfig.getDefaultIpLimit().getRequestsPerSecond(), // 기본: 10회
                                                 rateLimitingConfig.getDefaultIpLimit().getWindowSizeSecond()) // 기본: 1초
            );

            // 초당 제한 초과 시 즉시 거부 (Burst Attack 방지)
            if (!secondResult.isAllowed()) {
                log.warn("[Rate Limiting] IP 초당 제한 초과 - IP: {}, 제한: {}회/초 (Burst Attack 감지)", 
                        ipAddress, rateLimitingConfig.getDefaultIpLimit().getRequestsPerSecond());
                return secondResult;
            }

            // 2단계: 분당 제한 검사 (단기간 급증 방지)
            String minuteKey = RateLimitKeyBuilder.buildIpKey(ipAddress, "minute");
            RateLimitResult minuteResult = checkLimit(
                minuteKey,
                () -> createIpBucketConfiguration(rateLimitingConfig.getDefaultIpLimit().getRequestsPerMinute(), // 기본: 100회
                                                 rateLimitingConfig.getDefaultIpLimit().getWindowSizeMinute()) // 기본: 1분
            );

            // 분당 제한 초과 시 메트릭 기록 후 즉시 거부
            if (!minuteResult.isAllowed()) {
                log.warn("[Rate Limiting] IP 분당 제한 초과 - IP: {}, 제한: {}회/분", 
                        ipAddress, rateLimitingConfig.getDefaultIpLimit().getRequestsPerMinute());
                return minuteResult;
            }

            // 3단계: 시간당 제한 검사 (장기간 남용 방지)
            String hourKey = RateLimitKeyBuilder.buildIpKey(ipAddress, "hour");
            RateLimitResult hourResult = checkLimit(
                hourKey,
                () -> createIpBucketConfiguration(rateLimitingConfig.getDefaultIpLimit().getRequestsPerHour(), // 기본: 1000회
                                                 rateLimitingConfig.getDefaultIpLimit().getWindowSizeHour()) // 기본: 1시간
            );

            if (!hourResult.isAllowed()) {
                log.warn("[Rate Limiting] IP 시간당 제한 초과 - IP: {}, 제한: {}회/시간", 
                        ipAddress, rateLimitingConfig.getDefaultIpLimit().getRequestsPerHour());
            }

            return hourResult;

        } catch (Exception e) {
            log.error("[Rate Limiting] IP 제한 검사 중 오류 발생 - IP: {}, 오류: {}", ipAddress, e.getMessage(), e);
            // Redis 오류 시 요청 허용 (서비스 가용성 우선)
            return RateLimitResult.allowed();
        }
    }

    /**
     * 사용자 기반 요청 제한 검사 (3단계: 초 → 분 → 시간)
     * 인증된 사용자의 UUID를 기준으로 초/분/시간당 요청 제한을 확인
     * IP 기반 제한보다 더 관대한 제한을 적용 (로그인한 사용자에게 혜택 제공)
     * Burst Attack 방지를 위한 초 단위 제한 추가
     */
    public RateLimitResult checkUserLimit(String userUUID) {
        if (!rateLimitingConfig.isEnabled() || userUUID == null) {
            return RateLimitResult.allowed();
        }

        try {
            // 1단계: 초당 제한 검사 (Burst Attack 방지)
            String secondKey = RateLimitKeyBuilder.buildUserKey(userUUID, "second");
            RateLimitResult secondResult = checkLimit(
                secondKey,
                () -> createUserBucketConfiguration(rateLimitingConfig.getUserLimit().getAuthenticatedUserRequestsPerSecond(), // 20회
                                                   rateLimitingConfig.getUserLimit().getWindowSizeSecond()) // 1초
            );

            if (!secondResult.isAllowed()) {
                log.warn("[Rate Limiting] 사용자 초당 제한 초과 - User: {}, 제한: {}회/초 (Burst Attack 감지)", 
                        userUUID, rateLimitingConfig.getUserLimit().getAuthenticatedUserRequestsPerSecond());
                return secondResult;
            }

            // 2단계: 분당 제한 검사
            String minuteKey = RateLimitKeyBuilder.buildUserKey(userUUID, "minute");
            RateLimitResult minuteResult = checkLimit(
                minuteKey,
                () -> createUserBucketConfiguration(rateLimitingConfig.getUserLimit().getAuthenticatedUserRequestsPerMinute(), // 500회
                                                   rateLimitingConfig.getUserLimit().getWindowSizeMinute()) // 1분
            );

            if (!minuteResult.isAllowed()) {
                log.warn("[Rate Limiting] 사용자 분당 제한 초과 - User: {}, 제한: {}회/분", 
                        userUUID, rateLimitingConfig.getUserLimit().getAuthenticatedUserRequestsPerMinute());
                return minuteResult;
            }

            // 3단계: 시간당 제한 검사
            String hourKey = RateLimitKeyBuilder.buildUserKey(userUUID, "hour");
            RateLimitResult hourResult = checkLimit(
                hourKey,
                () -> createUserBucketConfiguration(rateLimitingConfig.getUserLimit().getAuthenticatedUserRequestsPerHour(), // 5000회
                                                   rateLimitingConfig.getUserLimit().getWindowSizeHour()) // 1시간
            );

            if (!hourResult.isAllowed()) {
                log.warn("[Rate Limiting] 사용자 시간당 제한 초과 - User: {}, 제한: {}회/시간", 
                        userUUID, rateLimitingConfig.getUserLimit().getAuthenticatedUserRequestsPerHour());
            }

            return hourResult;

        } catch (Exception e) {
            log.error("[Rate Limiting] 사용자 제한 검사 중 오류 발생 - User: {}, 오류: {}", userUUID, e.getMessage(), e);
            return RateLimitResult.allowed();
        }
    }

    /**
     * API별 요청 제한 검사 (3단계: 초 → 분 → 시간)
     * 특정 API 엔드포인트에 대한 초/분/시간당 세밀한 제한 적용
     * 모든 버킷에서 토큰을 소비하여 정확한 Rate Limiting 구현
     */
    public RateLimitResult checkApiLimit(String apiPath, String identifier) {
        if (!rateLimitingConfig.isEnabled()) {
            log.debug("[Rate Limiting] Rate Limiting이 비활성화되어 있습니다.");
            return RateLimitResult.allowed();
        }

        RateLimitingConfig.ApiLimit apiLimit = rateLimitingConfig.getApiLimit(apiPath);
        log.debug("[Rate Limiting] API 경로: {}, 매칭된 제한 설정: {}", apiPath, apiLimit != null ? "있음" : "없음");
        
        if (apiLimit == null || !apiLimit.isEnabled()) {
            log.debug("[Rate Limiting] API별 제한 설정이 없거나 비활성화됨 - 기본 제한 적용");
            return RateLimitResult.allowed();
        }
        
        log.info("[Rate Limiting] API별 제한 적용 - API: {}, 식별자: {}, 제한: {}초/{}분/{}시간", 
                apiPath, identifier, apiLimit.getRequestsPerSecond(), 
                apiLimit.getRequestsPerMinute(), apiLimit.getRequestsPerHour());

        try {
            // 버킷 생성
            String secondKey = RateLimitKeyBuilder.buildApiKey(apiPath, identifier, "second");
            String minuteKey = RateLimitKeyBuilder.buildApiKey(apiPath, identifier, "minute");
            String hourKey = RateLimitKeyBuilder.buildApiKey(apiPath, identifier, "hour");

            Bucket secondBucket = proxyManager.builder()
                    .build(secondKey.getBytes(), () -> createApiBucketConfiguration(
                        apiLimit.getRequestsPerSecond(), apiLimit.getWindowSizeSecond()));
            
            Bucket minuteBucket = proxyManager.builder()
                    .build(minuteKey.getBytes(), () -> createApiBucketConfiguration(
                        apiLimit.getRequestsPerMinute(), apiLimit.getWindowSizeMinute()));
            
            Bucket hourBucket = proxyManager.builder()
                    .build(hourKey.getBytes(), () -> createApiBucketConfiguration(
                        apiLimit.getRequestsPerHour(), apiLimit.getWindowSizeHour()));

            // 1단계: 모든 버킷의 토큰 가용성 확인 (실제 소비하지 않음)
            EstimationProbe secondProbe = secondBucket.estimateAbilityToConsume(1);
            EstimationProbe minuteProbe = minuteBucket.estimateAbilityToConsume(1);
            EstimationProbe hourProbe = hourBucket.estimateAbilityToConsume(1);

            // 가장 제한적인 버킷 찾기
            if (!secondProbe.canBeConsumed()) {
                log.warn("[Rate Limiting] API 초당 제한 초과 - API: {}, 식별자: {}, 제한: {}회/초", 
                        apiPath, identifier, apiLimit.getRequestsPerSecond());
                return RateLimitResult.rejected(
                    Duration.ofNanos(secondProbe.getNanosToWaitForRefill()),
                    secondProbe.getRemainingTokens(),
                    minuteProbe.getRemainingTokens(),
                    hourProbe.getRemainingTokens(),
                    apiLimit, "API"
                );
            }

            if (!minuteProbe.canBeConsumed()) {
                log.warn("[Rate Limiting] API 분당 제한 초과 - API: {}, 식별자: {}, 제한: {}회/분", 
                        apiPath, identifier, apiLimit.getRequestsPerMinute());
                return RateLimitResult.rejected(
                    Duration.ofNanos(minuteProbe.getNanosToWaitForRefill()),
                    secondProbe.getRemainingTokens(),
                    minuteProbe.getRemainingTokens(),
                    hourProbe.getRemainingTokens(),
                    apiLimit, "API"
                );
            }

            if (!hourProbe.canBeConsumed()) {
                log.warn("[Rate Limiting] API 시간당 제한 초과 - API: {}, 식별자: {}, 제한: {}회/시간", 
                        apiPath, identifier, apiLimit.getRequestsPerHour());
                return RateLimitResult.rejected(
                    Duration.ofNanos(hourProbe.getNanosToWaitForRefill()),
                    secondProbe.getRemainingTokens(),
                    minuteProbe.getRemainingTokens(),
                    hourProbe.getRemainingTokens(),
                    apiLimit, "API"
                );
            }

            // 2단계: 모든 제한을 통과했을 때만 실제로 토큰 소비
            ConsumptionProbe secondConsumption = secondBucket.tryConsumeAndReturnRemaining(1);
            ConsumptionProbe minuteConsumption = minuteBucket.tryConsumeAndReturnRemaining(1);
            ConsumptionProbe hourConsumption = hourBucket.tryConsumeAndReturnRemaining(1);

            // 예외적인 경우 처리 (동시성으로 인한 토큰 부족)
            if (!secondConsumption.isConsumed() || !minuteConsumption.isConsumed() || !hourConsumption.isConsumed()) {
                log.warn("[Rate Limiting] 동시성으로 인한 토큰 소비 실패 - API: {}, 식별자: {}", apiPath, identifier);
                // 가장 제한적인 버킷의 정보를 반환
                if (!secondConsumption.isConsumed()) {
                    return RateLimitResult.rejected(
                        Duration.ofNanos(secondConsumption.getNanosToWaitForRefill()),
                        secondConsumption.getRemainingTokens(),
                        minuteConsumption.getRemainingTokens(),
                        hourConsumption.getRemainingTokens(),
                        apiLimit, "API"
                    );
                } else if (!minuteConsumption.isConsumed()) {
                    return RateLimitResult.rejected(
                        Duration.ofNanos(minuteConsumption.getNanosToWaitForRefill()),
                        secondConsumption.getRemainingTokens(),
                        minuteConsumption.getRemainingTokens(),
                        hourConsumption.getRemainingTokens(),
                        apiLimit, "API"
                    );
                } else {
                    return RateLimitResult.rejected(
                        Duration.ofNanos(hourConsumption.getNanosToWaitForRefill()),
                        secondConsumption.getRemainingTokens(),
                        minuteConsumption.getRemainingTokens(),
                        hourConsumption.getRemainingTokens(),
                        apiLimit, "API"
                    );
                }
            }

            // 성공 시 모든 버킷의 정보를 포함한 결과 반환
            log.debug("[Rate Limiting] API 제한 통과 - API: {}, 남은 토큰: 초({}) 분({}) 시간({})", 
                    apiPath, secondConsumption.getRemainingTokens(), 
                    minuteConsumption.getRemainingTokens(), hourConsumption.getRemainingTokens());

            return RateLimitResult.allowed(
                secondConsumption.getRemainingTokens(),
                minuteConsumption.getRemainingTokens(),
                hourConsumption.getRemainingTokens(),
                apiLimit, "API"
            );

        } catch (Exception e) {
            log.error("[Rate Limiting] API 제한 검사 중 오류 발생 - API: {}, 식별자: {}, 오류: {}", 
                    apiPath, identifier, e.getMessage(), e);
            return RateLimitResult.allowed();
        }
    }

    /**
     * 공통 제한 검사 로직
     * Redis 기반 분산 토큰 버킷을 사용하여 실제 Rate Limiting 검사 수행
     * Bucket4j의 토큰 버킷 알고리즘을 사용하여 정확한 Rate Limiting 구현
     */
    private RateLimitResult checkLimit(String key, Supplier<BucketConfiguration> configSupplier) {
        try {
            // Redis에서 분산 토큰 버킷 생성 또는 조회 (String 키를 byte[]로 변환)
            Bucket bucket = proxyManager.builder()
                    .build(key.getBytes(), configSupplier);

            // 1개의 토큰 소비 시도 (1회 요청에 해당)
            // 반환값: 소비 성공 여부, 남은 토큰 수, 다음 리필 시간 등의 정보
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

            if (probe.isConsumed()) {
                // 토큰 소비 성공 - 요청 허용
                return RateLimitResult.allowed(probe.getRemainingTokens());
            } else {
                // 토큰 부족 - 요청 거부
                return RateLimitResult.rejected(
                    Duration.ofNanos(probe.getNanosToWaitForRefill()), // 다음 토큰 리필까지 대기 시간
                    probe.getRemainingTokens() // 현재 남은 토큰 수 (보통 0)
                );
            }
        } catch (Exception e) {
            log.error("[Rate Limiting] 버킷 작업 중 오류 발생 - Key: {}, 오류: {}", key, e.getMessage(), e);
            return RateLimitResult.allowed(); // Rate Limiting 오류 발생 시 요청 허용 (서비스 가용성 우선 목적)
        }
    }

    /**
     * IP 기반 버킷 설정 생성
     * 토큰 버킷의 용량과 리필 주기를 설정하여 IP별 Rate Limiting 정책 정의
     * 
     * @param capacity 버킷의 최대 토큰 수 (허용되는 최대 요청 수)
     * @param refillPeriod 토큰 리필 주기 (얼마나 자주 토큰을 보충할지)
     * @return Bucket4j 버킷 설정
     */
    private BucketConfiguration createIpBucketConfiguration(int capacity, Duration refillPeriod) {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.simple(capacity, refillPeriod)) // 단순 대역폭: 지정된 주기마다 토큰을 모두 리필
                .build();
    }

    /**
     * 사용자 기반 버킷 설정 생성
     * 인증된 사용자에 대한 토큰 버킷 설정 (IP 기반보다 더 관대한 제한)
     * 
     * @param capacity 버킷의 최대 토큰 수 (사용자별 허용 요청 수)
     * @param refillPeriod 토큰 리필 주기
     * @return Bucket4j 버킷 설정
     */
    private BucketConfiguration createUserBucketConfiguration(int capacity, Duration refillPeriod) {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.simple(capacity, refillPeriod))
                .build();
    }

    /**
     * API 기반 버킷 설정 생성
     * 특정 API 엔드포인트에 대한 세밀한 Rate Limiting 설정
     * 
     * @param capacity 버킷의 최대 토큰 수 (API별 허용 요청 수)
     * @param refillPeriod 토큰 리필 주기
     * @return Bucket4j 버킷 설정
     */
    private BucketConfiguration createApiBucketConfiguration(int capacity, Duration refillPeriod) {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.simple(capacity, refillPeriod))
                .build();
    }

    /**
     * Rate Limiting 결과를 담는 클래스
     * 요청 허용/거부 여부와 관련 메타데이터를 포함하는 불변 객체
     * 다중 시간 단위 토큰 정보 포함
     */
    public static class RateLimitResult {
        /** 요청 허용 여부 (true: 허용, false: 거부) */
        private final boolean allowed;
        
        /** 현재 남은 토큰 수 (가장 제한적인 버킷 기준) */
        private final long remainingTokens;
        
        /** 다음 토큰 리필까지 대기 시간 (거부된 경우에만 의미 있음) */
        private final Duration retryAfter;
        
        /** 초당 버킷의 남은 토큰 수 */
        private final long secondRemainingTokens;
        
        /** 분당 버킷의 남은 토큰 수 */
        private final long minuteRemainingTokens;
        
        /** 시간당 버킷의 남은 토큰 수 */
        private final long hourRemainingTokens;
        
        /** 적용된 API 제한 설정 */
        private final RateLimitingConfig.ApiLimit appliedLimit;
        
        /** 제한 타입 (IP, USER, API) */
        private final String limitType;

        private RateLimitResult(boolean allowed, long remainingTokens, Duration retryAfter,
                               long secondRemaining, long minuteRemaining, long hourRemaining,
                               RateLimitingConfig.ApiLimit appliedLimit, String limitType) {
            this.allowed = allowed;
            this.remainingTokens = remainingTokens;
            this.retryAfter = retryAfter;
            this.secondRemainingTokens = secondRemaining;
            this.minuteRemainingTokens = minuteRemaining;
            this.hourRemainingTokens = hourRemaining;
            this.appliedLimit = appliedLimit;
            this.limitType = limitType;
        }

        /** 요청 허용 결과 생성 (남은 토큰 수 불명) */
        public static RateLimitResult allowed() {
            return new RateLimitResult(true, -1, null, -1, -1, -1, null, "UNKNOWN");
        }

        /** 요청 허용 결과 생성 (남은 토큰 수 포함) */
        public static RateLimitResult allowed(long remainingTokens) {
            return new RateLimitResult(true, remainingTokens, null, -1, -1, -1, null, "BASIC");
        }

        /** 요청 허용 결과 생성 (다중 시간 단위 토큰 정보 포함) */
        public static RateLimitResult allowed(long secondRemaining, long minuteRemaining, 
                                            long hourRemaining, RateLimitingConfig.ApiLimit appliedLimit,
                                            String limitType) {
            long minRemaining = Math.min(Math.min(secondRemaining, minuteRemaining), hourRemaining);
            return new RateLimitResult(true, minRemaining, null, 
                                     secondRemaining, minuteRemaining, hourRemaining, 
                                     appliedLimit, limitType);
        }

        /** 요청 거부 결과 생성 (재시도 시간과 남은 토큰 수 포함) */
        public static RateLimitResult rejected(Duration retryAfter, long remainingTokens) {
            return new RateLimitResult(false, remainingTokens, retryAfter, -1, -1, -1, null, "BASIC");
        }

        /** 요청 거부 결과 생성 (다중 시간 단위 토큰 정보 포함) */
        public static RateLimitResult rejected(Duration retryAfter, long secondRemaining, 
                                             long minuteRemaining, long hourRemaining,
                                             RateLimitingConfig.ApiLimit appliedLimit, String limitType) {
            long minRemaining = Math.min(Math.min(secondRemaining, minuteRemaining), hourRemaining);
            return new RateLimitResult(false, minRemaining, retryAfter,
                                     secondRemaining, minuteRemaining, hourRemaining,
                                     appliedLimit, limitType);
        }

        /** @return 요청 허용 여부 */
        public boolean isAllowed() { return allowed; }
        
        /** @return 현재 남은 토큰 수 */
        public long getRemainingTokens() { return remainingTokens; }
        
        /** @return 다음 토큰 리필까지 대기 시간 */
        public Duration getRetryAfter() { return retryAfter; }
        
        /** @return 초당 버킷의 남은 토큰 수 */
        public long getSecondRemainingTokens() { return secondRemainingTokens; }
        
        /** @return 분당 버킷의 남은 토큰 수 */
        public long getMinuteRemainingTokens() { return minuteRemainingTokens; }
        
        /** @return 시간당 버킷의 남은 토큰 수 */
        public long getHourRemainingTokens() { return hourRemainingTokens; }
        
        /** @return 적용된 API 제한 설정 */
        public RateLimitingConfig.ApiLimit getAppliedLimit() { return appliedLimit; }
        
        /** @return 제한 타입 */
        public String getLimitType() { return limitType; }
    }
}
