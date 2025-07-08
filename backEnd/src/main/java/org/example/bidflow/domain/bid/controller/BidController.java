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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auctions")
public class BidController {

    private final BidService bidService;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final CookieUtil cookieUtil;
    private final JwtProvider jwtProvider;

    // 경매 입찰 컨트롤러 (쿠키에서 직접 JWT 토큰 추출)
    @MessageMapping("/auction/bid")
    public void createBids(@Payload AuctionBidRequest request, @Header("cookie") String cookieHeader) {
        try {
            // 쿠키에서 JWT 토큰 추출
            String token = extractTokenFromCookieHeader(cookieHeader);
            
            if (token == null) {
                log.error("[WebSocket 입찰 실패] JWT 토큰을 찾을 수 없습니다.");
                return;
            }

            // 토큰 유효성 검증
            if (!jwtProvider.validateToken(token)) {
                log.error("[WebSocket 입찰 실패] 유효하지 않은 JWT 토큰입니다.");
                return;
            }

            // 토큰에서 사용자 정보 추출
            String userUUID = jwtProvider.parseUserUUID(token);
            String nickname = jwtProvider.parseNickname(token);

            if (userUUID == null || nickname == null) {
                log.error("[WebSocket 입찰 실패] 토큰에서 사용자 정보를 추출할 수 없습니다.");
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
        }
    }

    /**
     * 쿠키 헤더에서 JWT 토큰 추출
     */
    private String extractTokenFromCookieHeader(String cookieHeader) {
        if (cookieHeader == null || cookieHeader.isEmpty()) {
            return null;
        }

        // 쿠키 헤더에서 jwt-token 찾기
        String[] cookies = cookieHeader.split(";");
        for (String cookie : cookies) {
            String trimmedCookie = cookie.trim();
            if (trimmedCookie.startsWith("jwt-token=")) {
                return trimmedCookie.substring("jwt-token=".length());
            }
        }
        
        return null;
    }
}
