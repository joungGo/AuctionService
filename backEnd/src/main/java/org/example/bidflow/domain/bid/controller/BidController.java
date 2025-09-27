package org.example.bidflow.domain.bid.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bidflow.domain.bid.dto.model.response.BidHistoryResponse;
import org.example.bidflow.domain.bid.service.BidService;
import org.example.bidflow.global.controller.BaseController;
import org.example.bidflow.global.dto.RsData;
import org.example.bidflow.global.utils.CookieUtil;
import org.example.bidflow.global.utils.JwtProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.example.bidflow.global.exception.ServiceException;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auctions")
public class BidController extends BaseController {

    private final BidService bidService;
    private final CookieUtil cookieUtil;
    private final JwtProvider jwtProvider;


    // 특정 경매의 입찰 내역 조회 API
    @GetMapping("/{auctionId}/bids")
    public ResponseEntity<RsData<List<BidHistoryResponse>>> getBidHistoryByAuction(@PathVariable Long auctionId) {
        long startTime = startOperation("getBidHistoryByAuction", "경매 입찰 내역 조회");
        try {
            List<BidHistoryResponse> bidHistory = bidService.getBidHistoryByAuction(auctionId);
            endOperation("getBidHistoryByAuction", "경매 입찰 내역 조회", startTime);
            return successResponse("입찰 내역 조회가 완료되었습니다.", bidHistory);
        } catch (ServiceException e) {
            endOperation("getBidHistoryByAuction", "경매 입찰 내역 조회", startTime);
            return errorResponse(e.getCode(), e.getMsg());
        } catch (Exception e) {
            endOperation("getBidHistoryByAuction", "경매 입찰 내역 조회", startTime);
            return serverErrorResponse("서버 오류가 발생했습니다.");
        }
    }

    // 특정 경매의 입찰 내역 페이징 조회 API
    @GetMapping("/{auctionId}/bids/paging")
    public ResponseEntity<RsData<Page<BidHistoryResponse>>> getBidHistoryByAuctionWithPaging(
            @PathVariable Long auctionId,
            @PageableDefault(size = 20, sort = "bidTime") Pageable pageable) {
        long startTime = startOperation("getBidHistoryByAuctionWithPaging", "경매 입찰 내역 페이징 조회");
        try {
            Page<BidHistoryResponse> bidHistoryPage = bidService.getBidHistoryByAuctionWithPaging(auctionId, pageable);
            endOperation("getBidHistoryByAuctionWithPaging", "경매 입찰 내역 페이징 조회", startTime);
            return successResponse("입찰 내역 페이징 조회가 완료되었습니다.", bidHistoryPage);
        } catch (ServiceException e) {
            endOperation("getBidHistoryByAuctionWithPaging", "경매 입찰 내역 페이징 조회", startTime);
            return errorResponse(e.getCode(), e.getMsg());
        } catch (Exception e) {
            endOperation("getBidHistoryByAuctionWithPaging", "경매 입찰 내역 페이징 조회", startTime);
            return serverErrorResponse("서버 오류가 발생했습니다.");
        }
    }

    // 특정 사용자의 입찰 내역 조회 API
    @GetMapping("/bids/my")
    public ResponseEntity<RsData<List<BidHistoryResponse>>> getMyBidHistory(@CookieValue(name = "jwt-token", required = false) String token) {
        long startTime = startOperation("getMyBidHistory", "내 입찰 내역 조회");
        try {
            String userUUID = validateTokenAndGetUserUUID(token);
            
            List<BidHistoryResponse> bidHistory = bidService.getBidHistoryByUser(userUUID);
            endOperation("getMyBidHistory", "내 입찰 내역 조회", startTime);
            return successResponse("내 입찰 내역 조회가 완료되었습니다.", bidHistory);
        } catch (ServiceException e) {
            endOperation("getMyBidHistory", "내 입찰 내역 조회", startTime);
            return errorResponse(e.getCode(), e.getMsg());
        } catch (Exception e) {
            endOperation("getMyBidHistory", "내 입찰 내역 조회", startTime);
            return serverErrorResponse("서버 오류가 발생했습니다.");
        }
    }

    // 특정 경매에서 특정 사용자의 입찰 내역 조회 API
    @GetMapping("/{auctionId}/bids/my")
    public ResponseEntity<RsData<List<BidHistoryResponse>>> getMyBidHistoryByAuction(
            @PathVariable Long auctionId,
            @CookieValue(name = "jwt-token", required = false) String token) {
        long startTime = startOperation("getMyBidHistoryByAuction", "내 경매별 입찰 내역 조회");
        try {
            String userUUID = validateTokenAndGetUserUUID(token);
            
            List<BidHistoryResponse> bidHistory = bidService.getBidHistoryByAuctionAndUser(auctionId, userUUID);
            endOperation("getMyBidHistoryByAuction", "내 경매별 입찰 내역 조회", startTime);
            return successResponse("내 경매별 입찰 내역 조회가 완료되었습니다.", bidHistory);
        } catch (ServiceException e) {
            endOperation("getMyBidHistoryByAuction", "내 경매별 입찰 내역 조회", startTime);
            return errorResponse(e.getCode(), e.getMsg());
        } catch (Exception e) {
            endOperation("getMyBidHistoryByAuction", "내 경매별 입찰 내역 조회", startTime);
            return serverErrorResponse("서버 오류가 발생했습니다.");
        }
    }



    // JWT 토큰 검증 및 사용자 UUID 추출 공통 메서드
    private String validateTokenAndGetUserUUID(String token) {
        if (token == null || !jwtProvider.validateToken(token)) {
            throw new ServiceException("401", "인증이 필요합니다.");
        }
        
        String userUUID = jwtProvider.parseUserUUID(token);
        if (userUUID == null) {
            throw new ServiceException("401", "사용자 정보를 확인할 수 없습니다.");
        }
        
        return userUUID;
    }
}
