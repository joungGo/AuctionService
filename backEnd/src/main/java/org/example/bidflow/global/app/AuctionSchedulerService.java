package org.example.bidflow.global.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.bidflow.domain.auction.entity.Auction;
import org.example.bidflow.global.app.job.AuctionStartJob;
import org.example.bidflow.global.app.job.AuctionEndJob;
import org.quartz.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionSchedulerService {

    private final Scheduler scheduler;
    
    @Autowired
    private AuctionStartJob auctionStartJob;
    
    @Autowired
    private AuctionEndJob auctionEndJob;

    /**
     * 경매의 시작/종료 스케줄을 등록
     */
    @Transactional
    public void scheduleAuction(Auction auction) {
        try {
            Long auctionId = auction.getAuctionId();
            
            log.info("[AuctionScheduler] 경매 스케줄 등록 시작 - 경매 ID: {}, 현재시간: {}", 
                    auctionId, LocalDateTime.now(ZoneId.of("Asia/Seoul")));
            
            // 기존 스케줄이 있다면 제거
            unscheduleAuction(auctionId);
            
            // 경매 시작 스케줄 등록
            if (auction.getStartTime() != null && auction.getStartTime().isAfter(LocalDateTime.now(ZoneId.of("Asia/Seoul")))) {
                scheduleAuctionStart(auctionId, auction.getStartTime());
                log.info("[AuctionScheduler] 경매 시작 스케줄 등록 완료 - 경매 ID: {}, 시작시간: {}", 
                        auctionId, auction.getStartTime());
            } else {
                log.warn("[AuctionScheduler] 경매 시작 시간이 과거이거나 null - 경매 ID: {}, 시작시간: {}", 
                        auctionId, auction.getStartTime());
            }
            
            // 경매 종료 스케줄 등록
            if (auction.getEndTime() != null && auction.getEndTime().isAfter(LocalDateTime.now(ZoneId.of("Asia/Seoul")))) {
                scheduleAuctionEnd(auctionId, auction.getEndTime());
                log.info("[AuctionScheduler] 경매 종료 스케줄 등록 완료 - 경매 ID: {}, 종료시간: {}", 
                        auctionId, auction.getEndTime());
            } else {
                log.warn("[AuctionScheduler] 경매 종료 시간이 과거이거나 null - 경매 ID: {}, 종료시간: {}", 
                        auctionId, auction.getEndTime());
            }
            
        } catch (Exception e) {
            log.error("[AuctionScheduler] 경매 스케줄 등록 중 오류 발생 - 경매 ID: {}", auction.getAuctionId(), e);
        }
    }

    /**
     * 경매 시작 스케줄 등록
     */
    private void scheduleAuctionStart(Long auctionId, LocalDateTime startTime) throws SchedulerException {
        // 1. JobDetail 생성 - Spring Bean Job 사용
        JobDetail jobDetail = newJob(AuctionStartJob.class)  // Spring Bean Job 클래스 지정
                .withIdentity("auction-start-" + auctionId, "auction-jobs")  // Job의 고유 식별자 설정
                .usingJobData("auctionId", auctionId)  // Job 실행 시 전달할 데이터 설정
                .storeDurably()  // Trigger가 없어도 Job을 데이터베이스에 유지하도록 설정
                .build();  // JobDetail 객체 생성 완료

        // 2. Trigger 생성 - Job을 언제 실행할지 정의
        Trigger trigger = newTrigger()  // 새로운 Trigger 생성 시작
                .withIdentity("auction-start-trigger-" + auctionId, "auction-triggers")  // Trigger의 고유 식별자 설정
                .startAt(Date.from(startTime.atZone(ZoneId.of("Asia/Seoul")).toInstant()))  // Job 실행 시각 설정
                .build();  // Trigger 객체 생성 완료

        // 3. 스케줄러에 Job과 Trigger 등록
        scheduler.scheduleJob(jobDetail, trigger);  // Quartz 스케줄러에 Job과 Trigger를 연결하여 등록
    }

    /**
     * 경매 종료 스케줄 등록
     */
    private void scheduleAuctionEnd(Long auctionId, LocalDateTime endTime) throws SchedulerException {
        // 1. JobDetail 생성 - Spring Bean Job 사용
        JobDetail jobDetail = newJob(AuctionEndJob.class)  // Spring Bean Job 클래스 지정
                .withIdentity("auction-end-" + auctionId, "auction-jobs")  // Job의 고유 식별자 설정
                .usingJobData("auctionId", auctionId)  // Job 실행 시 전달할 데이터 설정
                .storeDurably()  // Trigger가 없어도 Job을 데이터베이스에 유지하도록 설정
                .build();  // JobDetail 객체 생성 완료

        // 2. Trigger 생성 - Job을 언제 실행할지 정의
        Trigger trigger = newTrigger()  // 새로운 Trigger 생성 시작
                .withIdentity("auction-end-trigger-" + auctionId, "auction-triggers")  // Trigger의 고유 식별자 설정
                .startAt(Date.from(endTime.atZone(ZoneId.of("Asia/Seoul")).toInstant()))  // Job 실행 시각 설정
                .build();  // Trigger 객체 생성 완료

        // 3. 스케줄러에 Job과 Trigger 등록
        scheduler.scheduleJob(jobDetail, trigger);  // Quartz 스케줄러에 Job과 Trigger를 연결하여 등록
    }

    /**
     * 경매의 모든 스케줄 해제
     */
    @Transactional
    public void unscheduleAuction(Long auctionId) {
        try {
            // 시작 스케줄 해제
            scheduler.deleteJob(JobKey.jobKey("auction-start-" + auctionId, "auction-jobs"));
            
            // 종료 스케줄 해제
            scheduler.deleteJob(JobKey.jobKey("auction-end-" + auctionId, "auction-jobs"));
            
            log.info("[AuctionScheduler] 경매 스케줄 해제 완료 - 경매 ID: {}", auctionId);
            
        } catch (Exception e) {
            log.error("[AuctionScheduler] 경매 스케줄 해제 중 오류 발생 - 경매 ID: {}", auctionId, e);
        }
    }

    /**
     * 모든 경매 스케줄 해제 (시스템 정리용)
     */
    public void unscheduleAllAuctions() {
        try {
            scheduler.clear();
            log.info("[AuctionScheduler] 모든 경매 스케줄 해제 완료");
        } catch (Exception e) {
            log.error("[AuctionScheduler] 모든 경매 스케줄 해제 중 오류 발생", e);
        }
    }

    /**
     * 특정 경매의 스케줄 정보 조회
     */
    public void getAuctionScheduleInfo(Long auctionId) {
        try {
            JobKey startJobKey = JobKey.jobKey("auction-start-" + auctionId, "auction-jobs");
            JobKey endJobKey = JobKey.jobKey("auction-end-" + auctionId, "auction-jobs");
            
            JobDetail startJob = scheduler.getJobDetail(startJobKey);
            JobDetail endJob = scheduler.getJobDetail(endJobKey);
            
            if (startJob != null) {
                log.info("[AuctionScheduler] 경매 시작 스케줄 존재 - 경매 ID: {}", auctionId);
            }
            if (endJob != null) {
                log.info("[AuctionScheduler] 경매 종료 스케줄 존재 - 경매 ID: {}", auctionId);
            }
            
        } catch (Exception e) {
            log.error("[AuctionScheduler] 경매 스케줄 정보 조회 중 오류 발생 - 경매 ID: {}", auctionId, e);
        }
    }
    
    /**
     * 수동으로 경매 시작 Job 실행 (테스트용)
     */
    public void triggerAuctionStart(Long auctionId) {
        try {
            JobKey jobKey = JobKey.jobKey("auction-start-" + auctionId, "auction-jobs");
            scheduler.triggerJob(jobKey);
            log.info("[AuctionScheduler] 경매 시작 Job 수동 실행 - 경매 ID: {}", auctionId);
        } catch (Exception e) {
            log.error("[AuctionScheduler] 경매 시작 Job 수동 실행 실패 - 경매 ID: {}", auctionId, e);
        }
    }
    
    /**
     * 수동으로 경매 종료 Job 실행 (테스트용)
     */
    public void triggerAuctionEnd(Long auctionId) {
        try {
            JobKey jobKey = JobKey.jobKey("auction-end-" + auctionId, "auction-jobs");
            scheduler.triggerJob(jobKey);
            log.info("[AuctionScheduler] 경매 종료 Job 수동 실행 - 경매 ID: {}", auctionId);
        } catch (Exception e) {
            log.error("[AuctionScheduler] 경매 종료 Job 수동 실행 실패 - 경매 ID: {}", auctionId, e);
        }
    }
} 