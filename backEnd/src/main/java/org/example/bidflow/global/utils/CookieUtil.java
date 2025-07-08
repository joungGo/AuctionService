package org.example.bidflow.global.utils;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CookieUtil {
    
    private static final String JWT_COOKIE_NAME = "jwt-token";
    private static final int COOKIE_EXPIRE_SECONDS = 24 * 60 * 60; // 24시간
    
    @Value("${cookie.secure:true}")
    private boolean isSecure;
    
    @Value("${cookie.same-site:Strict}")
    private String sameSite;
    
    @Value("${cookie.domain:#{null}}")
    private String domain;
    
    /**
     * JWT 토큰을 쿠키에 저장
     */
    public void addJwtCookie(HttpServletResponse response, String token) {
        try {
            Cookie cookie = new Cookie(JWT_COOKIE_NAME, token);
            cookie.setHttpOnly(true);  // XSS 방지 - JavaScript에서 접근 불가
            cookie.setSecure(isSecure); // HTTPS에서만 전송 (배포 환경에서 true)
            cookie.setPath("/");       // 모든 경로에서 전송
            cookie.setMaxAge(COOKIE_EXPIRE_SECONDS);
            
            // 도메인 설정 (배포 환경에서 설정)
            if (domain != null && !domain.isEmpty()) {
                cookie.setDomain(domain);
            }
            
            response.addCookie(cookie);
            
            // SameSite 설정을 위한 추가 헤더 설정
            String cookieHeader = String.format("%s=%s; Path=/; Max-Age=%d; HttpOnly; SameSite=%s",
                    JWT_COOKIE_NAME, token, COOKIE_EXPIRE_SECONDS, sameSite);
            
            if (isSecure) {
                cookieHeader += "; Secure";
            }
            
            if (domain != null && !domain.isEmpty()) {
                cookieHeader += "; Domain=" + domain;
            }
            
            response.addHeader("Set-Cookie", cookieHeader);
            
            log.info("[쿠키 설정] JWT 토큰 쿠키 생성 완료 - Secure: {}, SameSite: {}", isSecure, sameSite);
            
        } catch (Exception e) {
            log.error("[쿠키 설정 실패] JWT 토큰 쿠키 생성 중 오류 발생: {}", e.getMessage(), e);
            throw new RuntimeException("쿠키 설정 중 오류가 발생했습니다.", e);
        }
    }
    
    /**
     * 쿠키에서 JWT 토큰 추출
     */
    public String getJwtFromCookie(HttpServletRequest request) {
        try {
            Cookie[] cookies = request.getCookies();
            if (cookies == null) {
                log.debug("[쿠키 조회] 요청에 쿠키가 없습니다.");
                return null;
            }
            
            for (Cookie cookie : cookies) {
                if (JWT_COOKIE_NAME.equals(cookie.getName())) {
                    String token = cookie.getValue();
                    log.debug("[쿠키 조회] JWT 토큰 추출 성공");
                    return token;
                }
            }
            
            log.debug("[쿠키 조회] JWT 토큰 쿠키를 찾을 수 없습니다.");
            return null;
            
        } catch (Exception e) {
            log.error("[쿠키 조회 실패] JWT 토큰 추출 중 오류 발생: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * JWT 토큰 쿠키 삭제 (로그아웃 시 사용)
     */
    public void deleteJwtCookie(HttpServletResponse response) {
        try {
            Cookie cookie = new Cookie(JWT_COOKIE_NAME, null);
            cookie.setHttpOnly(true);
            cookie.setSecure(isSecure);
            cookie.setPath("/");
            cookie.setMaxAge(0); // 즉시 만료
            
            if (domain != null && !domain.isEmpty()) {
                cookie.setDomain(domain);
            }
            
            response.addCookie(cookie);
            
            // 추가 헤더 설정으로 확실한 삭제 보장
            String cookieHeader = String.format("%s=; Path=/; Max-Age=0; HttpOnly; SameSite=%s",
                    JWT_COOKIE_NAME, sameSite);
            
            if (isSecure) {
                cookieHeader += "; Secure";
            }
            
            if (domain != null && !domain.isEmpty()) {
                cookieHeader += "; Domain=" + domain;
            }
            
            response.addHeader("Set-Cookie", cookieHeader);
            
            log.info("[쿠키 삭제] JWT 토큰 쿠키 삭제 완료");
            
        } catch (Exception e) {
            log.error("[쿠키 삭제 실패] JWT 토큰 쿠키 삭제 중 오류 발생: {}", e.getMessage(), e);
            throw new RuntimeException("쿠키 삭제 중 오류가 발생했습니다.", e);
        }
    }
    
    /**
     * 쿠키 이름 반환 (테스트 및 디버깅 용도)
     */
    public String getJwtCookieName() {
        return JWT_COOKIE_NAME;
    }
} 