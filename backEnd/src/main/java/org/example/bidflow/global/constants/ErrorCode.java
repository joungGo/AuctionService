package org.example.bidflow.global.constants;

/**
 * 애플리케이션 전체에서 사용되는 에러 코드 상수
 * 일관된 에러 코드 관리를 위해 중앙화된 상수 클래스
 */
public final class ErrorCode {

    // HTTP 상태 코드
    public static final String HTTP_OK = "200";
    public static final String HTTP_CREATED = "201";
    public static final String HTTP_BAD_REQUEST = "400";
    public static final String HTTP_UNAUTHORIZED = "401";
    public static final String HTTP_FORBIDDEN = "403";
    public static final String HTTP_NOT_FOUND = "404";
    public static final String HTTP_INTERNAL_SERVER_ERROR = "500";

    // 비즈니스 에러 코드
    public static final String AUCTION_NOT_FOUND = "400-1";
    public static final String AUCTION_NOT_ONGOING = "400-2";
    public static final String USER_NOT_FOUND = "404-1";
    public static final String CATEGORY_NOT_FOUND = "404-2";
    public static final String BID_NOT_FOUND = "404-3";
    public static final String PRODUCT_NOT_FOUND = "404-4";
    public static final String WINNER_NOT_FOUND = "404-5";

    // 인증/인가 에러 코드
    public static final String INVALID_TOKEN = "401-1";
    public static final String TOKEN_EXPIRED = "401-2";
    public static final String ACCESS_DENIED = "403-1";
    public static final String INSUFFICIENT_PERMISSION = "403-2";

    // 검증 에러 코드
    public static final String VALIDATION_ERROR = "400";
    public static final String INVALID_INPUT = "400-10";
    public static final String MISSING_REQUIRED_FIELD = "400-11";
    public static final String INVALID_FORMAT = "400-12";
    public static final String OUT_OF_RANGE = "400-13";

    // 시스템 에러 코드
    public static final String DATABASE_ERROR = "500-1";
    public static final String EXTERNAL_SERVICE_ERROR = "500-2";
    public static final String UNKNOWN_ERROR = "500-99";

    // 생성자 숨김 (유틸리티 클래스)
    private ErrorCode() {
        throw new UnsupportedOperationException("유틸리티 클래스는 인스턴스화할 수 없습니다.");
    }
}
