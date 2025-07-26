package org.example.bidflow.global.quartz; // 패키지 선언

import org.quartz.*; // Quartz 프레임워크의 주요 클래스 임포트
import org.springframework.context.annotation.Bean; // @Bean 어노테이션 임포트
import org.springframework.context.annotation.Configuration; // @Configuration 어노테이션 임포트

/**
 * QuartzConfig 클래스는 Quartz 스케쥴러에 대한 세부 설정과 이를 반영한 Job을 생성하는 클래스
 */
@Configuration // 이 클래스가 스프링 설정 클래스임을 명시
public class QuartzConfig { // Quartz 관련 설정을 담당하는 클래스

    // 30초마다 실행되는 Job 설정 -> 아래 simpleTestTrigger() 메서드에서 30초 단위를 설정함
    @Bean // simpleTestJobDetail() 메서드가 Bean으로 등록됨을 명시
    public JobDetail simpleTestJobDetail() { // Quartz JobDetail Bean 생성 메서드
        return JobBuilder.newJob(SimpleTestJob.class) // SimpleTestJob 클래스를 실행할 Job 생성
                .withIdentity("simpleTestJob", "testGroup") // Job의 이름과 그룹 지정
                .withDescription("Quartz 동작 확인을 위한 테스트 Job") // Job에 대한 설명 추가
                .usingJobData("message", "Quartz가 정상적으로 동작하고 있습니다!") // Job에 전달할 데이터(message) 설정
                .storeDurably() // JobDetail이 트리거 없이도 저장되도록 설정
                .build(); // JobDetail 객체 생성
    }

    // 30초마다 실행되는 Trigger 설정
    @Bean // simpleTestTrigger() 메서드가 Bean으로 등록됨을 명시
    public Trigger simpleTestTrigger() { // Quartz Trigger Bean 생성 메서드
        return TriggerBuilder.newTrigger() // 새로운 Trigger 생성
                .forJob(simpleTestJobDetail()) // 위에서 생성한 simpleTestJobDetail과 연결
                .withIdentity("simpleTestTrigger", "testGroup") // Trigger의 이름과 그룹 지정
                .withDescription("30초마다 실행") // Trigger에 대한 설명 추가
                .startNow()  // 애플리케이션 시작 시 즉시 Trigger 실행
                .withSchedule(SimpleScheduleBuilder.simpleSchedule() // 단순 반복 스케줄 설정
                        .withIntervalInSeconds(30)  // 30초 간격으로 실행
                        .repeatForever()) // 무한 반복
                .build(); // Trigger 객체 생성
    }
} // QuartzConfig 클래스 끝