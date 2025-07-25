package org.example.bidflow.global.quartz;

import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
public class SimpleTestJob extends QuartzJobBean {

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        String jobName = context.getJobDetail().getKey().getName();
        String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        log.info("=== Quartz Job 실행 ===");
        log.info("Job 이름: {}", jobName);
        log.info("실행 시간: {}", currentTime);
        log.info("Fire Time: {}", context.getFireTime());
        log.info("Next Fire Time: {}", context.getNextFireTime());
        log.info("======================");

        // Job Data Map에서 데이터 가져오기
        String message = (String) context.getJobDetail().getJobDataMap().get("message");
        if (message != null) {
            log.info("전달받은 메시지: {}", message);
        }
    }
}