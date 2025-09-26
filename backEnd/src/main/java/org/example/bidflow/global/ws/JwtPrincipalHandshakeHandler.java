package org.example.bidflow.global.ws;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bidflow.global.utils.CookieUtil;
import org.example.bidflow.global.utils.JwtProvider;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

/**
 * WebSocket 핸드셰이크 단계에서 JWT를 검증해 Principal을 설정하는 핸들러.
 * - 쿠키 또는 Authorization 헤더에서 JWT 추출 및 유효성 검사
 * - Principal의 name을 "nickname::userUUID" 형태로 구성하여 세션 식별에 활용
 * - 설정된 Principal은 STOMP 세션 및 구독/메시징 보안에 사용됨
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtPrincipalHandshakeHandler extends DefaultHandshakeHandler {

    private final JwtProvider jwtProvider;
    private final CookieUtil cookieUtil;

    @Override
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {
        // HTTP 요청이 아닌 경우 기본 동작 수행 -> Mock 객체나 다른 구현체를 사용하는 경우, servelt 컨테이너가 아닌 다른 서버 환경에서 실행될 때 등
        // 예외 케이스로 인한 방어적 프로그래밍(혹시 몰라서...)
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            /**
             * Note: 기본 동작의 경우 아래를 따름
             * null 반환: 기본적으로 Principal을 생성하지 않음
             * 익명 사용자: WebSocket 연결이 익명으로 처리됨
             * 인증 없음: 별도의 JWT 검증이나 사용자 식별 로직 없음
             */
            return super.determineUser(request, wsHandler, attributes);
        }

        HttpServletRequest httpRequest = servletRequest.getServletRequest();
        String token = cookieUtil.getJwtFromCookie(httpRequest);
        if (token == null) {
            String auth = httpRequest.getHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                token = auth.substring(7);
            }
        }

        if (token != null && jwtProvider.validateToken(token)) {
            String userUUID = jwtProvider.parseUserUUID(token);
            String nickname = jwtProvider.parseNickname(token);
            String combined = (nickname != null ? nickname : "unknown") + "::" + (userUUID != null ? userUUID : "unknown");
            log.info("👤 [Principal] Handshake Principal 설정 - nickname: {}, userUUID: {}", nickname, userUUID);
            return new Principal() {
                @Override
                public String getName() {
                    // name: "nickname::userUUID"
                    return combined;
                }
            };
        }

        return super.determineUser(request, wsHandler, attributes);
    }
}


