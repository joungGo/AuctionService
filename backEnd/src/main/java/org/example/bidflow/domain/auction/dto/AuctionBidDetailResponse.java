package org.example.bidflow.domain.auction.dto;

import lombok.Builder;
import lombok.Getter;
import org.example.bidflow.domain.product.dto.ProductResponse;

import java.time.LocalDateTime;

@Getter
@Builder
public class AuctionBidDetailResponse {
    private Long auctionId;
    private String productName;
    private String imageUrl;
    private String description;
    private Integer startPrice;
    private Integer currentBid;
    private Integer minBid;
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String highestBidderNickname;
    private String highestBidderUUID;
    private Long categoryId;
    private String categoryName;
} 