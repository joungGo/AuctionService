package org.example.bidflow.domain.bid.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bidflow.domain.auction.dto.AuctionBidRequest;
import org.example.bidflow.domain.bid.dto.model.response.BidCreateResponse;
import org.example.bidflow.domain.bid.dto.model.response.BidHistoryResponse;
import org.example.bidflow.domain.bid.service.BidService;
import org.example.bidflow.domain.bid.dto.model.response.webSocket.WebSocketResponse;
import org.example.bidflow.global.dto.RsData;
import org.example.bidflow.global.utils.CookieUtil;
import org.example.bidflow.global.utils.JwtProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/auctions")
public class BidController {

    private final BidService bidService;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final CookieUtil cookieUtil;
    private final JwtProvider jwtProvider;

    // 경매 입찰 컨트롤러 (세션 정보에서 JWT 토큰 추출)
    @MessageMapping("/auction/bid")
    public void createBids(@Payload AuctionBidRequest request, SimpMessageHeaderAccessor headerAccessor) {
        log.info("💰 [WebSocket 입찰] 입찰 메시지 수신");
        log.info("🎯 [WebSocket 입찰] 경매 ID: {}", request.getAuctionId());
        log.info("💵 [WebSocket 입찰] 입찰 금액: {}", request.getAmount());
        log.info("📋 [WebSocket 입찰] 세션 ID: {}", headerAccessor.getSessionId());
        
        try {
            // 세션 정보에서 JWT 토큰 추출
            String token = extractTokenFromSession(headerAccessor);
            
            if (token == null) {
                log.error("❌ [WebSocket 입찰] JWT 토큰을 찾을 수 없습니다");
                log.error("🔍 [WebSocket 입찰] 세션 정보: {}", headerAccessor.getSessionAttributes());
                sendErrorMessage(request.getAuctionId(), "인증 토큰이 없습니다. 다시 로그인해주세요.");
                return;
            }

            log.info("✅ [WebSocket 입찰] JWT 토큰 추출 성공");

            // 토큰 유효성 검증
            if (!jwtProvider.validateToken(token)) {
                log.error("❌ [WebSocket 입찰] 유효하지 않은 JWT 토큰");
                sendErrorMessage(request.getAuctionId(), "인증 토큰이 만료되었습니다. 다시 로그인해주세요.");
                return;
            }

            // 토큰에서 사용자 정보 추출
            String userUUID = jwtProvider.parseUserUUID(token);
            String nickname = jwtProvider.parseNickname(token);

            if (userUUID == null || nickname == null) {
                log.error("❌ [WebSocket 입찰] 사용자 정보 추출 실패");
                log.error("👤 [WebSocket 입찰] userUUID: {}, nickname: {}", userUUID, nickname);
                sendErrorMessage(request.getAuctionId(), "사용자 정보를 확인할 수 없습니다. 다시 로그인해주세요.");
                return;
            }

            log.info("👤 [WebSocket 입찰] 사용자 정보 확인");
            log.info("🆔 [WebSocket 입찰] userUUID: {}", userUUID);
            log.info("👤 [WebSocket 입찰] nickname: {}", nickname);
            log.info("💵 [WebSocket 입찰] 입찰 금액: {}", request.getAmount());

            BidCreateResponse response = bidService.createBid(request.getAuctionId(), request, userUUID);
            log.info("✅ [WebSocket 입찰] 입찰 처리 성공");

            // 입찰 성공 시 WebSocket 메시지 보낼 데이터
            WebSocketResponse res = WebSocketResponse.builder()
                    .message("입찰 성공")
                    .localDateTime(LocalDateTime.now())
                    .nickname(response.getNickname())
                    .currentBid(request.getAmount())
                    .userUUID(userUUID)  // 최고 입찰자의 UUID 포함
                    .build();

            log.info("📤 [WebSocket 입찰] 브로드캐스트 메시지 전송 시작");
            log.info("🎯 [WebSocket 입찰] 전송 대상: /sub/auction/{}", request.getAuctionId());
            log.info("📄 [WebSocket 입찰] 메시지 내용: {}", res);
            
            simpMessagingTemplate.convertAndSend("/sub/auction/" + request.getAuctionId(), res);
            log.info("✅ [WebSocket 입찰] 브로드캐스트 완료: /sub/auction/{}", request.getAuctionId());
            
        } catch (ServiceException e) {
            // 비즈니스 로직 위반 (정상적인 예외)
            log.warn("⚠️ [WebSocket 입찰] 비즈니스 로직 위반: {}", e.getMsg());
            sendErrorMessage(request.getAuctionId(), e.getMsg());
        } catch (Exception e) {
            log.error("💥 [WebSocket 입찰] 입찰 처리 중 오류 발생: {}", e.getMessage(), e);
            sendErrorMessage(request.getAuctionId(), "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    // 특정 경매의 입찰 내역 조회 API
    @GetMapping("/{auctionId}/bids")
    public ResponseEntity<RsData<List<BidHistoryResponse>>> getBidHistoryByAuction(@PathVariable Long auctionId) {
        log.info("[REST API] 경매 입찰 내역 조회 요청 - 경매ID: {}", auctionId);
        
        try {
            List<BidHistoryResponse> bidHistory = bidService.getBidHistoryByAuction(auctionId);
            RsData<List<BidHistoryResponse>> rsData = new RsData<>("200", "입찰 내역 조회가 완료되었습니다.", bidHistory);
            return ResponseEntity.ok(rsData);
        } catch (ServiceException e) {
            log.error("[REST API] 경매 입찰 내역 조회 실패 - 경매ID: {}, 오류: {}", auctionId, e.getMsg());
            RsData<List<BidHistoryResponse>> rsData = new RsData<>(e.getCode(), e.getMsg(), null);
            return ResponseEntity.badRequest().body(rsData);
        } catch (Exception e) {
            log.error("[REST API] 경매 입찰 내역 조회 중 예상치 못한 오류 - 경매ID: {}, 오류: {}", auctionId, e.getMessage(), e);
            RsData<List<BidHistoryResponse>> rsData = new RsData<>("500", "서버 오류가 발생했습니다.", null);
            return ResponseEntity.internalServerError().body(rsData);
        }
    }

    // 특정 경매의 입찰 내역 페이징 조회 API
    @GetMapping("/{auctionId}/bids/paging")
    public ResponseEntity<RsData<Page<BidHistoryResponse>>> getBidHistoryByAuctionWithPaging(
            @PathVariable Long auctionId,
            @PageableDefault(size = 20, sort = "bidTime") Pageable pageable) {
        log.info("[REST API] 경매 입찰 내역 페이징 조회 요청 - 경매ID: {}, 페이지: {}", auctionId, pageable.getPageNumber());
        
        try {
            Page<BidHistoryResponse> bidHistoryPage = bidService.getBidHistoryByAuctionWithPaging(auctionId, pageable);
            RsData<Page<BidHistoryResponse>> rsData = new RsData<>("200", "입찰 내역 페이징 조회가 완료되었습니다.", bidHistoryPage);
            return ResponseEntity.ok(rsData);
        } catch (ServiceException e) {
            log.error("[REST API] 경매 입찰 내역 페이징 조회 실패 - 경매ID: {}, 오류: {}", auctionId, e.getMsg());
            RsData<Page<BidHistoryResponse>> rsData = new RsData<>(e.getCode(), e.getMsg(), null);
            return ResponseEntity.badRequest().body(rsData);
        } catch (Exception e) {
            log.error("[REST API] 경매 입찰 내역 페이징 조회 중 예상치 못한 오류 - 경매ID: {}, 오류: {}", auctionId, e.getMessage(), e);
            RsData<Page<BidHistoryResponse>> rsData = new RsData<>("500", "서버 오류가 발생했습니다.", null);
            return ResponseEntity.internalServerError().body(rsData);
        }
    }

    // 특정 사용자의 입찰 내역 조회 API
    @GetMapping("/bids/my")
    public ResponseEntity<RsData<List<BidHistoryResponse>>> getMyBidHistory(@CookieValue(name = "jwt-token", required = false) String token) {
        log.info("[REST API] 내 입찰 내역 조회 요청");
        
        try {
            if (token == null || !jwtProvider.validateToken(token)) {
                RsData<List<BidHistoryResponse>> rsData = new RsData<>("401", "인증이 필요합니다.", null);
                return ResponseEntity.status(401).body(rsData);
            }
            
            String userUUID = jwtProvider.parseUserUUID(token);
            if (userUUID == null) {
                RsData<List<BidHistoryResponse>> rsData = new RsData<>("401", "사용자 정보를 확인할 수 없습니다.", null);
                return ResponseEntity.status(401).body(rsData);
            }
            
            List<BidHistoryResponse> bidHistory = bidService.getBidHistoryByUser(userUUID);
            RsData<List<BidHistoryResponse>> rsData = new RsData<>("200", "내 입찰 내역 조회가 완료되었습니다.", bidHistory);
            return ResponseEntity.ok(rsData);
        } catch (ServiceException e) {
            log.error("[REST API] 내 입찰 내역 조회 실패 - 오류: {}", e.getMsg());
            RsData<List<BidHistoryResponse>> rsData = new RsData<>(e.getCode(), e.getMsg(), null);
            return ResponseEntity.badRequest().body(rsData);
        } catch (Exception e) {
            log.error("[REST API] 내 입찰 내역 조회 중 예상치 못한 오류 - 오류: {}", e.getMessage(), e);
            RsData<List<BidHistoryResponse>> rsData = new RsData<>("500", "서버 오류가 발생했습니다.", null);
            return ResponseEntity.internalServerError().body(rsData);
        }
    }

    // 특정 경매에서 특정 사용자의 입찰 내역 조회 API
    @GetMapping("/{auctionId}/bids/my")
    public ResponseEntity<RsData<List<BidHistoryResponse>>> getMyBidHistoryByAuction(
            @PathVariable Long auctionId,
            @CookieValue(name = "jwt-token", required = false) String token) {
        log.info("[REST API] 내 경매별 입찰 내역 조회 요청 - 경매ID: {}", auctionId);
        
        try {
            if (token == null || !jwtProvider.validateToken(token)) {
                RsData<List<BidHistoryResponse>> rsData = new RsData<>("401", "인증이 필요합니다.", null);
                return ResponseEntity.status(401).body(rsData);
            }
            
            String userUUID = jwtProvider.parseUserUUID(token);
            if (userUUID == null) {
                RsData<List<BidHistoryResponse>> rsData = new RsData<>("401", "사용자 정보를 확인할 수 없습니다.", null);
                return ResponseEntity.status(401).body(rsData);
            }
            
            List<BidHistoryResponse> bidHistory = bidService.getBidHistoryByAuctionAndUser(auctionId, userUUID);
            RsData<List<BidHistoryResponse>> rsData = new RsData<>("200", "내 경매별 입찰 내역 조회가 완료되었습니다.", bidHistory);
            return ResponseEntity.ok(rsData);
        } catch (ServiceException e) {
            log.error("[REST API] 내 경매별 입찰 내역 조회 실패 - 경매ID: {}, 오류: {}", auctionId, e.getMsg());
            RsData<List<BidHistoryResponse>> rsData = new RsData<>(e.getCode(), e.getMsg(), null);
            return ResponseEntity.badRequest().body(rsData);
        } catch (Exception e) {
            log.error("[REST API] 내 경매별 입찰 내역 조회 중 예상치 못한 오류 - 경매ID: {}, 오류: {}", auctionId, e.getMessage(), e);
            RsData<List<BidHistoryResponse>> rsData = new RsData<>("500", "서버 오류가 발생했습니다.", null);
            return ResponseEntity.internalServerError().body(rsData);
        }
    }

    /**
     * 에러 메시지를 클라이언트에게 전송
     */
    private void sendErrorMessage(Long auctionId, String errorMessage) {
        try {
            log.warn("⚠️ [WebSocket 에러] 에러 메시지 전송: {}", errorMessage);
            
            WebSocketResponse errorRes = WebSocketResponse.builder()
                    .message("입찰 실패: " + errorMessage)
                    .localDateTime(LocalDateTime.now())
                    .nickname("System")
                    .currentBid(0)
                    .userUUID(null)  // 에러 시에는 userUUID 없음
                    .build();

            log.info("📤 [WebSocket 에러] 에러 메시지 브로드캐스트 시작");
            log.info("🎯 [WebSocket 에러] 전송 대상: /sub/auction/{}", auctionId);
            log.info("📄 [WebSocket 에러] 에러 메시지: {}", errorRes);
            
            simpMessagingTemplate.convertAndSend("/sub/auction/" + auctionId, errorRes);
            log.info("✅ [WebSocket 에러] 에러 메시지 전송 완료: /sub/auction/{}", auctionId);
        } catch (Exception e) {
            log.error("💥 [WebSocket 에러] 에러 메시지 전송 실패: {}", e.getMessage());
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
