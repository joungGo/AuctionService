package org.example.bidflow.domain.bid.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bidflow.domain.auction.dto.AuctionBidRequest;
import org.example.bidflow.domain.bid.dto.model.response.BidCreateResponse;
import org.example.bidflow.domain.bid.dto.model.response.webSocket.WebSocketResponse;
import org.example.bidflow.domain.bid.service.BidService;
import org.example.bidflow.global.utils.JwtProvider;
import org.example.bidflow.global.exception.ServiceException;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;

/**
 * WebSocket 기반 입찰 처리를 담당하는 컨트롤러
 * 
 * 이 컨트롤러는 실시간 입찰 처리와 WebSocket 메시지 전송을 담당합니다.
 * REST API와 분리하여 WebSocket 전용 로직만 처리합니다.
 * 
 * @author AuctionService Team
 * @version 1.0
 * @since 1.0
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class WebSocketBidController {

    private final BidService bidService;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final JwtProvider jwtProvider;

    /**
     * WebSocket을 통한 실시간 입찰 처리
     * 
     * @param request 입찰 요청 정보
     * @param headerAccessor WebSocket 세션 헤더 정보
     */
    @MessageMapping("/auction/bid")
    public void createBids(@Payload AuctionBidRequest request, SimpMessageHeaderAccessor headerAccessor) {
        log.info("💰 [WebSocket 입찰] 입찰 메시지 수신 - 경매 ID: {}, 금액: {}", 
                request.getAuctionId(), request.getAmount());
        
        try {
            // JWT 토큰 검증 및 사용자 정보 추출
            BidUserInfo userInfo = validateAndExtractUserInfo(headerAccessor, request.getAuctionId());
            if (userInfo == null) return;

            // 입찰 처리
            BidCreateResponse response = processBid(request, userInfo);
            
            // WebSocket 메시지 브로드캐스트
            broadcastBidSuccess(request, response, userInfo);
            
        } catch (ServiceException e) {
            handleBusinessException(request.getAuctionId(), e);
        } catch (Exception e) {
            handleUnexpectedException(request.getAuctionId(), e);
        }
    }

    /**
     * JWT 토큰 검증 및 사용자 정보 추출
     */
    private BidUserInfo validateAndExtractUserInfo(SimpMessageHeaderAccessor headerAccessor, Long auctionId) {
        // 세션 정보에서 JWT 토큰 추출
        String token = extractTokenFromSession(headerAccessor);
        
        if (token == null) {
            log.error("❌ [WebSocket 입찰] JWT 토큰을 찾을 수 없습니다");
            sendErrorMessage(auctionId, "인증 토큰이 없습니다. 다시 로그인해주세요.");
            return null;
        }

        // 토큰 유효성 검증
        if (!jwtProvider.validateToken(token)) {
            log.error("❌ [WebSocket 입찰] 유효하지 않은 JWT 토큰");
            sendErrorMessage(auctionId, "인증 토큰이 만료되었습니다. 다시 로그인해주세요.");
            return null;
        }

        // 토큰에서 사용자 정보 추출
        String userUUID = jwtProvider.parseUserUUID(token);
        String nickname = jwtProvider.parseNickname(token);

        if (userUUID == null || nickname == null) {
            log.error("❌ [WebSocket 입찰] 사용자 정보 추출 실패");
            sendErrorMessage(auctionId, "사용자 정보를 확인할 수 없습니다. 다시 로그인해주세요.");
            return null;
        }

        log.info("👤 [WebSocket 입찰] 사용자 정보 확인 - UUID: {}, 닉네임: {}", userUUID, nickname);
        return new BidUserInfo(userUUID, nickname);
    }

    /**
     * 입찰 처리 수행
     */
    private BidCreateResponse processBid(AuctionBidRequest request, BidUserInfo userInfo) {
        log.info("💵 [WebSocket 입찰] 입찰 처리 시작 - 금액: {}", request.getAmount());
        BidCreateResponse response = bidService.createBid(request.getAuctionId(), request, userInfo.getUserUUID());
        log.info("✅ [WebSocket 입찰] 입찰 처리 성공");
        return response;
    }

    /**
     * 입찰 성공 메시지 브로드캐스트
     */
    private void broadcastBidSuccess(AuctionBidRequest request, BidCreateResponse response, BidUserInfo userInfo) {
        WebSocketResponse res = WebSocketResponse.builder()
                .message("입찰 성공")
                .localDateTime(LocalDateTime.now())
                .nickname(response.getNickname())
                .currentBid(request.getAmount())
                .userUUID(userInfo.getUserUUID())
                .build();

        String destination = "/sub/auction/" + request.getAuctionId();
        log.info("📤 [WebSocket 입찰] 브로드캐스트 메시지 전송 - 대상: {}", destination);
        
        simpMessagingTemplate.convertAndSend(destination, res);
        log.info("✅ [WebSocket 입찰] 브로드캐스트 완료");
    }

    /**
     * 비즈니스 로직 예외 처리
     */
    private void handleBusinessException(Long auctionId, ServiceException e) {
        log.warn("⚠️ [WebSocket 입찰] 비즈니스 로직 위반: {}", e.getMsg());
        sendErrorMessage(auctionId, e.getMsg());
    }

    /**
     * 예상치 못한 예외 처리
     */
    private void handleUnexpectedException(Long auctionId, Exception e) {
        log.error("💥 [WebSocket 입찰] 입찰 처리 중 오류 발생: {}", e.getMessage(), e);
        sendErrorMessage(auctionId, "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
    }

    /**
     * 세션에서 JWT 토큰 추출
     */
    private String extractTokenFromSession(SimpMessageHeaderAccessor headerAccessor) {
        if (headerAccessor == null || headerAccessor.getSessionAttributes() == null) {
            return null;
        }
        
        Object tokenObj = headerAccessor.getSessionAttributes().get("jwt_token");
        return tokenObj != null ? tokenObj.toString() : null;
    }

    /**
     * 에러 메시지 전송
     */
    private void sendErrorMessage(Long auctionId, String message) {
        WebSocketResponse errorResponse = WebSocketResponse.builder()
                .message(message)
                .localDateTime(LocalDateTime.now())
                .nickname("SYSTEM")
                .currentBid(0)
                .userUUID("SYSTEM")
                .build();

        String destination = "/sub/auction/" + auctionId;
        simpMessagingTemplate.convertAndSend(destination, errorResponse);
        log.error("❌ [WebSocket 입찰] 에러 메시지 전송: {} -> {}", message, destination);
    }

    /**
     * 입찰 사용자 정보를 담는 내부 클래스
     */
    private static class BidUserInfo {
        private final String userUUID;
        private final String nickname;

        public BidUserInfo(String userUUID, String nickname) {
            this.userUUID = userUUID;
            this.nickname = nickname;
        }

        public String getUserUUID() { return userUUID; }
        public String getNickname() { return nickname; }
    }
}
