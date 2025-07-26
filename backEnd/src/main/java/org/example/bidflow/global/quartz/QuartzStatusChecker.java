package org.example.bidflow.global.quartz; // 패키지 선언

import lombok.RequiredArgsConstructor; // Lombok의 RequiredArgsConstructor 임포트 (final 필드 생성자 자동 생성)
import lombok.extern.slf4j.Slf4j; // Lombok의 Slf4j 로깅 어노테이션 임포트
import org.quartz.Scheduler; // Quartz의 Scheduler 인터페이스 임포트
import org.quartz.SchedulerException; // Quartz의 SchedulerException 임포트
import org.springframework.boot.context.event.ApplicationReadyEvent; // 스프링 ApplicationReadyEvent 임포트
import org.springframework.context.event.EventListener; // 스프링의 EventListener 어노테이션 임포트
import org.springframework.stereotype.Component; // 스프링의 Component 어노테이션 임포트

@Slf4j // Slf4j 로깅 사용 선언
@Component // 스프링 빈으로 등록
@RequiredArgsConstructor // final 필드에 대한 생성자 자동 생성
public class QuartzStatusChecker { // Quartz 상태를 확인하는 컴포넌트 클래스

    private final Scheduler scheduler; // Quartz 스케줄러 주입

    @EventListener(ApplicationReadyEvent.class) // ApplicationReadyEvent 발생 시 실행
    public void checkQuartzStatus() { // Quartz 상태를 확인하는 메서드
        try {
            log.info("==================== Quartz 상태 확인 ===================="); // 구분선 로그 출력
            log.info("스케줄러 이름: {}", scheduler.getSchedulerName()); // 스케줄러 이름 출력
            log.info("스케줄러 인스턴스 ID: {}", scheduler.getSchedulerInstanceId()); // 인스턴스 ID 출력
            log.info("스케줄러 시작 여부: {}", scheduler.isStarted()); // 시작 여부 출력
            log.info("스케줄러 대기 모드: {}", scheduler.isInStandbyMode()); // 대기 모드 여부 출력
            log.info("등록된 Job 그룹 수: {}", scheduler.getJobGroupNames().size()); // 등록된 Job 그룹 수 출력
            log.info("등록된 Trigger 그룹 수: {}", scheduler.getTriggerGroupNames().size()); // 등록된 Trigger 그룹 수 출력
            log.info("========================================================"); // 구분선 로그 출력

            if (scheduler.isStarted()) { // 스케줄러가 시작된 경우
                log.info("✅ Quartz 스케줄러가 정상적으로 시작되었습니다!"); // 정상 시작 메시지
                log.info("💡 테스트 Job이 30초마다 실행됩니다. 로그를 확인해주세요."); // 테스트 Job 안내
                log.info("🔗 API 테스트: http://localhost:8080/api/quartz/status"); // API 테스트 안내
            } else { // 시작되지 않은 경우
                log.warn("⚠️ Quartz 스케줄러가 시작되지 않았습니다."); // 경고 메시지
            }

        } catch (SchedulerException e) { // 예외 발생 시
            log.error("❌ Quartz 상태 확인 중 오류 발생: {}", e.getMessage()); // 에러 로그 출력
        }
    }
} // QuartzStatusChecker 클래스 끝