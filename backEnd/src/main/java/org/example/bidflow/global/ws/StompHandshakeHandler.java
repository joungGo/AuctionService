package org.example.bidflow.global.ws;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bidflow.global.utils.CookieUtil;
import org.example.bidflow.global.utils.JwtProvider;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class StompHandshakeHandler implements HandshakeInterceptor {

    private final JwtProvider jwtProvider;
    private final CookieUtil cookieUtil;

    // Handshake 전 처리 (쿠키 기반 인증 - 기본 검증만)
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        log.info("🔌 [WebSocket Handshake] 연결 시도 시작");
        log.info("🌐 [WebSocket Handshake] 요청 정보: {}", request.getRemoteAddress());
        log.info("📋 [WebSocket Handshake] 요청 헤더: {}", request.getHeaders());
        
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            log.error("❌ [WebSocket Handshake] 요청이 HTTP 요청이 아닙니다.");
            return false;
        }

        HttpServletRequest httpRequest = servletRequest.getServletRequest();
        log.info("🍪 [WebSocket Handshake] 쿠키 정보: {}", httpRequest.getHeader("Cookie"));
        log.info("🔑 [WebSocket Handshake] Authorization 헤더: {}", httpRequest.getHeader("Authorization"));
        
        String token = extractToken(httpRequest);
        log.info("🎫 [WebSocket Handshake] 추출된 토큰: {}", token != null ? "존재함" : "없음");

        // 토큰이 있고 유효하면 연결 허용 (세션에 저장)
        if (token != null && jwtProvider.validateToken(token)) {
            log.info("✅ [WebSocket Handshake] 인증 성공 - 쿠키 기반 인증 완료");
            log.info("👤 [WebSocket Handshake] 사용자 정보: {}", jwtProvider.parseUserUUID(token));
            
            // 세션 속성에 JWT 토큰 저장
            attributes.put("jwt-token", token);
            log.info("💾 [WebSocket Handshake] JWT 토큰 세션에 저장 완료");
            return true;
        }

        log.warn("❌ [WebSocket Handshake] 인증 실패 - JWT 토큰이 없거나 유효하지 않습니다.");
        log.warn("🔍 [WebSocket Handshake] 토큰 유효성: {}", token != null ? "토큰 존재하지만 유효하지 않음" : "토큰 없음");
        return false;
    }

    // Handshake 후 처리
    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception){
        if (exception != null) {
            log.error("❌ [WebSocket Handshake] Handshake 후 에러 발생: {}", exception.getMessage());
        } else {
            log.info("✅ [WebSocket Handshake] Handshake 완료 - WebSocket 연결 성공");
            log.info("🌐 [WebSocket Handshake] 연결된 클라이언트: {}", request.getRemoteAddress());
        }
    }

    // Jwt 토큰을 추출하는 메서드 (쿠키 우선)
    private String extractToken(HttpServletRequest request) {
        log.info("🔍 [WebSocket Token] 토큰 추출 시작");
        
        // 1. 쿠키에서 토큰 추출 (우선순위)
        String token = cookieUtil.getJwtFromCookie(request);
        if (token != null) {
            log.info("✅ [WebSocket Token] 쿠키에서 토큰 추출 성공");
            log.debug("🎫 [WebSocket Token] 추출된 토큰 (일부): {}...", token.substring(0, Math.min(20, token.length())));
            return token;
        }
        log.info("⚠️ [WebSocket Token] 쿠키에서 토큰을 찾을 수 없음");

        // 2. Authorization 헤더에서 토큰 추출
        token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            log.info("✅ [WebSocket Token] Authorization 헤더에서 토큰 추출 성공");
            String extractedToken = token.substring(7); // "Bearer " 제거
            log.debug("🎫 [WebSocket Token] 추출된 토큰 (일부): {}...", extractedToken.substring(0, Math.min(20, extractedToken.length())));
            return extractedToken;
        }
        log.info("⚠️ [WebSocket Token] Authorization 헤더에서 토큰을 찾을 수 없음");

        log.warn("❌ [WebSocket Token] 모든 방법으로 토큰 추출 실패");
        return null;
    }
}
