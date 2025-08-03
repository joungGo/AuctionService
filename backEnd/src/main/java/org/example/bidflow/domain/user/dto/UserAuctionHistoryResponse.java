package org.example.bidflow.domain.user.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder
public class UserAuctionHistoryResponse {
    private Long auctionId;
    private String productName;
    private String imageUrl;
    private String status; // 진행중, 낙찰, 패찰
    private Integer myLastBidAmount;
    private Integer myHighestBidAmount;
    private Integer auctionHighestBidAmount;
    private LocalDateTime endTime;
    private Boolean isWinner;
} 