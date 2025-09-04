package org.example.bidflow.global.filter;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.SecurityException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bidflow.domain.user.service.JwtBlacklistService;
import org.example.bidflow.global.exception.ServiceException;
import org.example.bidflow.global.utils.CookieUtil;
import org.example.bidflow.global.utils.JwtProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@Order(1) // Rate Limiting 필터보다 먼저 실행되어야 함
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final JwtBlacklistService jwtBlacklistService;
    private final CookieUtil cookieUtil;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        // WebSocket 연결 경로는 JWT 필터에서 제외
        return path.startsWith("/ws");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = resolveToken(request);

        if (token != null) {
            try {
                // 블랙리스트 확인
                if (jwtBlacklistService.isBlacklisted(token)) {
                    log.warn("[JWT 필터] 블랙리스트 토큰 접근 시도: {}", token.substring(0, Math.min(20, token.length())));
                    throw new ServiceException(HttpStatus.UNAUTHORIZED.value() + "", "로그아웃한 토큰으로 접근할 수 없습니다.");
                }

                // 토큰 유효성 검사
                if (jwtProvider.validateToken(token)) {
                    // 토큰에서 필요한 정보 추출
                    String username = jwtProvider.getUsername(token);
                    String role = jwtProvider.parseRole(token);  // 👉 role 추출
                    log.debug("[JWT 필터] 토큰 검증 성공 - 사용자: {}, 역할: {}", username, role);

                    // 직접 UserDetails 생성
                    UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                            .username(username)
                            .password("") // 비밀번호는 인증에 필요하지 않음
                            .authorities(new SimpleGrantedAuthority(role)) // 권한 설정
                            .build();

                    // 인증 객체 생성
                    UsernamePasswordAuthenticationToken authenticationToken =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

                    // SecurityContextHolder에 인증 정보 등록
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                } else {
                    log.warn("[JWT 필터] 토큰 유효성 검증 실패: {}", token.substring(0, Math.min(20, token.length())));
                    throw new ServiceException(HttpStatus.UNAUTHORIZED.value() + "", "유효하지 않은 토큰입니다.");
                }
                
            } catch (ExpiredJwtException e) {
                log.warn("[JWT 필터] 만료된 토큰 접근: {}", e.getMessage());
                throw e; // GlobalExceptionAdvisor에서 처리
            } catch (MalformedJwtException e) {
                log.warn("[JWT 필터] 잘못된 토큰 형식: {}", e.getMessage());
                throw e; // GlobalExceptionAdvisor에서 처리
            } catch (UnsupportedJwtException e) {
                log.warn("[JWT 필터] 지원되지 않는 토큰: {}", e.getMessage());
                throw e; // GlobalExceptionAdvisor에서 처리
            } catch (SecurityException e) {
                log.warn("[JWT 필터] 토큰 서명 검증 실패: {}", e.getMessage());
                throw e; // GlobalExceptionAdvisor에서 처리
            } catch (JwtException e) {
                log.warn("[JWT 필터] JWT 처리 오류: {}", e.getMessage());
                throw e; // GlobalExceptionAdvisor에서 처리
            } catch (ServiceException e) {
                throw e; // 이미 처리된 서비스 예외는 그대로 전달
            } catch (Exception e) {
                log.error("[JWT 필터] 예상치 못한 오류: {}", e.getMessage(), e);
                throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR.value() + "", "토큰 처리 중 오류가 발생했습니다.");
            }
        }

        filterChain.doFilter(request, response);
    }

    // 쿠키에서 토큰 추출 (기존 Authorization 헤더도 호환성을 위해 유지)
    private String resolveToken(HttpServletRequest request) {
        // 1. 쿠키에서 토큰 추출 (우선순위)
        String tokenFromCookie = cookieUtil.getJwtFromCookie(request);
        if (tokenFromCookie != null) {
            log.debug("[JWT 필터] 쿠키에서 토큰 추출 성공");
            return tokenFromCookie;
        }
        
        // 2. Authorization 헤더에서 토큰 추출 (하위 호환성 유지)
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            log.debug("[JWT 필터] Authorization 헤더에서 토큰 추출 성공");
            return bearerToken.substring(7);
        }
        
        log.debug("[JWT 필터] 토큰을 찾을 수 없습니다.");
        return null;
    }
}

