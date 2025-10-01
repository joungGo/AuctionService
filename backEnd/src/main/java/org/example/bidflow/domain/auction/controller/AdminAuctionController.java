package org.example.bidflow.domain.auction.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.bidflow.domain.auction.dto.AuctionAdminResponse;
import org.example.bidflow.domain.auction.dto.AuctionCreateResponse;
import org.example.bidflow.domain.auction.dto.AuctionRequest;
import org.example.bidflow.domain.auction.dto.AuctionStatisticsResponse;
import org.example.bidflow.domain.auction.dto.AuctionStatusUpdateRequest;
import org.example.bidflow.domain.auction.service.AuctionService;
import org.example.bidflow.global.controller.BaseController;
import org.example.bidflow.global.dto.RsData;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/auctions")
@RequiredArgsConstructor
public class AdminAuctionController extends BaseController {
    private final AuctionService auctionService;

    // 경매 등록 (관리자)
    @PostMapping
    public ResponseEntity<RsData<AuctionCreateResponse>> createAuction(@Valid @RequestBody AuctionRequest requestDto) {
        RsData<AuctionCreateResponse> response = auctionService.createAuction(requestDto);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    // 전체 경매 목록 조회 (관리자)
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

    // 경매 통계 조회
    @GetMapping("/statistics")
    public ResponseEntity<RsData<AuctionStatisticsResponse>> getAuctionStatistics() {
        AuctionStatisticsResponse statistics = auctionService.getAuctionStatistics();
        RsData<AuctionStatisticsResponse> rsData = new RsData<>("200", "경매 통계 조회가 완료되었습니다.", statistics);
        return ResponseEntity.ok(rsData);
    }

    // 관리자 수동 상태 변경(UPCOMING/ONGOING/FINISHED)
    @PatchMapping("/{auctionId}/status")
    public ResponseEntity<RsData<String>> updateAuctionStatus(
            @PathVariable Long auctionId,
            @Valid @RequestBody AuctionStatusUpdateRequest request
    ) {
        RsData<String> rs = auctionService.updateAuctionStatus(auctionId, request.getStatus());
        return ResponseEntity.status(rs.getStatusCode()).body(rs);
    }
}
