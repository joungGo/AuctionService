package org.example.bidflow.global.ws;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 경매 상태 변경 및 새로운 경매 알림을 위한 페이로드.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuctionStatusChangeData {
	private Long auctionId;
	private String productName;
	private String oldStatus; // e.g., UPCOMING, ONGOING, FINISHED (or NONE for new auction)
	private String newStatus; // e.g., NEW_AUCTION 대상 상태 또는 변경된 상태 필드
	private Long categoryId;
	private LocalDateTime eventTime;
	private Integer startPrice;
	private LocalDateTime endTime;
	private Map<String, Object> additionalInfo;
}
