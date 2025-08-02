package org.example.bidflow.domain.bid.dto.model.response;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.example.bidflow.domain.bid.entity.Bid;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BidHistoryResponse {
    private Long bidId;
    private Long auctionId;
    private String productName;
    private String bidderNickname;
    private String bidderUUID;
    private Integer bidAmount;
    private LocalDateTime bidTime;
    private Boolean isHighestBid; // 현재 최고 입찰인지 여부
    
    public static BidHistoryResponse from(Bid bid, Boolean isHighestBid) {
        return BidHistoryResponse.builder()
                .bidId(bid.getBidId())
                .auctionId(bid.getAuction().getAuctionId())
                .productName(bid.getAuction().getProduct().getProductName())
                .bidderNickname(bid.getUser().getNickname())
                .bidderUUID(bid.getUser().getUserUUID())
                .bidAmount(bid.getAmount())
                .bidTime(bid.getBidTime())
                .isHighestBid(isHighestBid)
                .build();
    }
} 