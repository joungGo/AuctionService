package org.example.bidflow.global.app.job;

import lombok.extern.slf4j.Slf4j;
import org.example.bidflow.data.AuctionStatus;
import org.example.bidflow.domain.auction.entity.Auction;
import org.example.bidflow.domain.auction.repository.AuctionRepository;
import org.example.bidflow.global.app.AuctionFinishedEvent;
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
                log.info("[AuctionEndJob] 경매 종료 완료 - 경매 ID: {}, 상품명: {}", auctionId, auction.getProduct().getProductName());
                eventPublisher.publishEvent(new AuctionFinishedEvent(this, auction));
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