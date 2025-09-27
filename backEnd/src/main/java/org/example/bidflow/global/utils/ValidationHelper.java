package org.example.bidflow.global.utils;

import org.example.bidflow.global.constants.BusinessConstants;
import org.example.bidflow.global.constants.ErrorCode;
import org.example.bidflow.global.exception.ServiceException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

/**
 * 공통 검증 로직을 제공하는 유틸리티 클래스
 * 
 * 이 클래스는 애플리케이션 전체에서 사용되는 검증 로직을 중앙화하여 관리합니다.
 * 비즈니스 규칙 검증과 데이터 유효성 검사를 통합하여 일관된 에러 처리와 메시지를 제공합니다.
 * 
 * 주요 기능:
 * - null 및 빈 값 검증
 * - 숫자 범위 검증
 * - 문자열 길이 및 형식 검증
 * - 날짜 및 시간 검증
 * - 비즈니스 규칙 검증
 * 
 * 사용 예시:
 * // null 검증
 * ValidationHelper.validateNotNull(user, "사용자");
 * 
 * // 양수 검증
 * ValidationHelper.validatePositive(amount, "금액");
 * 
 * // 이메일 형식 검증
 * ValidationHelper.validateEmail(email, "이메일");
 * 
 * @author AuctionService Team
 * @version 1.0
 * @since 1.0
 * @see ServiceException
 * @see BusinessConstants
 * @see ErrorCode
 */
@Component
public class ValidationHelper {

    /**
     * 객체가 null이 아닌지 검증합니다.
     * @param obj 검증할 객체
     * @param fieldName 필드명 (에러 메시지용)
     * @throws ServiceException 객체가 null인 경우
     */
    public static void validateNotNull(Object obj, String fieldName) {
        if (obj == null) {
            throw new ServiceException(ErrorCode.VALIDATION_ERROR, fieldName + "은(는) 필수입니다.");
        }
    }

    /**
     * 문자열이 null이거나 비어있지 않은지 검증합니다.
     * @param str 검증할 문자열
     * @param fieldName 필드명 (에러 메시지용)
     * @throws ServiceException 문자열이 null이거나 비어있는 경우
     */
    public static void validateNotBlank(String str, String fieldName) {
        if (str == null || str.trim().isEmpty()) {
            throw new ServiceException(ErrorCode.VALIDATION_ERROR, fieldName + "은(는) 필수입니다.");
        }
    }

    /**
     * 숫자가 양수인지 검증합니다.
     * @param value 검증할 숫자
     * @param fieldName 필드명 (에러 메시지용)
     * @throws ServiceException 숫자가 null이거나 0 이하인 경우
     */
    public static void validatePositive(Long value, String fieldName) {
        if (value == null || value <= BusinessConstants.ZERO) {
            throw new ServiceException(ErrorCode.VALIDATION_ERROR, fieldName + "은(는) 양수여야 합니다.");
        }
    }

    /**
     * 숫자가 양수인지 검증합니다.
     * @param value 검증할 숫자
     * @param fieldName 필드명 (에러 메시지용)
     * @throws ServiceException 숫자가 null이거나 0 이하인 경우
     */
    public static void validatePositive(Integer value, String fieldName) {
        if (value == null || value <= 0) {
            throw new ServiceException(ErrorCode.VALIDATION_ERROR, fieldName + "은(는) 양수여야 합니다.");
        }
    }

    /**
     * 숫자가 양수인지 검증합니다.
     * @param value 검증할 숫자
     * @param fieldName 필드명 (에러 메시지용)
     * @throws ServiceException 숫자가 null이거나 0 이하인 경우
     */
    public static void validatePositive(Double value, String fieldName) {
        if (value == null || value <= 0.0) {
            throw new ServiceException(ErrorCode.VALIDATION_ERROR, fieldName + "은(는) 양수여야 합니다.");
        }
    }

    /**
     * 숫자가 0 이상인지 검증합니다.
     * @param value 검증할 숫자
     * @param fieldName 필드명 (에러 메시지용)
     * @throws ServiceException 숫자가 null이거나 음수인 경우
     */
    public static void validateNonNegative(Long value, String fieldName) {
        if (value == null || value < BusinessConstants.ZERO) {
            throw new ServiceException(ErrorCode.VALIDATION_ERROR, fieldName + "은(는) 0 이상이어야 합니다.");
        }
    }

    /**
     * 컬렉션이 null이거나 비어있지 않은지 검증합니다.
     * @param collection 검증할 컬렉션
     * @param fieldName 필드명 (에러 메시지용)
     * @throws ServiceException 컬렉션이 null이거나 비어있는 경우
     */
    public static void validateNotEmpty(Collection<?> collection, String fieldName) {
        if (collection == null || collection.isEmpty()) {
            throw new ServiceException(ErrorCode.VALIDATION_ERROR, fieldName + "은(는) 비어있을 수 없습니다.");
        }
    }

    /**
     * 문자열 길이가 지정된 범위 내에 있는지 검증합니다.
     * @param str 검증할 문자열
     * @param minLength 최소 길이
     * @param maxLength 최대 길이
     * @param fieldName 필드명 (에러 메시지용)
     * @throws ServiceException 문자열이 null이거나 길이가 범위를 벗어나는 경우
     */
    public static void validateLength(String str, int minLength, int maxLength, String fieldName) {
        if (str == null) {
            throw new ServiceException(ErrorCode.VALIDATION_ERROR, fieldName + "은(는) 필수입니다.");
        }
        if (str.length() < minLength || str.length() > maxLength) {
            throw new ServiceException(ErrorCode.VALIDATION_ERROR, fieldName + "의 길이는 " + minLength + "자 이상 " + maxLength + "자 이하여야 합니다.");
        }
    }

    /**
     * 이메일 형식이 올바른지 검증합니다.
     * @param email 검증할 이메일
     * @param fieldName 필드명 (에러 메시지용)
     * @throws ServiceException 이메일 형식이 올바르지 않은 경우
     */
    public static void validateEmail(String email, String fieldName) {
        validateNotBlank(email, fieldName);
        if (!email.matches(BusinessConstants.EMAIL_REGEX)) {
            throw new ServiceException(ErrorCode.INVALID_FORMAT, "올바른 " + fieldName + " 형식이 아닙니다.");
        }
    }

    /**
     * 날짜가 미래인지 검증합니다.
     * @param dateTime 검증할 날짜
     * @param fieldName 필드명 (에러 메시지용)
     * @throws ServiceException 날짜가 현재 시간보다 이전인 경우
     */
    public static void validateFuture(LocalDateTime dateTime, String fieldName) {
        validateNotNull(dateTime, fieldName);
        if (dateTime.isBefore(LocalDateTime.now())) {
            throw new ServiceException(ErrorCode.VALIDATION_ERROR, fieldName + "은(는) 현재 시간보다 이후여야 합니다.");
        }
    }

    /**
     * 날짜가 과거인지 검증합니다.
     * @param dateTime 검증할 날짜
     * @param fieldName 필드명 (에러 메시지용)
     * @throws ServiceException 날짜가 현재 시간보다 이후인 경우
     */
    public static void validatePast(LocalDateTime dateTime, String fieldName) {
        validateNotNull(dateTime, fieldName);
        if (dateTime.isAfter(LocalDateTime.now())) {
            throw new ServiceException(ErrorCode.VALIDATION_ERROR, fieldName + "은(는) 현재 시간보다 이전이어야 합니다.");
        }
    }

    /**
     * 날짜 범위가 올바른지 검증합니다.
     * @param startDate 시작 날짜
     * @param endDate 종료 날짜
     * @param startFieldName 시작 날짜 필드명
     * @param endFieldName 종료 날짜 필드명
     * @throws ServiceException 시작 날짜가 종료 날짜보다 이후인 경우
     */
    public static void validateDateRange(LocalDateTime startDate, LocalDateTime endDate, 
                                       String startFieldName, String endFieldName) {
        validateNotNull(startDate, startFieldName);
        validateNotNull(endDate, endFieldName);
        if (startDate.isAfter(endDate)) {
            throw new ServiceException(ErrorCode.VALIDATION_ERROR, startFieldName + "은(는) " + endFieldName + "보다 이전이어야 합니다.");
        }
    }

    /**
     * Optional에서 값을 추출하거나 예외를 발생시킵니다.
     * @param optional 검증할 Optional
     * @param errorCode 에러 코드
     * @param errorMessage 에러 메시지
     * @return 추출된 값
     * @throws ServiceException Optional이 비어있는 경우
     */
    public static <T> T getOrThrow(Optional<T> optional, String errorCode, String errorMessage) {
        return optional.orElseThrow(() -> new ServiceException(errorCode, errorMessage));
    }

    /**
     * 조건이 false인 경우 예외를 발생시킵니다.
     * @param condition 검증할 조건
     * @param errorCode 에러 코드
     * @param errorMessage 에러 메시지
     * @throws ServiceException 조건이 false인 경우
     */
    public static void validateCondition(boolean condition, String errorCode, String errorMessage) {
        if (!condition) {
            throw new ServiceException(errorCode, errorMessage);
        }
    }

    /**
     * 경매 상태가 진행 중인지 검증합니다.
     * @param status 경매 상태
     * @throws ServiceException 경매가 진행 중이 아닌 경우
     */
    public static void validateAuctionOngoing(String status) {
        if (!BusinessConstants.AUCTION_STATUS_ONGOING.equals(status)) {
            throw new ServiceException(ErrorCode.AUCTION_NOT_ONGOING, "진행 중인 경매가 아닙니다.");
        }
    }

    /**
     * 사용자 권한을 검증합니다.
     * @param userRole 사용자 역할
     * @param requiredRole 필요한 역할
     * @throws ServiceException 권한이 없는 경우
     */
    public static void validateRole(String userRole, String requiredRole) {
        if (!requiredRole.equals(userRole)) {
            throw new ServiceException(ErrorCode.ACCESS_DENIED, "해당 작업을 수행할 권한이 없습니다.");
        }
    }

    /**
     * 경매 시작가가 유효한 범위인지 검증합니다.
     * 
     * @param startPrice 검증할 시작가
     * @param fieldName 필드명 (에러 메시지용)
     * @throws ServiceException 시작가가 유효하지 않은 경우
     */
    public static void validateAuctionStartPrice(Integer startPrice, String fieldName) {
        validateNotNull(startPrice, fieldName);
        validatePositive(startPrice, fieldName);
        
        if (startPrice < BusinessConstants.AUCTION_MIN_BID_AMOUNT) {
            throw new ServiceException(ErrorCode.VALIDATION_ERROR, 
                    fieldName + "은(는) " + BusinessConstants.AUCTION_MIN_BID_AMOUNT + "원 이상이어야 합니다.");
        }
        
        if (startPrice > BusinessConstants.AUCTION_MAX_BID_AMOUNT) {
            throw new ServiceException(ErrorCode.VALIDATION_ERROR, 
                    fieldName + "은(는) " + BusinessConstants.AUCTION_MAX_BID_AMOUNT + "원 이하여야 합니다.");
        }
    }

    /**
     * 입찰 금액이 유효한 범위인지 검증합니다.
     * 
     * @param bidAmount 검증할 입찰 금액
     * @param fieldName 필드명 (에러 메시지용)
     * @throws ServiceException 입찰 금액이 유효하지 않은 경우
     */
    public static void validateBidAmount(Integer bidAmount, String fieldName) {
        validateNotNull(bidAmount, fieldName);
        validatePositive(bidAmount, fieldName);
        
        if (bidAmount < BusinessConstants.AUCTION_MIN_BID_AMOUNT) {
            throw new ServiceException(ErrorCode.VALIDATION_ERROR, 
                    fieldName + "은(는) " + BusinessConstants.AUCTION_MIN_BID_AMOUNT + "원 이상이어야 합니다.");
        }
        
        if (bidAmount > BusinessConstants.AUCTION_MAX_BID_AMOUNT) {
            throw new ServiceException(ErrorCode.VALIDATION_ERROR, 
                    fieldName + "은(는) " + BusinessConstants.AUCTION_MAX_BID_AMOUNT + "원 이하여야 합니다.");
        }
    }

    /**
     * 경매 시간이 유효한지 검증합니다.
     * 
     * @param startTime 시작 시간
     * @param endTime 종료 시간
     * @throws ServiceException 경매 시간이 유효하지 않은 경우
     */
    public static void validateAuctionTime(LocalDateTime startTime, LocalDateTime endTime) {
        validateNotNull(startTime, "시작 시간");
        validateNotNull(endTime, "종료 시간");
        
        LocalDateTime now = LocalDateTime.now();
        
        if (startTime.isBefore(now.plusDays(BusinessConstants.MIN_AUCTION_START_DAYS))) {
            throw new ServiceException(ErrorCode.VALIDATION_ERROR, 
                    "경매 시작 시간은 최소 " + BusinessConstants.MIN_AUCTION_START_DAYS + "일 후여야 합니다.");
        }
        
        if (endTime.isBefore(startTime)) {
            throw new ServiceException(ErrorCode.VALIDATION_ERROR, 
                    "종료 시간은 시작 시간보다 늦어야 합니다.");
        }
    }
}
