package org.example.bidflow.global.utils;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;

@Component
public class JwtProvider {

    @Value("${jwt.secret}")
    private String SECRET_KEY;

    @Value("${jwt.expiration-time}")
    private Long EXPIRATION_TIME;

    private SecretKey secretKey; // 지연 초기화를 위해 final 제거

    // SecretKey 지연 초기화 메서드
    private SecretKey getSecretKey() {
        if (secretKey == null) {
            secretKey = Keys.hmacShaKeyFor(SECRET_KEY.getBytes());
        }
        return secretKey;
    }

    // JWT 토큰을 생성하는 메서드
    public String generateToken(Map<String, Object> claims, String email) {
        return Jwts.builder()
                .claims(claims) // 사용자 정보 포함
                .subject(email)  // subject에 이메일 저장
                .issuedAt(new Date()) // 발급 시간 설정
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME)) // 만료 시간 설정
                .signWith(getSecretKey()) // 서명 적용 (알고리즘 자동 선택)
                .compact(); // 최종적으로 문자열로 변환하여 반환
    }

    // JWT 파싱
    public Claims parseClaims(String token) {

        // Jwts.parser()를 사용하여 JWT 파싱
        return Jwts.parser()
                .verifyWith(getSecretKey()) // secretKey로 JWT 서명 검증
                .build() // 빌드하여 실제 파서 객체 생성
                .parseSignedClaims(token) // 서명된 JWT를 파싱하여 claims를 추출
                .getPayload(); // JWT에서 payload (실제 데이터)를 반환
    }

    // userUUID 직접 반환
    public String parseUserUUID(String token) {
        return parseClaims(token).get("userUUID", String.class); // 자동으로 userUUID 파싱
    }

    // nickname 직접 반환
    public String parseNickname(String token) {
        return parseClaims(token).get("nickname", String.class); // 자동으로 nickname 파싱
    }

    public String parseRole(String token) {
        return parseClaims(token).get("role", String.class);
    }


    // 토큰 유효성 검증
    public boolean validateToken(String token) {
        try {
            // JWT의 유효성을 검증하는 메서드
            // Jwts.parser()로 토큰 파싱 및 서명 검증을 진행`
            // secretKey로 JWT 서명을 검증하여 유효한 토큰인지 확인
            Jwts.parser()
                    .verifyWith(getSecretKey()) // SecretKey 검증
                    .build() // 빌드하여 실제 파서 객체 생성
                    .parseSignedClaims(token); // JWT 파싱 및 서명 검증`

            return true; // 검증 성공 (유효한 토큰)
        } catch (JwtException | IllegalArgumentException e) {
            // 예외 발생 시 잘못된 토큰, 만료된 토큰 등
            return false; // 검증 실패 (잘못된 토큰)
        }
    }

    // JWT에서 username (subject) 추출
    public String getUsername(String token) {
        return parseClaims(token).getSubject();
    }
}
