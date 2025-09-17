package org.example.bidflow.global.ws;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 페이지 기반(Web) 구독 정보를 관리하는 세션 관리자 (인메모리)
 * - 메인 페이지: new-auctions, all-status-changes
 * - 카테고리 페이지: new-auctions, category-status-changes (categoryId 포함)
 * - 상세 페이지: specific-auction-changes (auctionId 포함)
 */
@Slf4j
@Component
public class WebSocketSessionManager {

	/** 세션ID -> 구독 정보 매핑 (인메모리) */
	private final Map<String, SessionSubscription> sessionSubscriptions = new ConcurrentHashMap<>();

	@Data
	@Builder
	public static class SessionSubscription {
		private String sessionId;                 // 세션 ID
		private String currentPage;               // 현재 페이지: main | category | auction-detail
		private Set<String> subscribedTopics;     // 구독 중인 토픽 집합
		private Map<String, Object> pageContext;  // 페이지 컨텍스트 (categoryId, auctionId 등)
		private Instant subscribedAt;             // 구독 시간
		private Instant lastActivity;             // 마지막 활동 시간
	}

	/** 메인 페이지 구독 등록 */
	public void subscribeToMainPage(String sessionId) {
		SessionSubscription sub = SessionSubscription.builder()
				.sessionId(sessionId)
				.currentPage("main")
				.subscribedTopics(new HashSet<>())
				.pageContext(new HashMap<>())
				.subscribedAt(Instant.now())
				.lastActivity(Instant.now())
				.build();
		sub.getSubscribedTopics().add("new-auctions");
		sub.getSubscribedTopics().add("all-status-changes");
		sessionSubscriptions.put(sessionId, sub);
		log.info("[WS 세션] 메인 페이지 구독 - 세션: {}", sessionId);
	}

	/** 카테고리 페이지 구독 등록 */
	public void subscribeToCategoryPage(String sessionId, Long categoryId) {
		SessionSubscription sub = SessionSubscription.builder()
				.sessionId(sessionId)
				.currentPage("category")
				.subscribedTopics(new HashSet<>())
				.pageContext(new HashMap<>())
				.subscribedAt(Instant.now())
				.lastActivity(Instant.now())
				.build();
		sub.getSubscribedTopics().add("new-auctions");
		sub.getSubscribedTopics().add("category-status-changes");
		sub.getPageContext().put("categoryId", categoryId);
		sessionSubscriptions.put(sessionId, sub);
		log.info("[WS 세션] 카테고리 페이지 구독 - 세션: {}, 카테고리: {}", sessionId, categoryId);
	}

	/** 경매 상세 페이지 구독 등록 */
	public void subscribeToAuctionDetail(String sessionId, Long auctionId) {
		SessionSubscription sub = SessionSubscription.builder()
				.sessionId(sessionId)
				.currentPage("auction-detail")
				.subscribedTopics(new HashSet<>())
				.pageContext(new HashMap<>())
				.subscribedAt(Instant.now())
				.lastActivity(Instant.now())
				.build();
		sub.getSubscribedTopics().add("specific-auction-changes");
		sub.getPageContext().put("auctionId", auctionId);
		sessionSubscriptions.put(sessionId, sub);
		log.info("[WS 세션] 상세 페이지 구독 - 세션: {}, 경매: {}", sessionId, auctionId);
	}

	/** 세션 구독 해제 */
	public void removeSubscription(String sessionId) {
		sessionSubscriptions.remove(sessionId);
		log.info("[WS 세션] 구독 해제 - 세션: {}", sessionId);
	}

	/** NEW_AUCTION 대상: 메인/카테고리 구독자 전체 반환 */
	public Set<String> getSessionsForNewAuction() {
		return sessionSubscriptions.values().stream()
				.filter(sub -> sub.getSubscribedTopics().contains("new-auctions"))
				.map(SessionSubscription::getSessionId)
				.collect(Collectors.toSet());
	}

	/** AUCTION_STATUS_CHANGE 대상: all-status 또는 일치 카테고리 또는 일치 경매 구독자 */
	public Set<String> getSessionsForStatusChange(Long auctionId, Long categoryId) {
		return sessionSubscriptions.values().stream()
				.filter(sub ->
					sub.getSubscribedTopics().contains("all-status-changes") ||
					(
						sub.getSubscribedTopics().contains("category-status-changes") &&
						categoryId != null && categoryId.equals(asLong(sub.getPageContext().get("categoryId")))
					) ||
					(
						sub.getSubscribedTopics().contains("specific-auction-changes") &&
						auctionId != null && auctionId.equals(asLong(sub.getPageContext().get("auctionId")))
					)
				)
				.map(SessionSubscription::getSessionId)
				.collect(Collectors.toSet());
	}

	/** 활성 세션 통계 (페이지별 개수) */
	public Map<String, Long> getSessionStats() {
		Map<String, Long> stats = sessionSubscriptions.values().stream()
				.collect(Collectors.groupingBy(SessionSubscription::getCurrentPage, Collectors.counting()));
		stats.put("total", (long) sessionSubscriptions.size());
		return stats;
	}

	/** 모든 세션 ID 조회 (진단용) */
	public Set<String> getActiveSessionIds() {
		return Collections.unmodifiableSet(sessionSubscriptions.keySet());
	}

	/** Long 캐스팅 유틸 (null 안전) */
	private Long asLong(Object obj) {
		if (obj == null) return null;
		if (obj instanceof Long) return (Long) obj;
		if (obj instanceof Number) return ((Number) obj).longValue();
		try { return Long.parseLong(obj.toString()); } catch (NumberFormatException e) { return null; }
	}
}
