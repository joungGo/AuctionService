package org.example.bidflow.global.quartz;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuartzStatusChecker {

    private final Scheduler scheduler;

    @EventListener(ApplicationReadyEvent.class)
    public void checkQuartzStatus() {
        try {
            log.info("==================== Quartz 상태 확인 ====================");
            log.info("스케줄러 이름: {}", scheduler.getSchedulerName());
            log.info("스케줄러 인스턴스 ID: {}", scheduler.getSchedulerInstanceId());
            log.info("스케줄러 시작 여부: {}", scheduler.isStarted());
            log.info("스케줄러 대기 모드: {}", scheduler.isInStandbyMode());
            log.info("등록된 Job 그룹 수: {}", scheduler.getJobGroupNames().size());
            log.info("등록된 Trigger 그룹 수: {}", scheduler.getTriggerGroupNames().size());
            log.info("========================================================");

            if (scheduler.isStarted()) {
                log.info("✅ Quartz 스케줄러가 정상적으로 시작되었습니다!");
                log.info("💡 테스트 Job이 30초마다 실행됩니다. 로그를 확인해주세요.");
                log.info("🔗 API 테스트: http://localhost:8080/api/quartz/status");
            } else {
                log.warn("⚠️ Quartz 스케줄러가 시작되지 않았습니다.");
            }

        } catch (SchedulerException e) {
            log.error("❌ Quartz 상태 확인 중 오류 발생: {}", e.getMessage());
        }
    }
}