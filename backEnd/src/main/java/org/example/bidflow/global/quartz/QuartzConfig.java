package org.example.bidflow.global.quartz;

import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QuartzConfig {

    // 30초마다 실행되는 Job 설정
    @Bean
    public JobDetail simpleTestJobDetail() {
        return JobBuilder.newJob(SimpleTestJob.class)
                .withIdentity("simpleTestJob", "testGroup")
                .withDescription("Quartz 동작 확인을 위한 테스트 Job")
                .usingJobData("message", "Quartz가 정상적으로 동작하고 있습니다!")
                .storeDurably()
                .build();
    }

    // 30초마다 실행되는 Trigger 설정
    @Bean
    public Trigger simpleTestTrigger() {
        return TriggerBuilder.newTrigger()
                .forJob(simpleTestJobDetail())
                .withIdentity("simpleTestTrigger", "testGroup")
                .withDescription("30초마다 실행")
                .startNow()  // 즉시 시작
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInSeconds(30)  // 30초 간격
                        .repeatForever())
                .build();
    }
}