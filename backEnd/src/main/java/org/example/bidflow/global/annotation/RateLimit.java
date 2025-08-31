package org.example.bidflow.global.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.temporal.ChronoUnit;

/**
 * 메서드별 Rate Limiting을 위한 어노테이션
 * 
 * 사용 예시:
 * @RateLimit(requests = 10, window = 1, unit = ChronoUnit.MINUTES)
 * public ResponseEntity<?> sensitiveOperation() { ... }
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * 허용 요청 수
     * 지정된 시간 윈도우 내에서 허용되는 최대 요청 횟수
     * 기본값: 100회
     */
    int requests() default 100;

    /**
     * 시간 윈도우 크기
     * Rate Limiting이 적용되는 시간 구간의 크기
     * unit과 함께 사용되어 실제 시간을 결정 (예: window=5, unit=MINUTES → 5분)
     * 기본값: 1
     */
    long window() default 1;

    /**
     * 시간 단위
     * window와 함께 사용되어 Rate Limiting 시간 윈도우를 정의
     * 사용 가능한 값: SECONDS, MINUTES, HOURS, DAYS 등
     * 기본값: MINUTES (분 단위)
     */
    ChronoUnit unit() default ChronoUnit.MINUTES;

    /**
     * Rate Limiting 키 생성 전략
     * 어떤 기준으로 요청을 구분하여 제한을 적용할지 결정
     * 기본값: IP_AND_USER (IP와 사용자 정보 조합)
     */
    KeyType keyType() default KeyType.IP_AND_USER;

    /**
     * 커스텀 키 표현식 (SpEL 지원)
     * keyType이 CUSTOM일 때 사용되는 Spring Expression Language 표현식
     * 사용 가능한 변수: request, ip, user, authorities 등
     * 예: "#{request.getParameter('userId')}", "#{user + '_' + ip}"
     * 기본값: 빈 문자열 (사용하지 않음)
     */
    String keyExpression() default "";

    /**
     * 제한 초과 시 커스텀 에러 메시지
     * Rate Limit에 걸렸을 때 클라이언트에게 반환되는 에러 메시지
     * 사용자 친화적인 메시지로 작성하는 것을 권장
     * 기본값: "요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요."
     */
    String message() default "요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요.";

    /**
     * 이 Rate Limit이 전역 설정보다 우선하는지 여부
     * true: 어노테이션 설정이 전역 Rate Limiting 설정을 덮어씀
     * false: 전역 설정과 함께 적용됨 (더 엄격한 제한이 우선)
     * 기본값: false
     */
    boolean override() default false;

    /**
     * 키 생성 전략
     */
    enum KeyType {
        /**
         * IP 주소 기반
         * 클라이언트의 IP 주소를 기준으로 Rate Limiting 적용
         * 익명 사용자나 IP별 제한이 필요한 경우 사용
         */
        IP_ONLY,
        
        /**
         * 인증된 사용자 기반 (로그인 필요)
         * 로그인한 사용자의 UUID를 기준으로 Rate Limiting 적용
         * 사용자별 개별 제한이 필요한 경우 사용
         * 주의: 인증되지 않은 사용자의 경우 예외 발생
         */
        USER_ONLY,
        
        /**
         * IP와 사용자 조합 (기본값)
         * 로그인한 경우 사용자 UUID, 미로그인 시 IP 주소 사용
         * 가장 일반적이고 안전한 방식
         */
        IP_AND_USER,
        
        /**
         * 커스텀 키 표현식 사용
         * keyExpression에 정의된 SpEL 표현식으로 키 생성
         * 복잡한 비즈니스 로직이 필요한 경우 사용
         */
        CUSTOM
    }
}
