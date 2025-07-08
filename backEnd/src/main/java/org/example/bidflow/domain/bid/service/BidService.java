package org.example.bidflow.domain.bid.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bidflow.domain.auction.dto.AuctionBidRequest;
import org.example.bidflow.domain.auction.entity.Auction;
import org.example.bidflow.domain.auction.service.AuctionService;
import org.example.bidflow.domain.bid.dto.model.response.BidCreateResponse;
import org.example.bidflow.domain.bid.entity.Bid;
import org.example.bidflow.domain.bid.repository.BidRepository;
import org.example.bidflow.domain.user.entity.User;
import org.example.bidflow.domain.user.service.UserService;
import org.example.bidflow.global.app.RedisCommon;
import org.example.bidflow.global.exception.ServiceException;
import org.example.bidflow.global.utils.JwtProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class BidService {

    private final AuctionService auctionService;
    private final UserService userService;
    private final BidRepository bidRepository;
    private final RedisCommon redisCommon;

    @Transactional
    public BidCreateResponse createBid(Long auctionId, AuctionBidRequest request, String userUUID) {
        long startTime = System.currentTimeMillis();
        log.info("[입찰 시작] 경매 입찰 처리 시작 - 경매ID: {}, 입찰금액: {}, userUUID: {}", 
                auctionId, request.getAmount(), userUUID);
        
        String hashKey = "auction:" + auctionId;
        LocalDateTime now = LocalDateTime.now();

        try {
            // 유저 및 경매 정보 가져오기 (userUUID는 파라미터로 받음)
            log.debug("[입찰 정보] 사용자 정보 - userUUID: {}", userUUID);
            
            User user = userService.getUserByUUID(userUUID);
            Auction auction = auctionService.getAuctionWithValidation(auctionId);
            
            log.debug("[입찰 정보] 경매 정보 - 경매ID: {}, 시작가: {}, 최소입찰단위: {}, 시작시간: {}, 종료시간: {}", 
                    auctionId, auction.getStartPrice(), auction.getMinBid(), auction.getStartTime(), auction.getEndTime());

            // 경매 시간 검증
            validateAuctionTime(now, auction);

            // Redis에서 현재 최고가 조회
            Integer amount = redisCommon.getFromHash(hashKey, "amount", Integer.class);
            String highestUserUUID = redisCommon.getFromHash(hashKey, "userUUID", String.class); // 현재 최고 입찰자
            int currentBidAmount = (amount != null) ? amount : auction.getStartPrice();     // DB 테스트를 위한 redis에 없으면 시작가로 설정

            log.debug("[입찰 현황] 현재 최고 입찰 정보 - 최고가: {}, 최고입찰자: {}", currentBidAmount, highestUserUUID);

            if (userUUID.equals(highestUserUUID)) {
                log.warn("[입찰 실패] 동일 사용자 연속 입찰 시도 - userUUID: {}, 경매ID: {}", userUUID, auctionId);
                throw new ServiceException(HttpStatus.BAD_REQUEST.toString(), "이미 최고 입찰자입니다. 다른 사용자의 입찰을 기다려주세요.");
            }

            // 최소 입찰 단위 검증
            validateBidAmount(request.getAmount(), currentBidAmount, auction.getMinBid());

            // Redis에 입찰 정보 갱신
            redisCommon.putInHash(hashKey, "amount", request.getAmount());
            redisCommon.putInHash(hashKey, "userUUID", userUUID);
            
            log.info("[입찰 갱신] Redis 최고가 갱신 완료 - 경매ID: {}, 이전가격: {}, 새가격: {}, 입찰자: {}", 
                    auctionId, currentBidAmount, request.getAmount(), user.getNickname());

            // DB 저장 (낙찰용 로그로 남김)
            Bid bid = Bid.createBid(auction, user, request.getAmount(), LocalDateTime.now());
            bidRepository.save(bid);
            
            long endTime = System.currentTimeMillis();
            log.info("[입찰 성공] 입찰 처리 완료 - 경매ID: {}, 입찰자: {}, 금액: {}, 처리시간: {}ms", 
                    auctionId, user.getNickname(), request.getAmount(), (endTime - startTime));

            return BidCreateResponse.from(bid);
            
        } catch (ServiceException e) {
            log.error("[입찰 실패] 비즈니스 규칙 위반 - 경매ID: {}, 입찰금액: {}, 오류: {}", 
                    auctionId, request.getAmount(), e.getMsg());
            throw e;
        } catch (Exception e) {
            log.error("[입찰 오류] 예상치 못한 오류 발생 - 경매ID: {}, 입찰금액: {}", 
                    auctionId, request.getAmount(), e);
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR.toString(), "입찰 처리 중 오류가 발생했습니다.");
        }
    }

    // 경매 시간 유효성 검증
    private void validateAuctionTime(LocalDateTime now,  Auction auction) {
        if(now.isBefore(auction.getStartTime())){
            log.warn("[입찰 검증 실패] 경매 시작 전 입찰 시도 - 경매ID: {}, 현재시간: {}, 시작시간: {}", 
                    auction.getAuctionId(), now, auction.getStartTime());
            throw new ServiceException(HttpStatus.BAD_REQUEST.toString(), "경매가 시작 전입니다.");
        }else if(now.isAfter(auction.getEndTime())){
            log.warn("[입찰 검증 실패] 경매 종료 후 입찰 시도 - 경매ID: {}, 현재시간: {}, 종료시간: {}", 
                    auction.getAuctionId(), now, auction.getEndTime());
            throw new ServiceException(HttpStatus.BAD_REQUEST.toString(), "경매가 종료 되었습니다.");
        }
        log.debug("[입찰 검증 성공] 경매 시간 유효성 확인 완료 - 경매ID: {}", auction.getAuctionId());
    }

    /** 입찰 금액 유효성 검증
     * @param newAmount         받아온 입찰 금액
     * @param currentAmount     최근 조희 금액
     * @param minBidAmount      최소 입찰 금액 단위
     */
    private void validateBidAmount(Integer newAmount, Integer currentAmount, Integer minBidAmount) {
        log.debug("[입찰 금액 검증] 입찰 금액 유효성 검사 - 신규금액: {}, 현재금액: {}, 최소단위: {}", 
                newAmount, currentAmount, minBidAmount);
        
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
        
        log.debug("[입찰 검증 성공] 입찰 금액 유효성 확인 완료");
    }
}