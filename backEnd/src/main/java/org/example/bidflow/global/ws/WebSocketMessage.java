package org.example.bidflow.global.ws;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 서버 -> 클라이언트 메시지를 위한 표준화된 WebSocket DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketMessage<T> {
	private String id;            // UUID string, unique per emission
	private String type;          // e.g., NEW_AUCTION, AUCTION_STATUS_CHANGE, SUBSCRIPTION_CONFIRMED, PONG
	private Instant timestamp;    // server-side UTC timestamp
	private T data;               // payload
	private Map<String, Object> meta; // routing hints (optional)

	public static <T> WebSocketMessage<T> create(String type, T data) {
		return WebSocketMessage.<T>builder()
				.id(UUID.randomUUID().toString())
				.type(type)
				.timestamp(Instant.now())
				.data(data)
				.meta(new HashMap<>())
				.build();
	}
}
