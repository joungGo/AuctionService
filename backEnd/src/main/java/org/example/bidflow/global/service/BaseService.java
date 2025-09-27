package org.example.bidflow.global.service;

import lombok.extern.slf4j.Slf4j;
import org.example.bidflow.global.constants.ErrorCode;
import org.example.bidflow.global.utils.LoggingUtil;
import org.example.bidflow.global.utils.ValidationHelper;
import org.example.bidflow.global.exception.ServiceException;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 모든 서비스의 공통 기능을 제공하는 추상 클래스
 * 
 * 이 클래스는 비즈니스 로직을 담당하는 서비스들의 공통 기능을 추상화하여 제공합니다.
 * 엔티티 조회, 검증, 로깅 등의 공통 로직을 통합하여 일관된 서비스 계층 패턴을 보장합니다.
 * 
 * 주요 기능:
 * - JPA Repository를 통한 엔티티 조회 및 예외 처리
 * - 공통 검증 로직 제공 (ValidationHelper 연동)
 * - 메서드 실행 시간 측정 및 로깅
 * - 비즈니스 단계별 로깅
 * - 데이터베이스 작업 로깅
 * - 에러 및 성능 모니터링
 * 
 * 사용 방법:
 * @Service
 * @RequiredArgsConstructor
 * public class UserService extends BaseService {
 *     
 *     private final UserRepository userRepository;
 *     
 *     public User getUserById(Long userId) {
 *         long startTime = startOperation("getUserById", "사용자 조회");
 *         try {
 *             User user = findByIdOrThrow(userRepository, userId, "사용자");
 *             endOperation("getUserById", "사용자 조회", startTime);
 *             return user;
 *         } catch (Exception e) {
 *             endOperation("getUserById", "사용자 조회", startTime);
 *             throw e;
 *         }
 *     }
 * }
 * 
 * @author AuctionService Team
 * @version 1.0
 * @since 1.0
 * @see ValidationHelper
 * @see LoggingUtil
 * @see ErrorCode
 */
@Slf4j
public abstract class BaseService {

    /**
     * Optional에서 엔티티를 조회하거나 예외를 발생시킵니다.
     * @param optional 조회할 Optional
     * @param errorCode 에러 코드
     * @param errorMessage 에러 메시지
     * @return 조회된 엔티티
     * @throws ServiceException Optional이 비어있는 경우
     */
    protected <T> T findEntityOrThrow(Optional<T> optional, String errorCode, String errorMessage) {
        return ValidationHelper.getOrThrow(optional, errorCode, errorMessage);
    }

    /**
     * ID로 엔티티를 조회하거나 예외를 발생시킵니다.
     * @param repository JPA Repository
     * @param id 엔티티 ID
     * @param entityName 엔티티명 (에러 메시지용)
     * @return 조회된 엔티티
     * @throws ServiceException 엔티티가 존재하지 않는 경우
     */
    protected <T, ID> T findByIdOrThrow(JpaRepository<T, ID> repository, ID id, String entityName) {
        Optional<T> optional = repository.findById(id);
        return findEntityOrThrow(optional, ErrorCode.HTTP_NOT_FOUND, entityName + "이(가) 존재하지 않습니다.");
    }

    /**
     * 메서드 실행 시간을 측정하고 로그를 남깁니다.
     * @param methodName 메서드명
     * @param operation 작업 내용
     * @return 실행 시작 시간
     */
    protected long startOperation(String methodName, String operation) {
        long startTime = System.currentTimeMillis();
        LoggingUtil.logMethodStart(this.getClass().getSimpleName(), methodName);
        log.info("[{}] {} 시작", methodName, operation);
        return startTime;
    }

    /**
     * 메서드 실행 완료 로그를 남깁니다.
     * @param methodName 메서드명
     * @param operation 작업 내용
     * @param startTime 시작 시간
     */
    protected void endOperation(String methodName, String operation, long startTime) {
        long executionTime = System.currentTimeMillis() - startTime;
        LoggingUtil.logMethodEnd(this.getClass().getSimpleName(), methodName, executionTime);
        log.info("[{}] {} 완료 - 실행시간: {}ms", methodName, operation, executionTime);
    }

    /**
     * 메서드 실행 완료 로그를 남깁니다. (결과 포함)
     * @param methodName 메서드명
     * @param operation 작업 내용
     * @param startTime 시작 시간
     * @param result 실행 결과
     */
    protected void endOperation(String methodName, String operation, long startTime, Object result) {
        long executionTime = System.currentTimeMillis() - startTime;
        LoggingUtil.logMethodEnd(this.getClass().getSimpleName(), methodName, executionTime, result);
        log.info("[{}] {} 완료 - 실행시간: {}ms, 결과: {}", methodName, operation, executionTime, result);
    }

    /**
     * 비즈니스 로직 단계 로그를 남깁니다.
     * @param stepName 단계명
     * @param description 설명
     * @param context 컨텍스트 정보
     */
    protected void logBusinessStep(String stepName, String description, Object... context) {
        LoggingUtil.logBusinessStep(stepName, description, context);
    }

    /**
     * 데이터베이스 작업 로그를 남깁니다.
     * @param operation 작업명
     * @param entityName 엔티티명
     * @param entityId 엔티티 ID
     * @param details 상세 정보
     */
    protected void logDatabaseOperation(String operation, String entityName, Object entityId, String details) {
        LoggingUtil.logDatabaseOperation(operation, entityName, entityId, details);
    }

    /**
     * 에러 발생 로그를 남깁니다.
     * @param errorType 에러 타입
     * @param message 에러 메시지
     * @param throwable 예외 객체
     * @param context 컨텍스트 정보
     */
    protected void logError(String errorType, String message, Throwable throwable, java.util.Map<String, Object> context) {
        LoggingUtil.logError(errorType, message, throwable, context);
    }

    /**
     * 성능 경고 로그를 남깁니다.
     * @param operation 작업명
     * @param executionTimeMs 실행 시간 (밀리초)
     * @param thresholdMs 임계치 (밀리초)
     */
    protected void logPerformanceWarning(String operation, long executionTimeMs, long thresholdMs) {
        LoggingUtil.logPerformanceWarning(operation, executionTimeMs, thresholdMs);
    }

    /**
     * 사용자 액션 로그를 남깁니다.
     * @param userUUID 사용자 UUID
     * @param action 액션명
     * @param details 상세 정보
     * @param ipAddress IP 주소
     */
    protected void logUserAction(String userUUID, String action, String details, String ipAddress) {
        LoggingUtil.logUserAction(userUUID, action, details, ipAddress);
    }

    /**
     * 조건을 검증하고 실패 시 예외를 발생시킵니다.
     * @param condition 검증할 조건
     * @param errorCode 에러 코드
     * @param errorMessage 에러 메시지
     * @throws ServiceException 조건이 false인 경우
     */
    protected void validateCondition(boolean condition, String errorCode, String errorMessage) {
        ValidationHelper.validateCondition(condition, errorCode, errorMessage);
    }

    /**
     * 객체가 null이 아닌지 검증합니다.
     * @param obj 검증할 객체
     * @param fieldName 필드명
     * @throws ServiceException 객체가 null인 경우
     */
    protected void validateNotNull(Object obj, String fieldName) {
        ValidationHelper.validateNotNull(obj, fieldName);
    }

    /**
     * 문자열이 null이거나 비어있지 않은지 검증합니다.
     * @param str 검증할 문자열
     * @param fieldName 필드명
     * @throws ServiceException 문자열이 null이거나 비어있는 경우
     */
    protected void validateNotBlank(String str, String fieldName) {
        ValidationHelper.validateNotBlank(str, fieldName);
    }

    /**
     * 숫자가 양수인지 검증합니다.
     * @param value 검증할 숫자
     * @param fieldName 필드명
     * @throws ServiceException 숫자가 null이거나 0 이하인 경우
     */
    protected void validatePositive(Long value, String fieldName) {
        ValidationHelper.validatePositive(value, fieldName);
    }

    /**
     * 숫자가 양수인지 검증합니다.
     * @param value 검증할 숫자
     * @param fieldName 필드명
     * @throws ServiceException 숫자가 null이거나 0 이하인 경우
     */
    protected void validatePositive(Integer value, String fieldName) {
        ValidationHelper.validatePositive(value, fieldName);
    }

    /**
     * 숫자가 양수인지 검증합니다.
     * @param value 검증할 숫자
     * @param fieldName 필드명
     * @throws ServiceException 숫자가 null이거나 0 이하인 경우
     */
    protected void validatePositive(Double value, String fieldName) {
        ValidationHelper.validatePositive(value, fieldName);
    }

    /**
     * 이메일 형식을 검증합니다.
     * @param email 검증할 이메일
     * @param fieldName 필드명
     * @throws ServiceException 이메일 형식이 올바르지 않은 경우
     */
    protected void validateEmail(String email, String fieldName) {
        ValidationHelper.validateEmail(email, fieldName);
    }

    /**
     * 현재 서비스 클래스명을 반환합니다.
     * @return 서비스 클래스명
     */
    protected String getServiceName() {
        return this.getClass().getSimpleName();
    }
}
