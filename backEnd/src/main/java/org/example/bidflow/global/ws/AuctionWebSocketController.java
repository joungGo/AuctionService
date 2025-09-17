package org.example.bidflow.global.ws;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

import java.util.Map;

/**
 * 경매 관련 WebSocket STOMP 컨트롤러
 * - 구독 등록(메인/카테고리/상세)
 * - 하트비트(ping/pong)
 * - 브로드캐스트 헬퍼(NEW_AUCTION, AUCTION_STATUS_CHANGE)
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class AuctionWebSocketController {

	private final SimpMessagingTemplate messagingTemplate;
	private final WebSocketSessionManager sessionManager;

	/** 메인 페이지 구독 등록 */
	@MessageMapping("/subscribe/main")
	public void subscribeMain(SimpMessageHeaderAccessor headers) {
		String sessionId = headers.getSessionId();
		sessionManager.subscribeToMainPage(sessionId);
		messagingTemplate.convertAndSendToUser(
				sessionId,
				"/queue/subscription-confirmed",
				WebSocketMessage.create("SUBSCRIPTION_CONFIRMED", Map.of("page", "main"))
		);
		log.info("[WS 구독] 메인 페이지 - 세션: {}", sessionId);
	}

	/** 카테고리 페이지 구독 등록 */
	@MessageMapping("/subscribe/category")
	public void subscribeCategory(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headers) {
		String sessionId = headers.getSessionId();
		Long categoryId = asLong(payload.get("categoryId"));
		sessionManager.subscribeToCategoryPage(sessionId, categoryId);
		messagingTemplate.convertAndSendToUser(
				sessionId,
				"/queue/subscription-confirmed",
				WebSocketMessage.create("SUBSCRIPTION_CONFIRMED", Map.of("page", "category", "categoryId", categoryId))
		);
		log.info("[WS 구독] 카테고리 페이지 - 세션: {}, 카테고리: {}", sessionId, categoryId);
	}

	/** 상세 페이지 구독 등록 */
	@MessageMapping("/subscribe/auction")
	public void subscribeAuction(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headers) {
		String sessionId = headers.getSessionId();
		Long auctionId = asLong(payload.get("auctionId"));
		sessionManager.subscribeToAuctionDetail(sessionId, auctionId);
		messagingTemplate.convertAndSendToUser(
				sessionId,
				"/queue/subscription-confirmed",
				WebSocketMessage.create("SUBSCRIPTION_CONFIRMED", Map.of("page", "auction-detail", "auctionId", auctionId))
		);
		log.info("[WS 구독] 상세 페이지 - 세션: {}, 경매: {}", sessionId, auctionId);
	}

	/** 하트비트: ping 수신 → PONG 브로드캐스트 */
	@MessageMapping("/ping")
	@SendTo("/topic/pong")
	public WebSocketMessage<String> ping() {
		return WebSocketMessage.create("PONG", "pong");
	}

	/** NEW_AUCTION 브로드캐스트 (타겟팅) */
	public void broadcastNewAuction(AuctionStatusChangeData data) {
		WebSocketMessage<AuctionStatusChangeData> msg = WebSocketMessage.create("NEW_AUCTION", data);
		sessionManager.getSessionsForNewAuction().forEach(sessionId ->
				messagingTemplate.convertAndSendToUser(sessionId, "/queue/auction-updates", msg)
		);
		log.info("[WS 송신] NEW_AUCTION - auctionId: {} 대상: {}명", data.getAuctionId(), sessionManager.getActiveSessionIds().size());
	}

	/** AUCTION_STATUS_CHANGE 브로드캐스트 (타겟팅) */
	public void broadcastAuctionStatusChange(AuctionStatusChangeData data) {
		WebSocketMessage<AuctionStatusChangeData> msg = WebSocketMessage.create("AUCTION_STATUS_CHANGE", data);
		sessionManager.getSessionsForStatusChange(data.getAuctionId(), data.getCategoryId()).forEach(sessionId ->
				messagingTemplate.convertAndSendToUser(sessionId, "/queue/auction-updates", msg)
		);
		log.info("[WS 송신] AUCTION_STATUS_CHANGE - auctionId: {}, {} -> {}", data.getAuctionId(), data.getOldStatus(), data.getNewStatus());
	}

	/** Object → Long 변환 유틸 */
	private Long asLong(Object obj) {
		if (obj == null) return null;
		if (obj instanceof Long) return (Long) obj;
		if (obj instanceof Number) return ((Number) obj).longValue();
		try { return Long.parseLong(obj.toString()); } catch (NumberFormatException e) { return null; }
	}
}
