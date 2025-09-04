package org.example.bidflow.domain.auction.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.bidflow.domain.auction.dto.AuctionAdminResponse;
import org.example.bidflow.domain.auction.dto.AuctionCreateResponse;
import org.example.bidflow.domain.auction.dto.AuctionRequest;
import org.example.bidflow.domain.auction.service.AuctionService;
import org.example.bidflow.global.dto.RsData;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.example.bidflow.global.annotation.RateLimit;

import java.util.List;
import java.time.temporal.ChronoUnit;

@RestController
@RequestMapping("/api/admin/auctions")
@RequiredArgsConstructor
public class AdminAuctionController {
    private final AuctionService auctionService;

    // 경매 등록 컨트롤러
    @PostMapping
    @RateLimit(requests = 10, window = 1, unit = ChronoUnit.MINUTES,
               keyType = RateLimit.KeyType.USER_ONLY,
               message = "경매 등록 요청이 너무 많습니다. 1분 후 다시 시도해주세요.")
    public ResponseEntity<RsData<AuctionCreateResponse>> createAuction(@Valid @RequestBody AuctionRequest requestDto) {
        RsData<AuctionCreateResponse> response = auctionService.createAuction(requestDto);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    // 전체 경매 상품 리스트 조회 컨트롤러
    @GetMapping
    public ResponseEntity<RsData<List<AuctionAdminResponse>>> getAllAuctions() {
        // AuctionService에서 AuctionResponse 리스트를 반환
        List<AuctionAdminResponse> response = auctionService.getAdminAllAuctions();
        RsData<List<AuctionAdminResponse>> rsData = new RsData<>("200", "전체 조회가 완료되었습니다.", response);
        return ResponseEntity.ok(rsData);
    }

    // 기존 경매들에 기본 카테고리 할당 (한 번만 실행)
    @PostMapping("/assign-default-category")
    public ResponseEntity<RsData<String>> assignDefaultCategoryToExistingAuctions() {
        auctionService.assignDefaultCategoryToExistingAuctions();
        return ResponseEntity.ok(new RsData<>("200", "기존 경매들에 기본 카테고리 할당이 완료되었습니다.", "success"));
    }
}
