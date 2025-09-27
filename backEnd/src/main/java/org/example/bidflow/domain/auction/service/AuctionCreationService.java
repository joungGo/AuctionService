package org.example.bidflow.domain.auction.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bidflow.domain.auction.dto.AuctionCreateResponse;
import org.example.bidflow.domain.auction.dto.AuctionRequest;
import org.example.bidflow.domain.auction.entity.Auction;
import org.example.bidflow.domain.auction.entity.AuctionStatus;
import org.example.bidflow.domain.auction.repository.AuctionRepository;
import org.example.bidflow.domain.category.entity.Category;
import org.example.bidflow.domain.category.service.CategoryService;
import org.example.bidflow.domain.product.entity.Product;
import org.example.bidflow.domain.product.repository.ProductRepository;
import org.example.bidflow.global.dto.RsData;
import org.example.bidflow.global.service.AuctionSchedulerService;
import org.example.bidflow.global.utils.RedisCommon;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 경매 생성 관련 비즈니스 로직을 담당하는 서비스
 * 
 * 이 서비스는 경매 등록과 관련된 모든 작업을 담당합니다.
 * 상품 생성, 경매 생성, Redis 설정, 스케줄 등록 등을 수행합니다.
 * 
 * @author AuctionService Team
 * @version 1.0
 * @since 1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuctionCreationService {

    private final AuctionRepository auctionRepository;
    private final ProductRepository productRepository;
    private final CategoryService categoryService;
    private final RedisCommon redisCommon;
    private final AuctionSchedulerService auctionSchedulerService;
    private final AuctionRedisService auctionRedisService;

    /**
     * 경매를 생성합니다.
     * 
     * @param requestDto 경매 생성 요청 DTO
     * @return 생성된 경매 정보
     */
    @Transactional
    public RsData<AuctionCreateResponse> createAuction(AuctionRequest requestDto) {
        // 카테고리 조회
        Category category = null;
        if (requestDto.getCategoryId() != null) {
            category = categoryService.getCategoryEntityById(requestDto.getCategoryId());
        }
        
        // 상품 정보 저장
        Product product = createProduct(requestDto, category);
        
        // 경매 정보 저장
        Auction auction = createAuctionEntity(requestDto, product);
        
        // Redis 설정
        setupAuctionRedis(auction);
        
        // 경매 스케줄 등록
        auctionSchedulerService.scheduleAuction(auction);

        // 성공 응답 반환
        return new RsData<>("201", "경매가 등록되었습니다.", AuctionCreateResponse.from(auction));
    }

    /**
     * 상품 엔티티를 생성합니다.
     */
    private Product createProduct(AuctionRequest requestDto, Category category) {
        Product product = Product.builder()
                .productName(requestDto.getProductName())
                .imageUrl(requestDto.getImageUrl())
                .description(requestDto.getDescription())
                .category(category)
                .build();
        return productRepository.save(product);
    }

    /**
     * 경매 엔티티를 생성합니다.
     */
    private Auction createAuctionEntity(AuctionRequest requestDto, Product product) {
        Auction auction = Auction.builder()
                .product(product)
                .startPrice(requestDto.getStartPrice())
                .minBid(requestDto.getMinBid())
                .startTime(requestDto.getStartTime())
                .endTime(requestDto.getEndTime())
                .status(AuctionStatus.UPCOMING)
                .build();
        return auctionRepository.save(auction);
    }

    /**
     * 경매 Redis 설정을 수행합니다.
     */
    private void setupAuctionRedis(Auction auction) {
        String hashKey = auctionRedisService.getAuctionRedisKey(auction.getAuctionId());
        redisCommon.putInHash(hashKey, "amount", auction.getStartPrice());

        LocalDateTime expireTime = auction.getEndTime().plusMinutes(2); // 경매 종료 후 2분 여유시간
        redisCommon.setExpireAt(hashKey, expireTime);
    }
}
