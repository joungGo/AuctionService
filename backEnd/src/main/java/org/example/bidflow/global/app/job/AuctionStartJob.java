package org.example.bidflow.global.app.job;

import lombok.extern.slf4j.Slf4j;
import org.example.bidflow.data.AuctionStatus;
import org.example.bidflow.domain.auction.entity.Auction;
import org.example.bidflow.domain.auction.repository.AuctionRepository;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
public class AuctionStartJob implements Job {

    @Autowired
    private AuctionRepository auctionRepository;

    @Override
    @Transactional
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            // JobDataMap에서 auctionId 가져오기
            Long auctionId = context.getJobDetail().getJobDataMap().getLong("auctionId");
            
            log.info("[AuctionStartJob] 경매 시작 Job 실행 - 경매 ID: {}", auctionId);
            
            Auction auction = auctionRepository.findById(auctionId)
                    .orElseThrow(() -> new RuntimeException("경매를 찾을 수 없습니다: " + auctionId));
            
            if (auction.getStatus() == AuctionStatus.UPCOMING) {
                auction.setStatus(AuctionStatus.ONGOING);
                auctionRepository.save(auction);
                log.info("[AuctionStartJob] 경매 시작 완료 - 경매 ID: {}, 상품명: {}", auctionId, auction.getProduct().getProductName());
            } else {
                log.warn("[AuctionStartJob] 경매 상태가 UPCOMING이 아님 - 경매 ID: {}, 현재 상태: {}", auctionId, auction.getStatus());
            }
        } catch (Exception e) {
            log.error("[AuctionStartJob] 경매 시작 Job 실행 중 오류 발생", e);
            throw new JobExecutionException(e);
        }
    }
} 