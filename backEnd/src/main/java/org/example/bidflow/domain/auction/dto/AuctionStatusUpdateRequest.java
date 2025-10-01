package org.example.bidflow.domain.auction.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AuctionStatusUpdateRequest {
    @NotBlank
    private String status; // UPCOMING | ONGOING | FINISHED
}


