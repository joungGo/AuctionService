package org.example.bidflow.global.utils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bidflow.global.exception.ServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * JWT 토큰 검증 공통 유틸리티
 * 
 * 이 클래스는 JWT 토큰 검증과 관련된 공통 로직을 중앙화하여
 * 코드 중복을 제거하고 일관성을 보장합니다.
 * 
 * @author AuctionService Team
 * @version 1.0
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtValidationHelper {

    private final JwtProvider jwtProvider;

    /**
     * JWT 토큰의 유효성을 검증하고 사용자 UUID를 반환합니다.
     * 
     * @param token JWT 토큰
     * @return 사용자 UUID
     * @throws ServiceException 토큰이 유효하지 않은 경우
     */
    public String validateTokenAndGetUserUUID(String token) {
        if (token == null || token.trim().isEmpty()) {
            log.warn("[JWT 검증 실패] 토큰이 null 또는 빈 문자열입니다.");
            throw new ServiceException("401", "인증 토큰이 없습니다.");
        }

        if (!jwtProvider.validateToken(token)) {
            log.warn("[JWT 검증 실패] 유효하지 않은 토큰입니다.");
            throw new ServiceException("401", "유효하지 않은 인증 토큰입니다.");
        }

        String userUUID = jwtProvider.parseUserUUID(token);
        if (userUUID == null || userUUID.trim().isEmpty()) {
            log.warn("[JWT 검증 실패] 토큰에서 사용자 UUID를 추출할 수 없습니다.");
            throw new ServiceException("401", "사용자 정보를 확인할 수 없습니다.");
        }

        return userUUID;
    }

    /**
     * JWT 토큰의 유효성을 검증하고 사용자 닉네임을 반환합니다.
     * 
     * @param token JWT 토큰
     * @return 사용자 닉네임
     * @throws ServiceException 토큰이 유효하지 않은 경우
     */
    public String validateTokenAndGetNickname(String token) {
        if (token == null || token.trim().isEmpty()) {
            log.warn("[JWT 검증 실패] 토큰이 null 또는 빈 문자열입니다.");
            throw new ServiceException("401", "인증 토큰이 없습니다.");
        }

        if (!jwtProvider.validateToken(token)) {
            log.warn("[JWT 검증 실패] 유효하지 않은 토큰입니다.");
            throw new ServiceException("401", "유효하지 않은 인증 토큰입니다.");
        }

        String nickname = jwtProvider.parseNickname(token);
        if (nickname == null || nickname.trim().isEmpty()) {
            log.warn("[JWT 검증 실패] 토큰에서 사용자 닉네임을 추출할 수 없습니다.");
            throw new ServiceException("401", "사용자 정보를 확인할 수 없습니다.");
        }

        return nickname;
    }

    /**
     * JWT 토큰의 유효성을 검증하고 사용자 정보를 반환합니다.
     * 
     * @param token JWT 토큰
     * @return 사용자 정보 (UUID, 닉네임)
     * @throws ServiceException 토큰이 유효하지 않은 경우
     */
    public UserInfo validateTokenAndGetUserInfo(String token) {
        if (token == null || token.trim().isEmpty()) {
            log.warn("[JWT 검증 실패] 토큰이 null 또는 빈 문자열입니다.");
            throw new ServiceException("401", "인증 토큰이 없습니다.");
        }

        if (!jwtProvider.validateToken(token)) {
            log.warn("[JWT 검증 실패] 유효하지 않은 토큰입니다.");
            throw new ServiceException("401", "유효하지 않은 인증 토큰입니다.");
        }

        String userUUID = jwtProvider.parseUserUUID(token);
        String nickname = jwtProvider.parseNickname(token);

        if (userUUID == null || userUUID.trim().isEmpty()) {
            log.warn("[JWT 검증 실패] 토큰에서 사용자 UUID를 추출할 수 없습니다.");
            throw new ServiceException("401", "사용자 정보를 확인할 수 없습니다.");
        }

        if (nickname == null || nickname.trim().isEmpty()) {
            log.warn("[JWT 검증 실패] 토큰에서 사용자 닉네임을 추출할 수 없습니다.");
            throw new ServiceException("401", "사용자 정보를 확인할 수 없습니다.");
        }

        return new UserInfo(userUUID, nickname);
    }

    /**
     * JWT 토큰의 유효성을 검증합니다.
     * 
     * @param token JWT 토큰
     * @return 토큰이 유효한지 여부
     */
    public boolean isValidToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }
        return jwtProvider.validateToken(token);
    }

    /**
     * 사용자 정보를 담는 내부 클래스
     */
    public static class UserInfo {
        private final String userUUID;
        private final String nickname;

        public UserInfo(String userUUID, String nickname) {
            this.userUUID = userUUID;
            this.nickname = nickname;
        }

        public String getUserUUID() { return userUUID; }
        public String getNickname() { return nickname; }
    }
}
