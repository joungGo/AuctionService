package org.example.bidflow.global.app;

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
import org.springframework.core.env.Environment;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class StompHandshakeHandler implements HandshakeInterceptor {

    private final JwtProvider jwtProvider;
    private final CookieUtil cookieUtil;
    private final Environment environment;

    // Handshake 전 처리 (쿠키 기반 인증 - 기본 검증만)
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            log.error("[WebSocket Handshake] 요청이 HTTP 요청이 아닙니다.");
            return false;
        }

        HttpServletRequest httpRequest = servletRequest.getServletRequest();

        // 개발 환경(dev)에서는 인증 우회 허용 (로컬 크로스사이트 쿠키 문제 대응)
        if (isDevProfileActive()) {
            log.info("[WebSocket Handshake][DEV] 개발환경 - 인증 우회로 연결 허용");
            return true;
        }

        String token = extractToken(httpRequest);

        // 토큰이 있고 유효하면 연결 허용 (세션에 저장)
        if (token != null && jwtProvider.validateToken(token)) {
            log.info("[WebSocket Handshake] 성공 - 쿠키 기반 인증 완료");
            // 세션 속성에 JWT 토큰 저장
            attributes.put("jwt-token", token);
            log.debug("[WebSocket Handshake] JWT 토큰 세션에 저장 완료");
            return true;
        }

        log.warn("[WebSocket Handshake] 인증 실패 - JWT 토큰이 없거나 유효하지 않습니다.");
        return false;
    }

    // Handshake 후 처리
    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception){
        // 추가 로직 필요 시 사용.
    }

    // Jwt 토큰을 추출하는 메서드 (쿠키 우선)
    private String extractToken(HttpServletRequest request) {
        // 1. 쿠키에서 토큰 추출 (우선순위)
        String token = cookieUtil.getJwtFromCookie(request);
        if (token != null) {
            log.debug("[WebSocket] 쿠키에서 토큰 추출 성공");
            return token;
        }

        // 2. Authorization 헤더에서 토큰 추출
        token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            log.debug("[WebSocket] Authorization 헤더에서 토큰 추출 성공");
            return token.substring(7); // "Bearer " 제거
        }

        log.debug("[WebSocket] 토큰을 찾을 수 없습니다.");
        return null;
    }

    // 활성 프로파일에 dev가 포함되어 있는지 검사
    private boolean isDevProfileActive() {
        try {
            for (String p : environment.getActiveProfiles()) {
                if ("dev".equalsIgnoreCase(p)) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }
}
