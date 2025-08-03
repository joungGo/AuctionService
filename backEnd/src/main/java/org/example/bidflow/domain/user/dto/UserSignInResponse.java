package org.example.bidflow.domain.user.dto;

import lombok.Builder;
import lombok.Getter;
import org.example.bidflow.domain.user.entity.User;

@Builder
@Getter
public class UserSignInResponse {

    private final String token; // 발급된 JWT 토큰 (쿠키 기반 인증에서는 null)
    private final String userUUID; // 사용자 고유 식별자
    private final String nickname; // 사용자 닉네임
    private final String email; // 사용자 이메일

    // User 객체와 토큰을 받아 응답 객체를 생성하는 정적 메서드 (기존 호환성 유지)
    public static UserSignInResponse from(User user, String token) {
        return UserSignInResponse.builder()
                .userUUID(user.getUserUUID())
                .token(token)
                .nickname(user.getNickname())
                .email(user.getEmail())
                .build();
    }
    
    // 쿠키 기반 인증용 정적 메서드 (토큰 없이 응답 생성)
    public static UserSignInResponse fromCookie(User user) {
        return UserSignInResponse.builder()
                .userUUID(user.getUserUUID())
                .token(null)  // 쿠키에 저장되므로 응답에 포함하지 않음
                .nickname(user.getNickname())
                .email(user.getEmail())
                .build();
    }
}