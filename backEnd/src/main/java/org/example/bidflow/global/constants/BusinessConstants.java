package org.example.bidflow.global.constants;

import java.time.Duration;

/**
 * 비즈니스 로직에서 사용되는 상수들을 중앙화하여 관리하는 클래스
 * 
 * 이 클래스는 애플리케이션 전체에서 사용되는 비즈니스 관련 상수들을 정의합니다.
 * 매직 넘버와 하드코딩된 값들을 상수로 정의하여 코드의 가독성과 유지보수성을 향상시킵니다.
 * 
 * 주요 상수 카테고리:
 * - 경매 관련 상수 (시작일수, 버퍼 시간 등)
 * - 검증 관련 상수 (문자열 길이, 이메일 형식 등)
 * - 숫자 관련 상수 (기본값, 페이지 크기 등)
 * - Redis 관련 상수 (만료 시간, 캐시 설정 등)
 * - 성능 관련 상수 (임계치, 타임아웃 등)
 * - 상태 및 역할 상수 (경매 상태, 사용자 역할 등)
 * 
 * 사용 예시:
 * // 경매 시작 최소 일수 확인
 * if (startDate.isBefore(now.plusDays(BusinessConstants.MIN_AUCTION_START_DAYS))) {
 *     throw new ServiceException("경매 시작일이 너무 빠릅니다.");
 * }
 * 
 * // Redis 만료 시간 설정
 * redisTemplate.expire(key, BusinessConstants.REDIS_CACHE_EXPIRE_TIME);
 * 
 * @author AuctionService Team
 * @version 1.0
 * @since 1.0
 * @see ErrorCode
 */
public final class BusinessConstants {

    // 경매 관련 상수
    public static final int MIN_AUCTION_START_DAYS = 2; // 최소 경매 시작 일수
    public static final int AUCTION_END_BUFFER_MINUTES = 2; // 경매 종료 후 버퍼 시간 (분)
    public static final int AUCTION_MIN_BID_AMOUNT = 1000; // 최소 입찰 금액
    public static final int AUCTION_MAX_BID_AMOUNT = 100000000; // 최대 입찰 금액
    
    // 검증 관련 상수
    public static final int MIN_STRING_LENGTH = 1; // 최소 문자열 길이
    public static final int MAX_STRING_LENGTH = 255; // 최대 문자열 길이
    public static final int MAX_EMAIL_LENGTH = 100; // 최대 이메일 길이
    public static final int MAX_USERNAME_LENGTH = 50; // 최대 사용자명 길이
    
    // 숫자 관련 상수
    public static final long ZERO = 0L;
    public static final long ONE = 1L;
    public static final int DEFAULT_PAGE_SIZE = 20; // 기본 페이지 크기
    public static final int MAX_PAGE_SIZE = 100; // 최대 페이지 크기
    
    // Redis 관련 상수
    public static final Duration REDIS_DEFAULT_EXPIRE_TIME = Duration.ofSeconds(5); // 기본 만료 시간
    public static final Duration REDIS_SESSION_EXPIRE_TIME = Duration.ofHours(24); // 세션 만료 시간
    public static final Duration REDIS_CACHE_EXPIRE_TIME = Duration.ofMinutes(30); // 캐시 만료 시간
    
    // 성능 관련 상수
    public static final long PERFORMANCE_WARNING_THRESHOLD_MS = 1000; // 성능 경고 임계치 (밀리초)
    public static final long PERFORMANCE_ERROR_THRESHOLD_MS = 5000; // 성능 에러 임계치 (밀리초)
    
    // 데이터베이스 관련 상수
    public static final int MAX_BATCH_SIZE = 1000; // 최대 배치 크기
    public static final int DEFAULT_QUERY_TIMEOUT = 30; // 기본 쿼리 타임아웃 (초)
    
    // 이메일 정규식
    public static final String EMAIL_REGEX = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
    
    // 경매 상태
    public static final String AUCTION_STATUS_ONGOING = "ONGOING";
    public static final String AUCTION_STATUS_SCHEDULED = "SCHEDULED";
    public static final String AUCTION_STATUS_FINISHED = "FINISHED";
    public static final String AUCTION_STATUS_CANCELLED = "CANCELLED";
    
    // 사용자 역할
    public static final String USER_ROLE_ADMIN = "ADMIN";
    public static final String USER_ROLE_USER = "USER";
    
    // HTTP 상태 코드
    public static final int HTTP_STATUS_OK = 200;
    public static final int HTTP_STATUS_CREATED = 201;
    public static final int HTTP_STATUS_BAD_REQUEST = 400;
    public static final int HTTP_STATUS_UNAUTHORIZED = 401;
    public static final int HTTP_STATUS_FORBIDDEN = 403;
    public static final int HTTP_STATUS_NOT_FOUND = 404;
    public static final int HTTP_STATUS_INTERNAL_SERVER_ERROR = 500;

    // 생성자 숨김 (유틸리티 클래스)
    private BusinessConstants() {
        throw new UnsupportedOperationException("유틸리티 클래스는 인스턴스화할 수 없습니다.");
    }
}
