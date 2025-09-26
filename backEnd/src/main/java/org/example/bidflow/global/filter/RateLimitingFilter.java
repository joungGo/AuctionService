package org.example.bidflow.global.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bidflow.global.config.RateLimitingConfig;
import org.example.bidflow.global.service.RateLimitingService;
import org.example.bidflow.global.service.RateLimitingService.RateLimitResult;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Rate Limiting 필터
 * 모든 HTTP 요청에 대해 IP 기반 및 사용자 기반 요청 제한을 적용합니다.
 */
@Slf4j
@Component
@Order(2) // JwtAuthenticationFilter 다음에 실행 -> 인증된 사용자 정보가 필요하기 때문!
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

    /** Rate Limiting 핵심 서비스 - 실제 제한 검사 로직 담당 */
    private final RateLimitingService rateLimitingService;
    
    /** JSON 직렬화/역직렬화 - 에러 응답 생성용 */
    private final ObjectMapper objectMapper;

    /**
     * 특정 요청에 대해 Rate Limiting을 적용하지 않을지 결정
     * WebSocket 연결과 CORS Preflight 요청은 제외
     *
     * @return true이면 필터를 적용하지 않음, false이면 적용
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        String method = request.getMethod();
        
        // WebSocket 연결은 제외 (별도의 Rate Limiting 메커니즘 사용)
        if (path.startsWith("/ws")) {
            return true;
        }
        
        // OPTIONS 요청은 제외 (CORS preflight 요청은 실제 API 호출이 아님)
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }
        
        return false; // 나머지 모든 요청에 Rate Limiting 적용
    }

    /**
     * Rate Limiting 핵심 처리 로직
     * 1. IP 기반 제한 검사 2. 사용자 기반 제한 검사 3. API별 제한 검사를 순차적으로 수행
     * 4. 성공 응답에 Rate Limit 정보 추가
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String clientIp = null;
        String requestUri = null;
        String userUUID = null;
        RateLimitResult finalResult = null;

        try {
            clientIp = getClientIpAddress(request);
            requestUri = request.getRequestURI(); // 요청된 API 경로
            
            log.debug("[Rate Limiting] 요청 검사 시작 - IP: {}, URI: {}", clientIp, requestUri);

            // 1. IP 기반 제한 검사 (모든 요청에 적용되는 기본 제한)
            RateLimitResult ipResult = rateLimitingService.checkIpLimit(clientIp);
            if (!ipResult.isAllowed()) {
                handleRateLimitExceeded(response, ipResult, "IP", clientIp);
                return;
            }

            // 2. 인증된 사용자의 경우 추가 제한 검사 (더 관대한 제한)
            userUUID = getCurrentUserUUID();
            if (userUUID != null) {
                RateLimitResult userResult = rateLimitingService.checkUserLimit(userUUID);
                if (!userResult.isAllowed()) {
                    handleRateLimitExceeded(response, userResult, "USER", userUUID);
                    return;
                }
            }

            // 3. API별 제한 검사 (특정 API에 대한 세밀한 제한)
            String identifier = userUUID != null ? userUUID : clientIp;
            RateLimitResult apiResult = rateLimitingService.checkApiLimit(requestUri, identifier);
            if (!apiResult.isAllowed()) {
                handleRateLimitExceeded(response, apiResult, "API", requestUri);
                return;
            }

            // 최종 결과 저장 (성공 응답에 포함할 정보)
            finalResult = apiResult;

            // 4. 응답 헤더에 Rate Limit 정보 추가 (클라이언트 가이드용)
            addRateLimitHeaders(response, apiResult, userUUID != null, requestUri);

            log.debug("[Rate Limiting] 요청 허용 - IP: {}, URI: {}, User: {}", clientIp, requestUri, userUUID);
            
        } catch (Exception e) {
            log.error("[Rate Limiting] 필터 처리 중 오류 발생: {}", e.getMessage(), e);
            // 오류 발생 시 요청 허용 (서비스 가용성 우선 - Fail Open 정책)
        }

        // Rate Limiting 통과 시 ResponseWrapper로 감싸서 성공 응답에 토큰 정보 추가
        if (finalResult != null && finalResult.isAllowed()) {
            RateLimitResponseWrapper responseWrapper = new RateLimitResponseWrapper(response, objectMapper, finalResult, requestUri);
            filterChain.doFilter(request, responseWrapper);
            responseWrapper.copyBodyToResponse();
        } else {
            // Rate Limit 정보가 없는 경우 (오류 발생 등) 원본 응답 사용
            filterChain.doFilter(request, response);
        }
    }

    /**
     * 클라이언트 IP 주소 추출
     * 프록시, 로드밸런서 환경을 고려한 실제 IP 추출
     * ALB(Application Load Balancer) 환경에서 X-Forwarded-For 헤더 우선 처리
     * 
     * @param request HTTP 요청 객체
     * @return 클라이언트의 실제 IP 주소
     */
    private String getClientIpAddress(HttpServletRequest request) {
        // X-Forwarded-For 헤더 확인 (ALB, 프록시 환경)
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            // 첫 번째 IP가 실제 클라이언트 IP
            log.info("추출한 IP 주소(X-Forwarded-For 헤더 - ALB, 프록시 환경) {}", xForwardedFor.split(",")[0].trim());
            return xForwardedFor.split(",")[0].trim();
        }

        // X-Real-IP 헤더 확인 (Nginx 프록시에서 주로 사용)
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            log.info("추출한 IP 주소(X-Real-IP 헤더 - Nginx 프록시) {}", xRealIp);
            return xRealIp;
        }

        // X-Forwarded 헤더 확인 (일반적인 프록시 환경)
        String xForwarded = request.getHeader("X-Forwarded");
        if (xForwarded != null && !xForwarded.isEmpty() && !"unknown".equalsIgnoreCase(xForwarded)) {
            log.info("추출한 IP 주소(X-Forwarded 헤더 - 일반적인 프록시) {}", xForwarded);
            return xForwarded;
        }

        // Forwarded-For 헤더 확인 (표준 RFC 7239)
        String forwardedFor = request.getHeader("Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(forwardedFor)) {
            log.info("추출한 IP 주소(Forwarded-For 헤더 - 표준 RFC 7239) {}", forwardedFor);
            return forwardedFor;
        }

        // 모든 프록시 헤더가 없으면 기본 remote address 사용
        log.info("추출한 IP 주소(기본 remote address - 프록시 헤더 없음) {}", request.getRemoteAddr());
        return request.getRemoteAddr();
    }

    /**
     * 현재 인증된 사용자의 UUID 추출
     * Spring Security 컨텍스트에서 인증된 사용자 정보를 가져옴
     * JWT 필터가 먼저 실행되어 인증 정보가 설정된 상태
     * 
     * @return 인증된 사용자의 UUID, 미인증 시 null
     */
    private String getCurrentUserUUID() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                String username = authentication.getName();
                if (!"anonymousUser".equals(username)) {
                    // JWT에서 userUUID 추출 시도 (실제 구현에 따라 조정 필요)
                    return username; // JWT에서 userUUID 추출
                }
            }
        } catch (Exception e) {
            log.debug("[Rate Limiting] 사용자 정보 추출 실패: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Rate Limit 초과 시 응답 처리
     * HTTP 429 상태 코드와 함께 상세한 에러 정보를 JSON으로 반환
     * 
     * @param response HTTP 응답 객체
     * @param result Rate Limiting 검사 결과
     * @param limitType 제한 타입 (IP, USER, API)
     * @param identifier 제한 대상 식별자
     */
    private void handleRateLimitExceeded(HttpServletResponse response, RateLimitResult result, 
                                       String limitType, String identifier) throws IOException {
        
        log.warn("[Rate Limiting] 요청 제한 초과 - 타입: {}, 식별자: {}, 재시도 가능 시간: {}초", 
                limitType, identifier, result.getRetryAfter() != null ? result.getRetryAfter().getSeconds() : "N/A");

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value()); // HTTP 429 상태 코드
        response.setContentType(MediaType.APPLICATION_JSON_VALUE); // JSON 응답 타입
        response.setCharacterEncoding("UTF-8"); // UTF-8 인코딩

        // Retry-After 헤더 설정 (클라이언트가 재시도할 수 있는 시간)
        if (result.getRetryAfter() != null) {
            response.setHeader("Retry-After", String.valueOf(result.getRetryAfter().getSeconds()));
        }

        // Rate Limit 관련 커스텀 헤더 설정
        response.setHeader("X-RateLimit-Limit-Type", limitType); // 제한 타입
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, result.getRemainingTokens()))); // 남은 요청 수

        // 사용자 친화적인 에러 응답 생성
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요."); // 사용자 메시지
        errorResponse.put("errorType", "RATE_LIMIT_EXCEEDED"); // 에러 타입
        errorResponse.put("limitType", limitType); // 제한 타입 (IP/USER/API)
        errorResponse.put("timestamp", String.valueOf(System.currentTimeMillis())); // 타임스탬프
        
        // 재시도 가능 시간 정보 추가
        if (result.getRetryAfter() != null) {
            errorResponse.put("retryAfterSeconds", result.getRetryAfter().getSeconds());
            errorResponse.put("retryAfterMillis", result.getRetryAfter().toMillis());
        }

        // 상세한 토큰 정보 추가
        Map<String, Object> rateLimitInfo = new HashMap<>();
        
        // 남은 토큰 수 정보
        Map<String, Object> remainingTokens = new HashMap<>();
        if (result.getSecondRemainingTokens() >= 0) {
            remainingTokens.put("second", result.getSecondRemainingTokens());
        }
        if (result.getMinuteRemainingTokens() >= 0) {
            remainingTokens.put("minute", result.getMinuteRemainingTokens());
        }
        if (result.getHourRemainingTokens() >= 0) {
            remainingTokens.put("hour", result.getHourRemainingTokens());
        }
        rateLimitInfo.put("remainingTokens", remainingTokens);
        
        // 제한 토큰 수 정보
        Map<String, Object> limits = new HashMap<>();
        if (result.getAppliedLimit() != null) {
            RateLimitingConfig.ApiLimit limit = result.getAppliedLimit();
            limits.put("second", limit.getRequestsPerSecond());
            limits.put("minute", limit.getRequestsPerMinute());
            limits.put("hour", limit.getRequestsPerHour());
        } else {
            // 기본 IP 제한 정보
            limits.put("second", 10);
            limits.put("minute", 100);
            limits.put("hour", 1000);
        }
        rateLimitInfo.put("limits", limits);
        
        // 제한 타입별 추가 정보
        rateLimitInfo.put("endpoint", identifier);
        rateLimitInfo.put("appliedRuleType", result.getLimitType() != null ? result.getLimitType() : limitType);
        
        errorResponse.put("rateLimitInfo", rateLimitInfo);

        // JSON 응답 생성 및 전송
        String jsonResponse = objectMapper.writeValueAsString(errorResponse);
        response.getWriter().write(jsonResponse);
    }

    /**
     * 응답 헤더에 Rate Limit 정보 추가
     * 클라이언트가 현재 Rate Limit 상태를 파악할 수 있도록 상세한 헤더 정보 제공
     * @param result Rate Limiting 검사 결과
     * @param isAuthenticated 인증된 사용자 여부
     * @param requestUri 요청 URI
     */
    private void addRateLimitHeaders(HttpServletResponse response, RateLimitResult result, 
                                   boolean isAuthenticated, String requestUri) {
        
        // 기본 토큰 정보
        if (result.getRemainingTokens() >= 0) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(result.getRemainingTokens()));
        }

        // 각 시간 단위별 남은 토큰 수
        if (result.getSecondRemainingTokens() >= 0) {
            response.setHeader("X-RateLimit-Second-Remaining", String.valueOf(result.getSecondRemainingTokens()));
        }
        if (result.getMinuteRemainingTokens() >= 0) {
            response.setHeader("X-RateLimit-Minute-Remaining", String.valueOf(result.getMinuteRemainingTokens()));
        }
        if (result.getHourRemainingTokens() >= 0) {
            response.setHeader("X-RateLimit-Hour-Remaining", String.valueOf(result.getHourRemainingTokens()));
        }

        // 각 시간 단위별 제한 수
        if (result.getAppliedLimit() != null) {
            RateLimitingConfig.ApiLimit limit = result.getAppliedLimit();
            response.setHeader("X-RateLimit-Second-Limit", String.valueOf(limit.getRequestsPerSecond()));
            response.setHeader("X-RateLimit-Minute-Limit", String.valueOf(limit.getRequestsPerMinute()));
            response.setHeader("X-RateLimit-Hour-Limit", String.valueOf(limit.getRequestsPerHour()));
        } else {
            // 기본 IP 제한 정보 (API별 제한이 없는 경우)
            response.setHeader("X-RateLimit-Second-Limit", "10");
            response.setHeader("X-RateLimit-Minute-Limit", "100");
            response.setHeader("X-RateLimit-Hour-Limit", "1000");
        }

        // 추가 메타데이터
        response.setHeader("X-RateLimit-Type", result.getLimitType() != null ? result.getLimitType() : (isAuthenticated ? "USER" : "IP"));
        response.setHeader("X-RateLimit-Endpoint", requestUri);
        
        // 다음 리셋 시간 (현재 시간 기준)
        long currentTime = System.currentTimeMillis();
        response.setHeader("X-RateLimit-Reset-Second", String.valueOf(currentTime + 1000));
        response.setHeader("X-RateLimit-Reset-Minute", String.valueOf(currentTime + 60000));
        response.setHeader("X-RateLimit-Reset-Hour", String.valueOf(currentTime + 3600000));
    }
}
