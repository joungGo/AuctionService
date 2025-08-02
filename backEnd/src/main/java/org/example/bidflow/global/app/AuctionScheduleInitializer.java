package org.example.bidflow.global.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bidflow.data.AuctionStatus;
import org.example.bidflow.domain.auction.entity.Auction;
import org.example.bidflow.domain.auction.repository.AuctionRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionScheduleInitializer implements ApplicationRunner {

    private final AuctionRepository auctionRepository;
    private final AuctionSchedulerService auctionSchedulerService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        log.info("[AuctionScheduleInitializer] 경매 스케줄 복구 시작");
        
        try {
            LocalDateTime now = LocalDateTime.now();
            
            // 1. 과거 경매 상태 자동 수정
            fixPastAuctionStatuses(now);
            
            // 2. 스케줄이 필요한 경매들 조회
            List<Auction> auctionsToSchedule = auctionRepository.findAuctionsNeedingSchedule(now);
            
            if (auctionsToSchedule.isEmpty()) {
                log.info("[AuctionScheduleInitializer] 복구할 경매 스케줄이 없습니다.");
                return;
            }
            
            log.info("[AuctionScheduleInitializer] 복구 대상 경매 수: {}", auctionsToSchedule.size());
            
            int scheduledCount = 0;
            
            for (Auction auction : auctionsToSchedule) {
                try {
                    // 미래 시각의 경매만 스케줄 등록
                    if (shouldScheduleAuction(auction, now)) {
                        auctionSchedulerService.scheduleAuction(auction);
                        scheduledCount++;
                        log.debug("[AuctionScheduleInitializer] 경매 스케줄 복구 완료 - 경매 ID: {}, 상태: {}", 
                                auction.getAuctionId(), auction.getStatus());
                    }
                } catch (Exception e) {
                    log.error("[AuctionScheduleInitializer] 개별 경매 스케줄 복구 실패 - 경매 ID: {}", 
                            auction.getAuctionId(), e);
                }
            }
            
            log.info("[AuctionScheduleInitializer] 경매 스케줄 복구 완료 - 총 {}개 중 {}개 복구", 
                    auctionsToSchedule.size(), scheduledCount);
                    
        } catch (Exception e) {
            log.error("[AuctionScheduleInitializer] 경매 스케줄 복구 중 오류 발생", e);
        }
    }
    
    /**
     * 과거 경매들의 상태를 자동으로 수정
     */
    private void fixPastAuctionStatuses(LocalDateTime now) {
        try {
            // 모든 경매 조회
            List<Auction> allAuctions = auctionRepository.findAll();
            int fixedCount = 0;
            
            for (Auction auction : allAuctions) {
                boolean needsUpdate = false;
                AuctionStatus newStatus = auction.getStatus();
                
                // UPCOMING 상태인데 시작 시간이 지난 경우
                if (auction.getStatus() == AuctionStatus.UPCOMING && 
                    auction.getStartTime() != null && 
                    auction.getStartTime().isBefore(now)) {
                    
                    // 종료 시간도 지났으면 FINISHED, 아니면 ONGOING
                    if (auction.getEndTime() != null && auction.getEndTime().isBefore(now)) {
                        newStatus = AuctionStatus.FINISHED;
                    } else {
                        newStatus = AuctionStatus.ONGOING;
                    }
                    needsUpdate = true;
                }
                // ONGOING 상태인데 종료 시간이 지난 경우
                else if (auction.getStatus() == AuctionStatus.ONGOING && 
                         auction.getEndTime() != null && 
                         auction.getEndTime().isBefore(now)) {
                    newStatus = AuctionStatus.FINISHED;
                    needsUpdate = true;
                }
                
                if (needsUpdate) {
                    auction.setStatus(newStatus);
                    auctionRepository.save(auction);
                    fixedCount++;
                    log.info("[AuctionScheduleInitializer] 경매 상태 자동 수정 - 경매 ID: {}, {} → {}", 
                            auction.getAuctionId(), auction.getStatus(), newStatus);
                }
            }
            
            if (fixedCount > 0) {
                log.info("[AuctionScheduleInitializer] 경매 상태 자동 수정 완료 - {}개 경매 수정", fixedCount);
            }
            
        } catch (Exception e) {
            log.error("[AuctionScheduleInitializer] 경매 상태 자동 수정 중 오류 발생", e);
        }
    }
    
    /**
     * 경매를 스케줄에 등록해야 하는지 판단
     */
    private boolean shouldScheduleAuction(Auction auction, LocalDateTime now) {
        // UPCOMING 상태이고 시작 시간이 미래인 경우
        if (auction.getStatus() == AuctionStatus.UPCOMING && 
            auction.getStartTime() != null && 
            auction.getStartTime().isAfter(now)) {
            return true;
        }
        
        // ONGOING 상태이고 종료 시간이 미래인 경우
        if (auction.getStatus() == AuctionStatus.ONGOING && 
            auction.getEndTime() != null && 
            auction.getEndTime().isAfter(now)) {
            return true;
        }
        
        return false;
    }
} 