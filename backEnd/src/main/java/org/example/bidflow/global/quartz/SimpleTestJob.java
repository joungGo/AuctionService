package org.example.bidflow.global.quartz; // 패키지 선언

import lombok.extern.slf4j.Slf4j; // Lombok의 Slf4j 로깅 어노테이션 임포트
import org.quartz.JobExecutionContext; // Quartz의 JobExecutionContext 임포트
import org.quartz.JobExecutionException; // Quartz의 JobExecutionException 임포트
import org.springframework.scheduling.quartz.QuartzJobBean; // 스프링의 QuartzJobBean 임포트
import org.springframework.stereotype.Component; // 스프링의 Component 어노테이션 임포트

import java.time.LocalDateTime; // LocalDateTime 임포트 (현재 시간)
import java.time.format.DateTimeFormatter; // 시간 포맷터 임포트

/**
 * SimpleTestJob 클래스는 Quartz Job을 구현하여 주기적으로 실행되는 작업을 정의합니다.
 */
@Slf4j // Slf4j 로깅 사용 선언
@Component // 스프링 빈으로 등록
public class SimpleTestJob extends QuartzJobBean { // Quartz Job 구현 클래스

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException { // Job 실행 시 호출되는 메서드
        String jobName = context.getJobDetail().getKey().getName(); // Job 이름 가져오기
        String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")); // 현재 시간 포맷팅

        log.info("=== Quartz Job 실행 ==="); // 구분선 로그 출력
        log.info("Job 이름: {}", jobName); // Job 이름 출력
        log.info("실행 시간: {}", currentTime); // 실행 시간 출력
        log.info("Fire Time: {}", context.getFireTime()); // 실제 실행된 시간 출력
        log.info("Next Fire Time: {}", context.getNextFireTime()); // 다음 실행 예정 시간 출력
        log.info("======================"); // 구분선 로그 출력

        // Job Data Map에서 데이터 가져오기
        String message = (String) context.getJobDetail().getJobDataMap().get("message"); // JobDataMap에서 message 값 추출
        if (message != null) { // message가 존재하면
            log.info("전달받은 메시지: {}", message); // 전달받은 메시지 출력
        }
    }
} // SimpleTestJob 클래스 끝