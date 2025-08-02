package org.example.bidflow.domain.user.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder
public class FavoriteResponse {
    private Long favoriteId;
    private Long auctionId;
    private String productName;
    private String imageUrl;
    private LocalDateTime createdAt;
} 