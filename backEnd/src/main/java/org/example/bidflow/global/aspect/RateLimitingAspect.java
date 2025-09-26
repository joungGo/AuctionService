package org.example.bidflow.global.aspect;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.example.bidflow.global.annotation.RateLimit;
import org.example.bidflow.global.service.RateLimitingService;
import org.example.bidflow.global.service.RateLimitingService.RateLimitResult;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * @RateLimit 어노테이션 처리를 위한 AOP
 * 메서드 레벨에서 세밀한 Rate Limiting 제어를 제공합니다.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitingAspect {

    private final RateLimitingService rateLimitingService;
    
    /** Spring Expression Language 파서 - 커스텀 키 표현식 평가용 */
    private final ExpressionParser expressionParser = new SpelExpressionParser();

    /**
     * @RateLimit 어노테이션이 적용된 메서드 실행 전 Rate Limiting 검사
     * 
     * @param joinPoint AOP 조인 포인트 - 실행되는 메서드 정보
     * @param rateLimit Rate Limiting 설정 어노테이션
     */
    @Around("@annotation(rateLimit)")
    public Object checkRateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        
        try {
            // Rate Limiting 키 생성 (어노테이션 설정에 따라 다양한 전략 사용)
            String key = generateRateLimitKey(rateLimit, joinPoint);
            
            log.debug("[Rate Limiting AOP] 메서드 레벨 제한 검사 - 메서드: {}, 키: {}", 
                    joinPoint.getSignature().toShortString(), key);

            // 어노테이션 설정을 기반으로 한 커스텀 Rate Limiting 검사
            RateLimitResult result = checkCustomRateLimit(key, rateLimit);

            if (!result.isAllowed()) {
                log.warn("[Rate Limiting AOP] 메서드 레벨 제한 초과 - 메서드: {}, 키: {}, 제한: {}회/{}{}",
                        joinPoint.getSignature().toShortString(), key, 
                        rateLimit.requests(), rateLimit.window(), rateLimit.unit());

                // 제한 초과 시 상세 정보와 함께 예외 발생
                Map<String, Object> errorDetails = new HashMap<>();
                errorDetails.put("method", joinPoint.getSignature().toShortString()); // 실행된 메서드명
                errorDetails.put("limit", rateLimit.requests()); // 설정된 요청 제한 수
                errorDetails.put("window", rateLimit.window() + " " + rateLimit.unit().name()); // 시간 윈도우
                errorDetails.put("retryAfterSeconds", result.getRetryAfter() != null ? result.getRetryAfter().getSeconds() : 0); // 재시도 가능 시간

                throw new RateLimitExceededException(rateLimit.message(), errorDetails);
            }

            log.debug("[Rate Limiting AOP] 메서드 레벨 제한 통과 - 메서드: {}, 남은 토큰: {}", 
                    joinPoint.getSignature().toShortString(), result.getRemainingTokens());

        } catch (RateLimitExceededException e) {
            throw e; // Rate Limit 예외는 그대로 전파
        } catch (Exception e) {
            log.error("[Rate Limiting AOP] 처리 중 오류 발생 - 메서드: {}, 오류: {}", 
                    joinPoint.getSignature().toShortString(), e.getMessage(), e);
            // 오류 발생 시 메서드 실행 허용 (서비스 가용성 우선)
        }

        return joinPoint.proceed();
    }

    /**
     * Rate Limiting 키 생성
     * 어노테이션의 keyType 설정에 따라 다른 전략으로 고유 키를 생성
     */
    private String generateRateLimitKey(RateLimit rateLimit, ProceedingJoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().toShortString();
        
        switch (rateLimit.keyType()) {
            case IP_ONLY:
                // IP 주소만을 기준으로 키 생성 (익명 사용자 대상)
                return "method:" + methodName + ":ip:" + getClientIpAddress();
                
            case USER_ONLY:
                // 인증된 사용자 UUID만을 기준으로 키 생성
                String userUUID = getCurrentUserUUID();
                if (userUUID == null) {
                    throw new IllegalStateException("USER_ONLY Rate Limiting requires authenticated user");
                }
                return "method:" + methodName + ":user:" + userUUID;
                
            case IP_AND_USER:
                // 로그인 시 사용자 UUID, 미로그인 시 IP 주소 사용 (기본 전략)
                String user = getCurrentUserUUID();
                String ip = getClientIpAddress();
                return "method:" + methodName + ":composite:" + (user != null ? user : ip);
                
            case CUSTOM:
                // SpEL 표현식을 사용한 커스텀 키 생성
                if (rateLimit.keyExpression().isEmpty()) {
                    throw new IllegalArgumentException("CUSTOM key type requires keyExpression");
                }
                return "method:" + methodName + ":custom:" + evaluateKeyExpression(rateLimit.keyExpression(), joinPoint);
                
            default:
                // 기본값: IP 주소 기반
                return "method:" + methodName + ":default:" + getClientIpAddress();
        }
    }

    /**
     * 커스텀 키 표현식 평가
     * Spring Expression Language(SpEL)를 사용하여 동적 키 생성
     */
    private String evaluateKeyExpression(String keyExpression, ProceedingJoinPoint joinPoint) {
        try {
            StandardEvaluationContext context = new StandardEvaluationContext();
            
            // 메서드 파라미터를 SpEL 컨텍스트에 추가 (arg0, arg1, ... 형태)
            Object[] args = joinPoint.getArgs();
            String[] paramNames = getParameterNames(joinPoint);
            for (int i = 0; i < args.length && i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
            
            // HTTP 요청 관련 정보를 SpEL 변수로 추가
            HttpServletRequest request = getCurrentRequest();
            if (request != null) {
                context.setVariable("request", request); // HTTP 요청 객체
                context.setVariable("ip", getClientIpAddress()); // 클라이언트 IP
                context.setVariable("userAgent", request.getHeader("User-Agent")); // User-Agent 헤더
            }
            
            // Spring Security 인증 정보를 SpEL 변수로 추가
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null) {
                context.setVariable("user", auth.getName()); // 사용자명
                context.setVariable("authorities", auth.getAuthorities()); // 권한 목록
            }

            Expression expression = expressionParser.parseExpression(keyExpression);
            Object result = expression.getValue(context);
            
            return result != null ? result.toString() : "null";
            
        } catch (Exception e) {
            log.error("[Rate Limiting AOP] 키 표현식 평가 실패 - 표현식: {}, 오류: {}", keyExpression, e.getMessage(), e);
            return "evaluation_failed";
        }
    }

    /**
     * 커스텀 Rate Limiting 검사
     * 어노테이션에 정의된 제한 설정을 기반으로 요청 허용 여부 판단
     */
    private RateLimitResult checkCustomRateLimit(String key, RateLimit rateLimit) {
        Duration window = Duration.of(rateLimit.window(), rateLimit.unit());
        
        // RateLimitingService를 확장하여 커스텀 제한 검사
        // 현재는 API 제한 검사 메서드를 재사용 (향후 전용 메서드로 분리 가능)
        return rateLimitingService.checkApiLimit("custom:" + key, key);
    }

    /**
     * 클라이언트 IP 주소 추출
     * 프록시나 로드밸런서 환경에서도 실제 클라이언트 IP를 정확히 추출
     */
    private String getClientIpAddress() {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            return "unknown";
        }

        // X-Forwarded-For 헤더 확인 (ALB, 프록시 환경에서 실제 클라이언트 IP)
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim(); // 첫 번째 IP가 실제 클라이언트 IP
        }

        // 직접 연결된 경우 기본 remote address 사용
        return request.getRemoteAddr();
    }

    /**
     * 현재 인증된 사용자 UUID 추출
     * Spring Security 컨텍스트에서 인증된 사용자 정보를 가져옴
     */
    private String getCurrentUserUUID() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                String username = authentication.getName();
                if (!"anonymousUser".equals(username)) {
                    return username; // JWT에서 userUUID 추출
                }
            }
        } catch (Exception e) {
            log.debug("[Rate Limiting AOP] 사용자 정보 추출 실패: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 현재 HTTP 요청 추출
     */
    private HttpServletRequest getCurrentRequest() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
            return attributes.getRequest();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 메서드 파라미터 이름 추출 (간단한 구현)
     * SpEL 표현식에서 사용할 파라미터명을 생성
     */
    private String[] getParameterNames(ProceedingJoinPoint joinPoint) {
        int paramCount = joinPoint.getArgs().length;
        String[] names = new String[paramCount];
        for (int i = 0; i < paramCount; i++) {
            names[i] = "arg" + i;
        }
        return names;
    }

    /**
     * Rate Limit 초과 예외
     * 메서드 레벨 Rate Limiting에서 제한을 초과했을 때 발생하는 예외
     */
    public static class RateLimitExceededException extends ResponseStatusException {
        /** 추가적인 에러 상세 정보 (메서드명, 제한 설정 등) */
        private final Map<String, Object> errorDetails;

        /**
         * Rate Limit 초과 예외 생성자
         */
        public RateLimitExceededException(String message, Map<String, Object> errorDetails) {
            super(HttpStatus.TOO_MANY_REQUESTS, message);
            this.errorDetails = errorDetails;
        }

        /**
         * 에러 상세 정보 반환
         */
        public Map<String, Object> getErrorDetails() {
            return errorDetails;
        }
    }
}
