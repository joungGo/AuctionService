package org.example.bidflow.domain.bid.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bidflow.domain.auction.dto.AuctionBidRequest;
import org.example.bidflow.domain.auction.entity.Auction;
import org.example.bidflow.domain.auction.entity.AuctionStatus;
import org.example.bidflow.domain.auction.service.AuctionService;
import org.example.bidflow.domain.bid.dto.model.response.BidCreateResponse;
import org.example.bidflow.domain.bid.dto.model.response.BidHistoryResponse;
import org.example.bidflow.domain.bid.entity.Bid;
import org.example.bidflow.domain.bid.repository.BidRepository;
import org.example.bidflow.domain.user.entity.User;
import org.example.bidflow.domain.user.service.UserService;
import org.example.bidflow.global.service.BaseService;
import org.example.bidflow.global.utils.RedisCommon;
import org.example.bidflow.global.exception.ServiceException;
import org.example.bidflow.global.messaging.publisher.EventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BidService extends BaseService {

    private final AuctionService auctionService;
    private final UserService userService;
    private final BidRepository bidRepository;
    private final RedisCommon redisCommon;
    private final EventPublisher eventPublisher;

    @Transactional
    public BidCreateResponse createBid(Long auctionId, AuctionBidRequest request, String userUUID) {
        long startTime = startOperation("createBid", "경매 입찰 처리");
        try {
            // 기본 정보 조회 및 검증
            BidContext context = prepareBidContext(auctionId, request, userUUID);
            
            // 입찰 검증
            validateBidRequest(context);
            
            // Redis 업데이트
            updateRedisBidInfo(context);
            
            // DB 저장
            Bid bid = saveBidToDatabase(context);
            
            // 입찰 업데이트 이벤트 발행 (해당 경매 구독자에게만)
            eventPublisher.publishBidUpdate(
                context.getAuctionId(),
                (long) context.getRequest().getAmount(),
                context.getUser().getNickname(),
                context.getUserUUID()
            );
            
            BidCreateResponse response = BidCreateResponse.from(bid);
            endOperation("createBid", "경매 입찰 처리", startTime);
            return response;
            
        } catch (ServiceException e) {
            endOperation("createBid", "경매 입찰 처리", startTime);
            log.error("[입찰 실패] 비즈니스 규칙 위반 - 경매ID: {}, 입찰금액: {}, 오류: {}", 
                    auctionId, request.getAmount(), e.getMsg());
            throw e;
        } catch (Exception e) {
            endOperation("createBid", "경매 입찰 처리", startTime);
            log.error("[입찰 오류] 예상치 못한 오류 발생 - 경매ID: {}, 입찰금액: {}", 
                    auctionId, request.getAmount(), e);
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR.toString(), "입찰 처리 중 오류가 발생했습니다.");
        }
    }

    /**
     * 입찰 처리에 필요한 기본 정보를 조회하고 검증합니다.
     */
    private BidContext prepareBidContext(Long auctionId, AuctionBidRequest request, String userUUID) {
        User user = userService.getUserByUUID(userUUID);
        Auction auction = auctionService.getAuctionWithValidation(auctionId);
        
        // 경매 시간 검증
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        validateAuctionTime(now, auction);

        return new BidContext(auctionId, request, userUUID, user, auction, now);
    }

    /**
     * 입찰 요청을 검증합니다.
     */
    private void validateBidRequest(BidContext context) {
        // Redis에서 현재 최고가 조회
        String hashKey = "auction:" + context.getAuctionId();
        Integer amount = redisCommon.getFromHash(hashKey, "amount", Integer.class);
        String highestUserUUID = redisCommon.getFromHash(hashKey, "userUUID", String.class);
        
        int currentBidAmount = (amount != null) ? amount : context.getAuction().getStartPrice();
        context.setCurrentBidAmount(currentBidAmount);
        context.setHighestUserUUID(highestUserUUID);

        // 동일 사용자 연속 입찰 검증
        if (context.getUserUUID().equals(highestUserUUID)) {
            log.warn("[입찰 실패] 동일 사용자 연속 입찰 시도 - userUUID: {}, 경매ID: {}", 
                    context.getUserUUID(), context.getAuctionId());
            throw new ServiceException(HttpStatus.BAD_REQUEST.toString(), 
                    "이미 최고 입찰자입니다. 다른 사용자의 입찰을 기다려주세요.");
        }

        // 최소 입찰 단위 검증
        validateBidAmount(context.getRequest().getAmount(), currentBidAmount, context.getAuction().getMinBid());
    }

    /**
     * Redis에 입찰 정보를 업데이트합니다.
     */
    private void updateRedisBidInfo(BidContext context) {
        String hashKey = "auction:" + context.getAuctionId();
        redisCommon.putInHash(hashKey, "amount", context.getRequest().getAmount());
        redisCommon.putInHash(hashKey, "userUUID", context.getUserUUID());
        
        log.info("[입찰 갱신] Redis 최고가 갱신 완료 - 경매ID: {}, 이전가격: {}, 새가격: {}, 입찰자: {}", 
                context.getAuctionId(), context.getCurrentBidAmount(), 
                context.getRequest().getAmount(), context.getUser().getNickname());
    }

    /**
     * DB에 입찰 정보를 저장합니다.
     */
    private Bid saveBidToDatabase(BidContext context) {
        Bid bid = Bid.createBid(context.getAuction(), context.getUser(), 
                context.getRequest().getAmount(), context.getNow());
        bidRepository.save(bid);
        
        log.info("[입찰 성공] 입찰 처리 완료 - 경매ID: {}, 입찰자: {}, 금액: {}", 
                context.getAuctionId(), context.getUser().getNickname(), context.getRequest().getAmount());
        
        return bid;
    }

    /**
     * 입찰 처리에 필요한 컨텍스트 정보를 담는 내부 클래스
     */
    private static class BidContext {
        private final Long auctionId;
        private final AuctionBidRequest request;
        private final String userUUID;
        private final User user;
        private final Auction auction;
        private final LocalDateTime now;
        private int currentBidAmount;
        private String highestUserUUID;

        public BidContext(Long auctionId, AuctionBidRequest request, String userUUID, 
                         User user, Auction auction, LocalDateTime now) {
            this.auctionId = auctionId;
            this.request = request;
            this.userUUID = userUUID;
            this.user = user;
            this.auction = auction;
            this.now = now;
        }

        // Getters and Setters
        public Long getAuctionId() { return auctionId; }
        public AuctionBidRequest getRequest() { return request; }
        public String getUserUUID() { return userUUID; }
        public User getUser() { return user; }
        public Auction getAuction() { return auction; }
        public LocalDateTime getNow() { return now; }
        public int getCurrentBidAmount() { return currentBidAmount; }
        public void setCurrentBidAmount(int currentBidAmount) { this.currentBidAmount = currentBidAmount; }
        public String getHighestUserUUID() { return highestUserUUID; }
        public void setHighestUserUUID(String highestUserUUID) { this.highestUserUUID = highestUserUUID; }
    }

    // 특정 경매의 입찰 내역 조회
    @Transactional(readOnly = true)
    public List<BidHistoryResponse> getBidHistoryByAuction(Long auctionId) {
        long startTime = startOperation("getBidHistoryByAuction", "경매 입찰 내역 조회");
        try {
            Auction auction = auctionService.getAuctionWithValidation(auctionId);
            List<Bid> bids = bidRepository.findByAuctionOrderByBidTimeDesc(auction);
            
            Integer currentHighestAmount = getCurrentHighestAmount(auctionId, auction);
            
            List<BidHistoryResponse> responses = bids.stream()
                    .map(bid -> {
                        Boolean isHighestBid = bid.getAmount().equals(currentHighestAmount);
                        return BidHistoryResponse.from(bid, isHighestBid);
                    })
                    .collect(Collectors.toList());
            
            endOperation("getBidHistoryByAuction", "경매 입찰 내역 조회", startTime);
            log.info("[입찰 내역 조회] 경매 입찰 내역 조회 완료 - 경매ID: {}, 입찰 개수: {}", 
                    auctionId, responses.size());
            
            return responses;
            
        } catch (Exception e) {
            endOperation("getBidHistoryByAuction", "경매 입찰 내역 조회", startTime);
            log.error("[입찰 내역 조회 실패] 경매ID: {}, 오류: {}", auctionId, e.getMessage(), e);
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR.toString(), "입찰 내역 조회 중 오류가 발생했습니다.");
        }
    }

    /**
     * 현재 최고가를 조회합니다. (Redis 우선, DB 폴백)
     */
    private Integer getCurrentHighestAmount(Long auctionId, Auction auction) {
        String hashKey = "auction:" + auctionId;
        Integer currentHighestAmount = redisCommon.getFromHash(hashKey, "amount", Integer.class);
        
        // DB에서 최고가 조회 (Redis 폴백)
        if (currentHighestAmount == null) {
            currentHighestAmount = bidRepository.findMaxAmountByAuction(auction)
                    .orElse(auction.getStartPrice());
        }
        
        return currentHighestAmount;
    }

    // 특정 경매의 입찰 내역 페이징 조회
    @Transactional(readOnly = true)
    public Page<BidHistoryResponse> getBidHistoryByAuctionWithPaging(Long auctionId, Pageable pageable) {
        long startTime = startOperation("getBidHistoryByAuctionWithPaging", "경매 입찰 내역 페이징 조회");
        try {
            Auction auction = auctionService.getAuctionWithValidation(auctionId);
            Page<Bid> bidPage = bidRepository.findByAuctionOrderByBidTimeDesc(auction, pageable);
            
            Integer currentHighestAmount = getCurrentHighestAmount(auctionId, auction);
            
            Page<BidHistoryResponse> responsePage = bidPage.map(bid -> {
                Boolean isHighestBid = bid.getAmount().equals(currentHighestAmount);
                return BidHistoryResponse.from(bid, isHighestBid);
            });
            
            endOperation("getBidHistoryByAuctionWithPaging", "경매 입찰 내역 페이징 조회", startTime);
            log.info("[입찰 내역 페이징 조회] 경매 입찰 내역 페이징 조회 완료 - 경매ID: {}, 총 개수: {}, 현재 페이지: {}", 
                    auctionId, responsePage.getTotalElements(), responsePage.getNumber());
            
            return responsePage;
            
        } catch (Exception e) {
            endOperation("getBidHistoryByAuctionWithPaging", "경매 입찰 내역 페이징 조회", startTime);
            log.error("[입찰 내역 페이징 조회 실패] 경매ID: {}, 오류: {}", auctionId, e.getMessage(), e);
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR.toString(), "입찰 내역 조회 중 오류가 발생했습니다.");
        }
    }

    // 특정 사용자의 입찰 내역 조회
    @Transactional(readOnly = true)
    public List<BidHistoryResponse> getBidHistoryByUser(String userUUID) {
        long startTime = startOperation("getBidHistoryByUser", "사용자 입찰 내역 조회");
        try {
            User user = userService.getUserByUUID(userUUID);
            List<Bid> bids = bidRepository.findByUserOrderByBidTimeDesc(user);
            
            List<BidHistoryResponse> responses = bids.stream()
                    .map(bid -> {
                        Integer currentHighestAmount = getCurrentHighestAmount(bid.getAuction().getAuctionId(), bid.getAuction());
                        Boolean isHighestBid = bid.getAmount().equals(currentHighestAmount);
                        return BidHistoryResponse.from(bid, isHighestBid);
                    })
                    .collect(Collectors.toList());
            
            endOperation("getBidHistoryByUser", "사용자 입찰 내역 조회", startTime);
            log.info("[사용자 입찰 내역 조회] 사용자 입찰 내역 조회 완료 - userUUID: {}, 입찰 개수: {}", 
                    userUUID, responses.size());
            
            return responses;
            
        } catch (Exception e) {
            endOperation("getBidHistoryByUser", "사용자 입찰 내역 조회", startTime);
            log.error("[사용자 입찰 내역 조회 실패] userUUID: {}, 오류: {}", userUUID, e.getMessage(), e);
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR.toString(), "입찰 내역 조회 중 오류가 발생했습니다.");
        }
    }

    // 특정 경매에서 특정 사용자의 입찰 내역 조회
    @Transactional(readOnly = true)
    public List<BidHistoryResponse> getBidHistoryByAuctionAndUser(Long auctionId, String userUUID) {
        long startTime = startOperation("getBidHistoryByAuctionAndUser", "사용자 경매별 입찰 내역 조회");
        try {
            Auction auction = auctionService.getAuctionWithValidation(auctionId);
            User user = userService.getUserByUUID(userUUID);
            List<Bid> bids = bidRepository.findByAuctionAndUserOrderByBidTimeDesc(auction, user);
            
            Integer currentHighestAmount = getCurrentHighestAmount(auctionId, auction);
            
            List<BidHistoryResponse> responses = bids.stream()
                    .map(bid -> {
                        Boolean isHighestBid = bid.getAmount().equals(currentHighestAmount);
                        return BidHistoryResponse.from(bid, isHighestBid);
                    })
                    .collect(Collectors.toList());
            
            endOperation("getBidHistoryByAuctionAndUser", "사용자 경매별 입찰 내역 조회", startTime);
            log.info("[사용자 경매별 입찰 내역 조회] 사용자 경매별 입찰 내역 조회 완료 - 경매ID: {}, userUUID: {}, 입찰 개수: {}", 
                    auctionId, userUUID, responses.size());
            
            return responses;
            
        } catch (Exception e) {
            endOperation("getBidHistoryByAuctionAndUser", "사용자 경매별 입찰 내역 조회", startTime);
            log.error("[사용자 경매별 입찰 내역 조회 실패] 경매ID: {}, userUUID: {}, 오류: {}", 
                    auctionId, userUUID, e.getMessage(), e);
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR.toString(), "입찰 내역 조회 중 오류가 발생했습니다.");
        }
    }

    // 경매 시간 유효성 검증
    private void validateAuctionTime(LocalDateTime now,  Auction auction) {
        // 경매 상태를 우선적으로 확인
        if (auction.getStatus() == AuctionStatus.UPCOMING) {
            log.warn("[입찰 검증 실패] 경매 시작 전 입찰 시도 - 경매ID: {}, 현재상태: {}, 시작시간: {}",
                    auction.getAuctionId(), auction.getStatus(), auction.getStartTime());
            throw new ServiceException(HttpStatus.BAD_REQUEST.toString(), "경매가 시작 전입니다.");
        } else if (auction.getStatus() == AuctionStatus.FINISHED) {
            log.warn("[입찰 검증 실패] 경매 종료 후 입찰 시도 - 경매ID: {}, 현재상태: {}, 종료시간: {}",
                    auction.getAuctionId(), auction.getStatus(), auction.getEndTime());
            throw new ServiceException(HttpStatus.BAD_REQUEST.toString(), "경매가 종료 되었습니다.");
        }

        // 경매 상태가 ONGOING인 경우에만 시간 검증 수행
        if (auction.getStatus() == AuctionStatus.ONGOING) {
            if(now.isBefore(auction.getStartTime())){
                log.warn("[입찰 검증 실패] 경매 시작 전 입찰 시도 - 경매ID: {}, 현재시간: {}, 시작시간: {}, 상태: {}",
                        auction.getAuctionId(), now, auction.getStartTime(), auction.getStatus());
                throw new ServiceException(HttpStatus.BAD_REQUEST.toString(), "경매가 시작 전입니다.");
            }else if(now.isAfter(auction.getEndTime())){
                log.warn("[입찰 검증 실패] 경매 종료 후 입찰 시도 - 경매ID: {}, 현재시간: {}, 종료시간: {}, 상태: {}",
                        auction.getAuctionId(), now, auction.getEndTime(), auction.getStatus());
                throw new ServiceException(HttpStatus.BAD_REQUEST.toString(), "경매가 종료 되었습니다.");
            }
        }
    }

    /** 입찰 금액 유효성 검증
     * @param newAmount         받아온 입찰 금액
     * @param currentAmount     최근 조희 금액
     * @param minBidAmount      최소 입찰 금액 단위
     */
    private void validateBidAmount(Integer newAmount, Integer currentAmount, Integer minBidAmount) {
        if(newAmount <= currentAmount) {
            log.warn("[입찰 검증 실패] 현재 최고가보다 낮은 금액 입찰 - 신규금액: {}, 현재금액: {}", newAmount, currentAmount);
            throw new ServiceException(HttpStatus.BAD_REQUEST.toString(), "입찰 금액이 현재 최고가보다 낮습니다.");
        }

        if(newAmount < (currentAmount + minBidAmount)) {
            log.warn("[입찰 검증 실패] 최소 입찰 단위 미달 - 신규금액: {}, 필요금액: {}", 
                    newAmount, (currentAmount + minBidAmount));
            throw new ServiceException(HttpStatus.BAD_REQUEST.toString(),
                    "입찰 금액이 최소 입찰 단위보다 작습니다. 최소 " + (currentAmount + minBidAmount) + "원 이상 입찰해야 합니다.");
        }
    }
}