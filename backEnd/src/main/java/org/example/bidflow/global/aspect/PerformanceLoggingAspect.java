package org.example.bidflow.global.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.example.bidflow.global.utils.LoggingUtil;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 성능 모니터링 및 디버깅용 AOP 어스펙트
 * 서비스 메서드의 실행 시간과 파라미터/결과를 로깅합니다.
 */
@Slf4j
@Aspect
@Component
public class PerformanceLoggingAspect {

    // 성능 임계치 설정 (밀리초)
    private static final long PERFORMANCE_THRESHOLD_MS = 1000; // 1초
    private static final long SLOW_QUERY_THRESHOLD_MS = 2000;  // 2초

    /**
     * Service 클래스의 모든 public 메서드에 대해 성능 로깅 적용
     */
    @Around("execution(* org.example.bidflow.domain..service.*.*(..))")
    public Object logServicePerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        return logMethodExecution(joinPoint, "Service");
    }

    /**
     * Controller 클래스의 모든 public 메서드에 대해 성능 로깅 적용
     */
    @Around("execution(* org.example.bidflow.domain..controller.*.*(..))")
    public Object logControllerPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        return logMethodExecution(joinPoint, "Controller");
    }

    /**
     * Repository 클래스의 모든 public 메서드에 대해 성능 로깅 적용
     */
    @Around("execution(* org.example.bidflow.domain..repository.*.*(..))")
    public Object logRepositoryPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        return logMethodExecution(joinPoint, "Repository");
    }

    /**
     * 스케줄러 메서드에 대한 성능 로깅
     */
    @Around("execution(* org.example.bidflow.global.app.*Scheduler*.*(..))")
    public Object logSchedulerPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        return logMethodExecution(joinPoint, "Scheduler");
    }

    /**
     * 메서드 실행 로깅 공통 로직
     */
    private Object logMethodExecution(ProceedingJoinPoint joinPoint, String componentType) throws Throwable {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();
        
        // 시작 시간 기록
        long startTime = System.currentTimeMillis();
        
        // 메서드 시작 로그 (디버그 레벨에서만)
        if (log.isDebugEnabled()) {
            LoggingUtil.logMethodStart(className, methodName, args);
        }

        Object result = null;
        Throwable exception = null;
        
        try {
            // 실제 메서드 실행
            result = joinPoint.proceed();
            return result;
            
        } catch (Throwable e) {
            exception = e;
            
            // 에러 컨텍스트 정보 수집
            Map<String, Object> errorContext = new HashMap<>();
            errorContext.put("className", className);
            errorContext.put("methodName", methodName);
            errorContext.put("componentType", componentType);
            errorContext.put("arguments", Arrays.toString(args));
            
            LoggingUtil.logError("METHOD_EXECUTION_ERROR", 
                    "메서드 실행 중 오류 발생", e, errorContext);
            
            throw e;
            
        } finally {
            // 실행 시간 계산
            long executionTime = System.currentTimeMillis() - startTime;
            
            // 성능 로깅
            logPerformanceMetrics(componentType, className, methodName, executionTime, exception != null);
            
            // 메서드 종료 로그 (디버그 레벨에서만)
            if (log.isDebugEnabled()) {
                if (exception == null) {
                    LoggingUtil.logMethodEnd(className, methodName, executionTime, result);
                } else {
                    LoggingUtil.logMethodEnd(className, methodName, executionTime);
                }
            }
        }
    }

    /**
     * 성능 메트릭 로깅
     */
    private void logPerformanceMetrics(String componentType, String className, String methodName, 
                                     long executionTime, boolean hasError) {
        
        String operation = String.format("%s.%s.%s", componentType, className, methodName);
        
        // 성능 임계치 체크
        if (executionTime > SLOW_QUERY_THRESHOLD_MS) {
            LoggingUtil.logPerformanceWarning(operation, executionTime, SLOW_QUERY_THRESHOLD_MS);
        } else if (executionTime > PERFORMANCE_THRESHOLD_MS) {
            log.warn("[성능 주의] {} - 실행시간: {}ms (주의 임계치: {}ms)", 
                    operation, executionTime, PERFORMANCE_THRESHOLD_MS);
        }
        
        // 정상 실행 시 INFO 레벨 로깅 (중요한 비즈니스 로직만)
        if (!hasError && isImportantBusinessMethod(methodName)) {
            log.info("[성능 메트릭] {} - 실행시간: {}ms", operation, executionTime);
        }
        
        // 디버그 레벨에서는 모든 메서드 로깅
        log.debug("[성능 상세] {} - 실행시간: {}ms, 오류여부: {}", operation, executionTime, hasError);
    }

    /**
     * 중요한 비즈니스 메서드인지 판단
     */
    private boolean isImportantBusinessMethod(String methodName) {
        return methodName.contains("create") || 
               methodName.contains("update") || 
               methodName.contains("delete") || 
               methodName.contains("login") || 
               methodName.contains("signup") || 
               methodName.contains("bid") ||
               methodName.contains("auction") ||
               methodName.contains("process");
    }
} 