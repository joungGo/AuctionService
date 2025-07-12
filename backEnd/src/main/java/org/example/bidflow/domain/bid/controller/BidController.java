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
                sendErrorMessage(request.getAuctionId(), "인증 실패: JWT 토큰을 찾을 수 없습니다.");
                return;
            }

            log.debug("[WebSocket 입찰] JWT 토큰 추출 성공: {}", token.substring(0, Math.min(token.length(), 20)) + "...");

            // 토큰 유효성 검증
            if (!jwtProvider.validateToken(token)) {
                log.error("[WebSocket 입찰 실패] 유효하지 않은 JWT 토큰입니다.");
                sendErrorMessage(request.getAuctionId(), "인증 실패: 유효하지 않은 JWT 토큰입니다.");
                return;
            }

            // 토큰에서 사용자 정보 추출
            String userUUID = jwtProvider.parseUserUUID(token);
            String nickname = jwtProvider.parseNickname(token);

            if (userUUID == null || nickname == null) {
                log.error("[WebSocket 입찰 실패] 토큰에서 사용자 정보를 추출할 수 없습니다. userUUID: {}, nickname: {}", 
                        userUUID, nickname);
                sendErrorMessage(request.getAuctionId(), "인증 실패: 사용자 정보를 추출할 수 없습니다.");
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
            
        } catch (Exception e) {
            log.error("[WebSocket 입찰 실패] 입찰 처리 중 오류 발생: {}", e.getMessage(), e);
            sendErrorMessage(request.getAuctionId(), "입찰 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 에러 메시지를 클라이언트에게 전송
     */
    private void sendErrorMessage(Long auctionId, String errorMessage) {
        try {
            WebSocketResponse errorRes = WebSocketResponse.builder()
                    .message("입찰 실패")
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
            // 1. 세션 속성에서 토큰 추출
            Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
            if (sessionAttributes != null) {
                Object token = sessionAttributes.get("jwt-token");
                if (token != null) {
                    log.debug("[WebSocket] 세션 속성에서 토큰 추출 성공");
                    return token.toString();
                }
            }

            // 2. 네이티브 헤더에서 토큰 추출
            Map<String, Object> nativeHeaders = (Map<String, Object>) headerAccessor.getHeader("nativeHeaders");
            if (nativeHeaders != null) {
                Object cookieList = nativeHeaders.get("cookie");
                if (cookieList instanceof List) {
                    for (Object cookieObj : (List<?>) cookieList) {
                        String cookie = cookieObj.toString();
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
            if (cookies != null && !cookies.isEmpty()) {
                for (String cookieHeader : cookies) {
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

            log.debug("[WebSocket] 모든 방법으로 토큰 추출 실패");
            return null;
            
        } catch (Exception e) {
            log.error("[WebSocket] 토큰 추출 중 오류 발생: {}", e.getMessage(), e);
            return null;
        }
    }
}
