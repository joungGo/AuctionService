package org.example.bidflow.domain.auction.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bidflow.domain.auction.dto.*;
import org.example.bidflow.domain.auction.entity.Auction;
import org.example.bidflow.domain.auction.entity.AuctionStatus;
import org.example.bidflow.domain.auction.repository.AuctionRepository;
import org.example.bidflow.domain.bid.repository.BidRepository;
import org.example.bidflow.domain.category.entity.Category;
import org.example.bidflow.domain.category.repository.CategoryRepository;
import org.example.bidflow.domain.category.service.CategoryService;
import org.example.bidflow.domain.product.repository.ProductRepository;
import org.example.bidflow.domain.user.entity.Role;
import org.example.bidflow.domain.user.service.UserService;
import org.example.bidflow.global.annotation.HasRole;
import org.example.bidflow.global.service.AuctionSchedulerService;
import org.example.bidflow.global.messaging.publisher.EventPublisher;
import org.example.bidflow.global.service.BaseService;
import org.example.bidflow.global.utils.RedisCommon;
import org.example.bidflow.global.dto.RsData;
import org.example.bidflow.global.exception.ServiceException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuctionService extends BaseService {

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository; // 임시
    private final ProductRepository productRepository; // 임시
    private final CategoryRepository categoryRepository; // 임시
    private final RedisCommon redisCommon; // 임시
    private final UserService userService;
    private final CategoryService categoryService;
    private final AuctionSchedulerService auctionSchedulerService; // 임시
    private final AuctionRedisService auctionRedisService;
    private final AuctionStatisticsService auctionStatisticsService;
    private final AuctionMaintenanceService auctionMaintenanceService;
    private final AuctionCreationService auctionCreationService;
    private final EventPublisher eventPublisher;

    // 사용자-모든 경매 목록을 조회하고 AuctionResponse DTO 리스트로 변환
    public List<AuctionCheckResponse> getAllAuctions()  {
        return getAllAuctionsByCategory(null);
    }

    // 카테고리별 경매 목록 조회
    public List<AuctionCheckResponse> getAllAuctionsByCategory(Long categoryId)  {
        // 경매 목록 조회 <AuctionRepository에서 조회>
        List<Auction> auctions;
        if (categoryId != null) {
            Category category = categoryService.getCategoryEntityById(categoryId);
            auctions = auctionRepository.findAllAuctionsByCategory(category);
        } else {
            auctions = auctionRepository.findAllAuctions();
        }
        
        if (auctions.isEmpty()) { // 리스트가 비어있을경우 예외처리
            throw new ServiceException("404", "등록된 경매가 없습니다. 새로운 경매가 등록될 때까지 기다려주세요.");
        }

        // Auction 엔티티를 AuctionCheckResponse DTO로 변환
        return auctions.stream()
                .map(auction -> {
                    Integer amount = auctionRedisService.getCurrentBidAmount(auction.getAuctionId(), auction.getStartPrice());
                    log.info("amount: {}", amount);
                    return AuctionCheckResponse.from(auction, amount);
                })
                .collect(Collectors.toList());
    }

    //관리자- 모든 경매 목록을 조회 (관리자)
    @HasRole(Role.ADMIN)
    public List<AuctionAdminResponse> getAdminAllAuctions() {
        List<Auction> auctions = auctionRepository.findAllAuctionsWithProductAndWinner();

        if (auctions == null || auctions.isEmpty()) {
            throw new ServiceException("404", "경매 목록 조회 실패");
        }

        return auctions.stream()
                .map(auction -> {
                    Integer amount = auctionRedisService.getCurrentBidAmount(auction.getAuctionId(), auction.getStartPrice());
                    log.info("amount: {}", amount);
                    return AuctionAdminResponse.from(auction, amount);
                })
                .toList();
    }

    // 경매 등록 서비스 (관리자)
    @HasRole(Role.ADMIN)
    public RsData<AuctionCreateResponse> createAuction(AuctionRequest requestDto) {
        return auctionCreationService.createAuction(requestDto);
    }

    // 외부 요청에 대한 거래 종료 기능 (보류)
    @Transactional
    public void closeAuction(Long auctionId) {
        Auction auction = auctionRepository.findByAuctionId(auctionId)
                .orElseThrow(() -> new IllegalArgumentException("진행 중인 경매를 찾을 수 없습니다."));// 최고 입찰가 찾기

        auction.setStatus(AuctionStatus.FINISHED);
    }

    // 관리자 수동 상태 변경 (UPCOMING/ONGOING/FINISHED)
    @HasRole(Role.ADMIN)
    @Transactional
    public RsData<String> updateAuctionStatus(Long auctionId, String newStatus) {
        Auction auction = auctionRepository.findByAuctionId(auctionId)
                .orElseThrow(() -> new IllegalArgumentException("경매를 찾을 수 없습니다."));

        AuctionStatus target;
        try {
            target = AuctionStatus.valueOf(newStatus);
        } catch (IllegalArgumentException e) {
            throw new ServiceException("400", "유효하지 않은 상태 값입니다. (UPCOMING|ONGOING|FINISHED)");
        }

        auction.setStatus(target);
        auctionRepository.save(auction);

        Long categoryId = auction.getProduct() != null && auction.getProduct().getCategory() != null
                ? auction.getProduct().getCategory().getCategoryId()
                : null;

        // 현재가 조회(목록 반영용)
        Integer amount = auctionRedisService.getCurrentBidAmount(auction.getAuctionId(), auction.getStartPrice());
        eventPublisher.publishAuctionStatusChange(
                auction.getAuctionId(),
                categoryId,
                target.name(),
                amount != null ? amount.longValue() : (auction.getStartPrice() != null ? auction.getStartPrice().longValue() : 0L)
        );

        return new RsData<>("200", "경매 상태가 변경되었습니다.", target.name());
    }

    // 경매 데이터 검증 후 DTO 반환
    @Transactional
    public AuctionDetailResponse getAuctionDetail(Long auctionId) {
        long startTime = startOperation("getAuctionDetail", "경매 상세 정보 조회");
        try {
            Auction auction = getAuctionWithValidation(auctionId);

            Integer amount = auctionRedisService.getCurrentBidAmount(auction.getAuctionId(), auction.getStartPrice());
            log.info("DetailCurrentAmount: {}", amount);

            AuctionDetailResponse response = AuctionDetailResponse.from(auction, amount);
            endOperation("getAuctionDetail", "경매 상세 정보 조회", startTime);
            return response;
        } catch (Exception e) {
            endOperation("getAuctionDetail", "경매 상세 정보 조회", startTime);
            throw e;
        }
    }

    // 입찰 페이지 전용 상세 정보 반환
    public AuctionBidDetailResponse getAuctionBidDetail(Long auctionId) {
        long startTime = startOperation("getAuctionBidDetail", "입찰 페이지 경매 상세 정보 조회");
        try {
            Auction auction = getAuctionWithValidation(auctionId);
            AuctionRedisService.BidInfo bidInfo = getCurrentBidInfo(auctionId, auction);
            String highestBidderNickname = getHighestBidderNickname(bidInfo.getHighestBidderUUID());
            
            AuctionBidDetailResponse response = buildAuctionBidDetailResponse(auction, bidInfo, highestBidderNickname);
            endOperation("getAuctionBidDetail", "입찰 페이지 경매 상세 정보 조회", startTime);
            return response;
        } catch (Exception e) {
            endOperation("getAuctionBidDetail", "입찰 페이지 경매 상세 정보 조회", startTime);
            throw e;
        }
    }

    // 현재 입찰 정보 조회 (AuctionRedisService 사용)
    private AuctionRedisService.BidInfo getCurrentBidInfo(Long auctionId, Auction auction) {
        return auctionRedisService.getCurrentBidInfo(auctionId, auction);
    }

    // 최고 입찰자 닉네임 조회
    private String getHighestBidderNickname(String highestBidderUUID) {
        if (highestBidderUUID == null) {
            return null;
        }
        
        try {
            return userService.getUserByUUID(highestBidderUUID).getNickname();
        } catch (Exception e) {
            log.warn("[최고 입찰자 닉네임 조회 실패] userUUID: {}, 오류: {}", highestBidderUUID, e.getMessage());
            return null;
        }
    }

    // AuctionBidDetailResponse 객체 생성
    private AuctionBidDetailResponse buildAuctionBidDetailResponse(Auction auction, AuctionRedisService.BidInfo bidInfo, String highestBidderNickname) {
        return AuctionBidDetailResponse.builder()
            .auctionId(auction.getAuctionId())
            .productName(auction.getProduct().getProductName())
            .imageUrl(auction.getProduct().getImageUrl())
            .description(auction.getProduct().getDescription())
            .startPrice(auction.getStartPrice())
            .currentBid(bidInfo.getCurrentBid())
            .minBid(auction.getMinBid())
            .status(auction.getStatus().toString())
            .startTime(auction.getStartTime())
            .endTime(auction.getEndTime())
            .highestBidderNickname(highestBidderNickname)
            .highestBidderUUID(bidInfo.getHighestBidderUUID())
            .categoryId(auction.getProduct().getCategory() != null ? auction.getProduct().getCategory().getCategoryId() : null)
            .categoryName(auction.getProduct().getCategory() != null ? auction.getProduct().getCategory().getCategoryName() : null)
            .build();
    }


    // 경매 조회 및 상태 검증 메서드
    public Auction getAuctionWithValidation(Long auctionId) {
        // 경매 조회
        Auction auction = findByIdOrThrow(auctionRepository, auctionId, "경매");

        // 경매 상태 검증
        if (!auction.getStatus().equals(AuctionStatus.ONGOING)) {
            throw new ServiceException("400-2", "진행 중인 경매가 아닙니다.");
        }

        return auction;
    }

    // 기존 경매들에 기본 카테고리 할당 (한 번만 실행)
    public void assignDefaultCategoryToExistingAuctions() {
        auctionMaintenanceService.assignDefaultCategoryToExistingAuctions();
    }

    // 경매 통계 조회
    public AuctionStatisticsResponse getAuctionStatistics() {
        long startTime = startOperation("getAuctionStatistics", "경매 통계 조회");
        try {
            AuctionStatisticsResponse response = auctionStatisticsService.getAuctionStatistics();
            endOperation("getAuctionStatistics", "경매 통계 조회", startTime);
            return response;
        } catch (Exception e) {
            endOperation("getAuctionStatistics", "경매 통계 조회", startTime);
            throw e;
        }
    }

}