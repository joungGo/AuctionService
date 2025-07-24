package org.example.bidflow.global.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bidflow.data.AuctionStatus;
import org.example.bidflow.domain.auction.entity.Auction;
import org.example.bidflow.domain.auction.repository.AuctionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

// 경매 종료 -> 종료 상태 변경 및 이벤트 발행
@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionSchedulerEvent {
    private final RedisTemplate<String, String> redisTemplate;
    private final AuctionRepository auctionRepository;
    private final ApplicationEventPublisher eventPublisher;
// @Scheduled(fixedDelay = 30000) -> 약 30초
// @Scheduled(fixedDelay = 10000) -> 5초
// @Scheduled(fixedDelay = 1000) -> 아예 실행 X. 로그도 안뜨는거같던데

    @Transactional
    @Scheduled(fixedDelay = 3000) // 3초마다 실행 -> 경매 종료 시 바로 실행
    public void processAuctions() {
        long startTime = System.currentTimeMillis();
        
        try {
            Set<String> keys = redisTemplate.keys("auction:*");

            if (keys == null || keys.isEmpty()) {
                // log.debug("[Scheduler] 현재 진행 중인 경매가 없습니다.");
                return;
            }

            // log.debug("[Scheduler] 경매 상태 검사 시작 - 대상 경매 수: {}", keys.size());

            int processedCount = 0;
            int finishedCount = 0;

            for (String key : keys) {
                try {
                    Long auctionId = Long.valueOf(key.split(":")[1]);
                    Optional<Auction> auctionOpt = auctionRepository.findById(auctionId);
                    
                    if (auctionOpt.isEmpty()) {
                        // log.warn("[Scheduler] 존재하지 않는 경매 ID - Redis Key: {}, 경매 ID: {}", key, auctionId);
                        continue;
                    }

                    Auction auction = auctionOpt.get();
                    processedCount++;

                    // 경매 종료 시간 도달 && 아직 종료 처리 안 됐을 때
                    LocalDateTime now = LocalDateTime.now();
                    if (now.isAfter(auction.getEndTime()) && auction.getStatus() != AuctionStatus.FINISHED) {
                        auction.setStatus(AuctionStatus.FINISHED);
                        auctionRepository.save(auction);
                        finishedCount++;
                        
                        // log.info("[Scheduler] 경매 종료 처리 완료 - 경매 ID: {}, 상품명: {}, 종료시간: {}, 처리시간: {}", auctionId, auction.getProduct().getProductName(), auction.getEndTime(), now);

                        // 즉시 이벤트 발행
                        eventPublisher.publishEvent(new AuctionFinishedEvent(this, auction));
                        // log.debug("[Scheduler] 경매 종료 이벤트 발행 완료 - 경매 ID: {}", auctionId);
                    }
                    
                } catch (NumberFormatException e) {
                    // log.error("[Scheduler] Redis Key 파싱 오류 - Key: {}, 오류: {}", key, e.getMessage());
                } catch (Exception e) {
                    // log.error("[Scheduler] 개별 경매 처리 중 오류 발생 - Key: {}", key, e);
                }
            }

            long endTime = System.currentTimeMillis();
            if (processedCount > 0) {
                // log.info("[Scheduler] 경매 상태 검사 완료 - 검사 대상: {}, 종료 처리: {}, 처리 시간: {}ms", processedCount, finishedCount, (endTime - startTime));
            }
            
        } catch (Exception e) {
            // log.error("[Scheduler] 스케줄러 실행 중 예상치 못한 오류 발생", e);
        }
    }
}