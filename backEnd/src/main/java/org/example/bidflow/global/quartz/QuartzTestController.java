package org.example.bidflow.global.quartz;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/quartz")
@RequiredArgsConstructor
public class QuartzTestController {

    private final Scheduler scheduler;

    // Quartz 스케줄러 상태 확인
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSchedulerStatus() {
        Map<String, Object> status = new HashMap<>();

        try {
            status.put("schedulerName", scheduler.getSchedulerName());
            status.put("schedulerInstanceId", scheduler.getSchedulerInstanceId());
            status.put("isStarted", scheduler.isStarted());
            status.put("isInStandbyMode", scheduler.isInStandbyMode());
            status.put("isShutdown", scheduler.isShutdown());
            status.put("currentTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            return ResponseEntity.ok(status);
        } catch (SchedulerException e) {
            log.error("스케줄러 상태 조회 실패: {}", e.getMessage());
            status.put("error", e.getMessage());
            return ResponseEntity.ok(status);
        }
    }

    // 등록된 Job 목록 조회
    @GetMapping("/jobs")
    public ResponseEntity<List<Map<String, Object>>> getJobs() {
        List<Map<String, Object>> jobList = new ArrayList<>();

        try {
            for (String groupName : scheduler.getJobGroupNames()) {
                for (JobKey jobKey : scheduler.getJobKeys(GroupMatcher.jobGroupEquals(groupName))) {
                    Map<String, Object> jobInfo = new HashMap<>();

                    JobDetail jobDetail = scheduler.getJobDetail(jobKey);
                    List<? extends Trigger> triggers = scheduler.getTriggersOfJob(jobKey);

                    jobInfo.put("jobName", jobKey.getName());
                    jobInfo.put("jobGroup", jobKey.getGroup());
                    jobInfo.put("jobClass", jobDetail.getJobClass().getSimpleName());
                    jobInfo.put("description", jobDetail.getDescription());

                    if (!triggers.isEmpty()) {
                        Trigger trigger = triggers.get(0);
                        jobInfo.put("triggerState", scheduler.getTriggerState(trigger.getKey()).name());
                        jobInfo.put("nextFireTime", trigger.getNextFireTime());
                        jobInfo.put("previousFireTime", trigger.getPreviousFireTime());
                    }

                    jobList.add(jobInfo);
                }
            }

            return ResponseEntity.ok(jobList);
        } catch (SchedulerException e) {
            log.error("Job 목록 조회 실패: {}", e.getMessage());
            return ResponseEntity.ok(jobList);
        }
    }

    // 특정 Job 즉시 실행
    @PostMapping("/jobs/{jobName}/trigger")
    public ResponseEntity<String> triggerJob(@PathVariable String jobName) {
        try {
            JobKey jobKey = JobKey.jobKey(jobName, "testGroup");
            scheduler.triggerJob(jobKey);

            String message = String.format("Job '%s'이(가) 즉시 실행되었습니다. 로그를 확인해주세요.", jobName);
            log.info(message);

            return ResponseEntity.ok(message);
        } catch (SchedulerException e) {
            String errorMessage = String.format("Job '%s' 실행 실패: %s", jobName, e.getMessage());
            log.error(errorMessage);
            return ResponseEntity.badRequest().body(errorMessage);
        }
    }

    // 스케줄러 시작
    @PostMapping("/start")
    public ResponseEntity<String> startScheduler() {
        try {
            if (!scheduler.isStarted()) {
                scheduler.start();
                return ResponseEntity.ok("스케줄러가 시작되었습니다.");
            } else {
                return ResponseEntity.ok("스케줄러가 이미 실행 중입니다.");
            }
        } catch (SchedulerException e) {
            log.error("스케줄러 시작 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().body("스케줄러 시작 실패: " + e.getMessage());
        }
    }

    // 스케줄러 일시 정지
    @PostMapping("/standby")
    public ResponseEntity<String> standbyScheduler() {
        try {
            scheduler.standby();
            return ResponseEntity.ok("스케줄러가 일시 정지되었습니다.");
        } catch (SchedulerException e) {
            log.error("스케줄러 일시 정지 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().body("스케줄러 일시 정지 실패: " + e.getMessage());
        }
    }
}