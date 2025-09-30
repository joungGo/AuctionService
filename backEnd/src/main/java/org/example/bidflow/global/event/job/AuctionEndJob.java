package org.example.bidflow.global.event.job;

import lombok.extern.slf4j.Slf4j;
import org.example.bidflow.domain.auction.entity.Auction;
import org.example.bidflow.domain.auction.entity.AuctionStatus;
import org.example.bidflow.domain.auction.repository.AuctionRepository;
import org.example.bidflow.global.event.AuctionFinishedEvent;
import org.example.bidflow.global.messaging.publisher.EventPublisher;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
public class AuctionEndJob implements Job {

    @Autowired
    private AuctionRepository auctionRepository;
    
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    
    @Autowired
    private EventPublisher wsEventPublisher;

    @Override
    @Transactional
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            // JobDataMap에서 auctionId 가져오기
            Long auctionId = context.getJobDetail().getJobDataMap().getLong("auctionId");
            
            log.info("[AuctionEndJob] 경매 종료 Job 실행 - 경매 ID: {}", auctionId);
            
            Auction auction = auctionRepository.findById(auctionId)
                    .orElseThrow(() -> new RuntimeException("경매를 찾을 수 없습니다: " + auctionId));
            
            if (auction.getStatus() == AuctionStatus.ONGOING) {
                auction.setStatus(AuctionStatus.FINISHED);
                auctionRepository.save(auction);
                
                // 경매 상태 변경 이벤트 발행 (관심사별 타겟팅)
                wsEventPublisher.publishAuctionStatusChange(
                    auction.getAuctionId(),
                    auction.getProduct().getCategory() != null ? auction.getProduct().getCategory().getCategoryId() : null,
                    "FINISHED",
                    auction.getStartPrice().longValue() // 현재 입찰가 (임시로 시작가 사용, 실제로는 최고가 조회 필요)
                );
                
                // 기존 이벤트 발행 (하위 호환성)
                eventPublisher.publishEvent(new AuctionFinishedEvent(this, auction));
                
                log.info("[AuctionEndJob] 경매 종료 완료 - 경매 ID: {}, 상품명: {}", auctionId, auction.getProduct().getProductName());
                log.debug("[AuctionEndJob] 경매 종료 이벤트 발행 완료 - 경매 ID: {}", auctionId);
            } else {
                log.warn("[AuctionEndJob] 경매 상태가 ONGOING이 아님 - 경매 ID: {}, 현재 상태: {}", auctionId, auction.getStatus());
            }
        } catch (Exception e) {
            log.error("[AuctionEndJob] 경매 종료 Job 실행 중 오류 발생", e);
            throw new JobExecutionException(e);
        }
    }
} 