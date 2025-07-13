package org.example.bidflow.domain.bid.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bidflow.domain.auction.dto.AuctionBidRequest;
import org.example.bidflow.domain.bid.dto.model.response.BidCreateResponse;
import org.example.bidflow.domain.bid.service.BidService;
import org.example.bidflow.domain.bid.dto.model.response.webSocket.WebSocketResponse;
import org.example.bidflow.global.utils.CookieUtil;
import org.example.bidflow.global.utils.JwtProvider;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.example.bidflow.global.exception.ServiceException;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auctions")
public class BidController {

    private final BidService bidService;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final CookieUtil cookieUtil;
    private final JwtProvider jwtProvider;

    // 경매 입찰 컨트롤러 (세션 정보에서 JWT 토큰 추출)
    @MessageMapping("/auction/bid")
    public void createBids(@Payload AuctionBidRequest request, SimpMessageHeaderAccessor headerAccessor) {
        log.info("[WebSocket 입찰] 입찰 메시지 수신 - 경매ID: {}, 금액: {}", 
                request.getAuctionId(), request.getAmount());
        
        try {
            // 세션 정보에서 JWT 토큰 추출
            String token = extractTokenFromSession(headerAccessor);
            
            if (token == null) {
                log.error("[WebSocket 입찰 실패] JWT 토큰을 찾을 수 없습니다. 세션 정보: {}", 
                        headerAccessor.getSessionAttributes());
                sendErrorMessage(request.getAuctionId(), "인증 토큰이 없습니다. 다시 로그인해주세요.");
                return;
            }

            log.debug("[WebSocket 입찰] JWT 토큰 추출 성공: {}", token.substring(0, Math.min(token.length(), 20)) + "...");

            // 토큰 유효성 검증
            if (!jwtProvider.validateToken(token)) {
                log.error("[WebSocket 입찰 실패] 유효하지 않은 JWT 토큰입니다.");
                sendErrorMessage(request.getAuctionId(), "인증 토큰이 만료되었습니다. 다시 로그인해주세요.");
                return;
            }

            // 토큰에서 사용자 정보 추출
            String userUUID = jwtProvider.parseUserUUID(token);
            String nickname = jwtProvider.parseNickname(token);

            if (userUUID == null || nickname == null) {
                log.error("[WebSocket 입찰 실패] 토큰에서 사용자 정보를 추출할 수 없습니다. userUUID: {}, nickname: {}", 
                        userUUID, nickname);
                sendErrorMessage(request.getAuctionId(), "사용자 정보를 확인할 수 없습니다. 다시 로그인해주세요.");
                return;
            }

            log.info("[WebSocket 입찰] 입찰 요청 수신 - userUUID: {}, nickname: {}, 금액: {}", 
                    userUUID, nickname, request.getAmount());

            BidCreateResponse response = bidService.createBid(request.getAuctionId(), request, userUUID);

            // 입찰 성공 시 WebSocket 메시지 보낼 데이터
            WebSocketResponse res = WebSocketResponse.builder()
                    .message("입찰 성공")
                    .localDateTime(LocalDateTime.now())
                    .nickname(response.getNickname())
                    .currentBid(request.getAmount())
                    .build();

            simpMessagingTemplate.convertAndSend("/sub/auction/" + request.getAuctionId(), res);
            log.info("[WebSocket 입찰] 입찰 브로드캐스트 완료: /sub/auction/{}", request.getAuctionId());
            
        } catch (ServiceException e) {
            // 비즈니스 로직 위반 (정상적인 예외)
            log.warn("[WebSocket 입찰 실패] 비즈니스 로직 위반: {}", e.getMsg());
            sendErrorMessage(request.getAuctionId(), e.getMsg());
        } catch (Exception e) {
            log.error("[WebSocket 입찰 실패] 입찰 처리 중 오류 발생: {}", e.getMessage(), e);
            sendErrorMessage(request.getAuctionId(), "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    /**
     * 에러 메시지를 클라이언트에게 전송
     */
    private void sendErrorMessage(Long auctionId, String errorMessage) {
        try {
            log.warn("[WebSocket 입찰 실패] 에러 메시지 전송: {}", errorMessage);
            
            WebSocketResponse errorRes = WebSocketResponse.builder()
                    .message("입찰 실패: " + errorMessage)
                    .localDateTime(LocalDateTime.now())
                    .nickname("System")
                    .currentBid(0)
                    .build();

            simpMessagingTemplate.convertAndSend("/sub/auction/" + auctionId, errorRes);
            log.info("[WebSocket 오류] 에러 메시지 전송 완료: /sub/auction/{}", auctionId);
        } catch (Exception e) {
            log.error("[WebSocket 오류] 에러 메시지 전송 실패: {}", e.getMessage());
        }
    }

    /**
     * 세션 정보에서 JWT 토큰 추출
     */
    private String extractTokenFromSession(SimpMessageHeaderAccessor headerAccessor) {
        try {
            log.debug("[WebSocket 토큰 추출] 토큰 추출 시작");
            
            // 1. 세션 속성에서 토큰 추출
            Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
            log.debug("[WebSocket 토큰 추출] 세션 속성 확인: {}", sessionAttributes);
            
            if (sessionAttributes != null) {
                Object token = sessionAttributes.get("jwt-token");
                log.debug("[WebSocket 토큰 추출] 세션 속성에서 jwt-token 키 조회 결과: {}", token);
                if (token != null) {
                    log.debug("[WebSocket] 세션 속성에서 토큰 추출 성공");
                    return token.toString();
                }
            }

            // 2. 네이티브 헤더에서 토큰 추출
            Map<String, Object> nativeHeaders = (Map<String, Object>) headerAccessor.getHeader("nativeHeaders");
            log.debug("[WebSocket 토큰 추출] 네이티브 헤더 확인: {}", nativeHeaders);
            
            if (nativeHeaders != null) {
                Object cookieList = nativeHeaders.get("cookie");
                log.debug("[WebSocket 토큰 추출] 네이티브 헤더의 쿠키 리스트: {}", cookieList);
                
                if (cookieList instanceof List) {
                    for (Object cookieObj : (List<?>) cookieList) {
                        String cookie = cookieObj.toString();
                        log.debug("[WebSocket 토큰 추출] 쿠키 문자열 확인: {}", cookie);
                        
                        if (cookie.contains("jwt-token=")) {
                            String[] cookies = cookie.split(";");
                            for (String c : cookies) {
                                String trimmedCookie = c.trim();
                                if (trimmedCookie.startsWith("jwt-token=")) {
                                    log.debug("[WebSocket] 네이티브 헤더에서 토큰 추출 성공");
                                    return trimmedCookie.substring("jwt-token=".length());
                                }
                            }
                        }
                    }
                }
            }

            // 3. STOMP 헤더에서 토큰 추출
            List<String> cookies = headerAccessor.getNativeHeader("cookie");
            log.debug("[WebSocket 토큰 추출] STOMP 헤더의 쿠키 리스트: {}", cookies);
            
            if (cookies != null && !cookies.isEmpty()) {
                for (String cookieHeader : cookies) {
                    log.debug("[WebSocket 토큰 추출] STOMP 헤더 쿠키 문자열: {}", cookieHeader);
                    
                    if (cookieHeader != null && cookieHeader.contains("jwt-token=")) {
                        String[] cookieArray = cookieHeader.split(";");
                        for (String cookie : cookieArray) {
                            String trimmedCookie = cookie.trim();
                            if (trimmedCookie.startsWith("jwt-token=")) {
                                log.debug("[WebSocket] STOMP 헤더에서 토큰 추출 성공");
                                return trimmedCookie.substring("jwt-token=".length());
                            }
                        }
                    }
                }
            }

            // 4. 모든 헤더 정보 로깅 (디버깅용)
            log.warn("[WebSocket 토큰 추출] 모든 방법으로 토큰 추출 실패");
            log.warn("[WebSocket 토큰 추출] 전체 헤더 정보: {}", headerAccessor.getMessageHeaders());
            log.warn("[WebSocket 토큰 추출] 세션 ID: {}", headerAccessor.getSessionId());
            
            return null;
            
        } catch (Exception e) {
            log.error("[WebSocket] 토큰 추출 중 오류 발생: {}", e.getMessage(), e);
            return null;
        }
    }
}
