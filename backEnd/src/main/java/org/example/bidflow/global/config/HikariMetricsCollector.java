package org.example.bidflow.global.config;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * HikariCP 커넥션 풀의 메트릭을 주기적으로 수집하여 모니터링하는 컴포넌트
 * Micrometer를 통해 메트릭을 수집하고 등록한다.
 */
@Slf4j
@Component
public class HikariMetricsCollector {

    // Micrometer 메트릭 레지스트리 - 메트릭을 등록하고 관리하는 중앙 저장소
    private final MeterRegistry meterRegistry;
    // 데이터베이스 커넥션을 관리하는 DataSource
    private final DataSource dataSource;

    /**
     * 생성자 기반 의존성 주입
     * @param meterRegistry 메트릭을 등록할 레지스트리
     * @param dataSource HikariCP 데이터소스
     */
    public HikariMetricsCollector(MeterRegistry meterRegistry, DataSource dataSource) {
        this.meterRegistry = meterRegistry;
        this.dataSource = dataSource;
    }

    /**
     * HikariCP 커넥션 풀 메트릭을 주기적으로 수집하는 스케줄링 메서드
     * @Scheduled(fixedRate = 3000): 3초(3,000ms)마다 실행
     */
    @Scheduled(fixedRate = 3000) // 5초마다 수집
    public void collectHikariMetrics() {
        // DataSource가 HikariDataSource 인스턴스인지 확인
        if (dataSource instanceof HikariDataSource) {
            // HikariDataSource로 캐스팅하여 HikariCP 전용 메서드 접근
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            // HikariCP의 JMX MBean을 통해 실시간 풀 상태 정보에 접근
            HikariPoolMXBean poolMXBean = hikariDataSource.getHikariPoolMXBean();

            try {
                // === 커넥션 풀 설정 관련 메트릭 등록 ===

                // 최대 커넥션 풀 크기 메트릭 - 설정된 최대 커넥션 수
                Gauge.builder("hikaricp.pool.size", hikariDataSource, ds -> ds.getMaximumPoolSize())
                        .description("최대 커넥션 풀 크기")
                        .register(meterRegistry);

                // 최소 유휴 커넥션 수 메트릭 - 항상 유지해야 할 최소 커넥션 수
                Gauge.builder("hikaricp.pool.minimum", hikariDataSource, ds -> ds.getMinimumIdle())
                        .description("최소 유휴 커넥션 수")
                        .register(meterRegistry);

                // === 실시간 커넥션 상태 메트릭 등록 ===

                // 커넥션을 기다리고 있는 스레드 수 - 커넥션 부족 상황을 모니터링
                Gauge.builder("hikaricp.connections.pending", poolMXBean, bean -> bean.getThreadsAwaitingConnection())
                        .description("커넥션 대기 중인 스레드 수")
                        .register(meterRegistry);

                // === 커넥션 풀 사용률 계산 및 등록 ===

                // 현재 활성 상태인 커넥션 수 (실제 사용 중인 커넥션)
                int activeConnections = poolMXBean.getActiveConnections();
                // 설정된 최대 커넥션 풀 크기
                int maxPoolSize = hikariDataSource.getMaximumPoolSize();
                // 사용률 계산 (활성 커넥션 / 최대 커넥션 * 100)
                // maxPoolSize가 0보다 클 때만 계산하여 0으로 나누기 방지
                double usagePercentage = maxPoolSize > 0 ? (double) activeConnections / maxPoolSize * 100 : 0;

                // 커넥션 풀 사용률을 백분율로 등록 - 람다 표현식으로 실시간 값 제공
                Gauge.builder("hikaricp.pool.usage.percentage", () -> usagePercentage)
                        .description("커넥션 풀 사용률 (%)")
                        .register(meterRegistry);

                // 디버그 레벨 로그 - 수집된 메트릭 정보를 로그로 출력
                // 활성 커넥션, 유휴 커넥션, 대기 스레드, 사용률 정보 포함
                log.debug("HikariCP 메트릭 수집 완료 - 활성: {}, 유휴: {}, 대기: {}, 사용률: {:.2f}%",
                        poolMXBean.getActiveConnections(),      // 현재 사용 중인 커넥션 수
                        poolMXBean.getIdleConnections(),        // 현재 유휴 상태인 커넥션 수
                        poolMXBean.getThreadsAwaitingConnection(), // 커넥션을 기다리는 스레드 수
                        usagePercentage);                       // 계산된 사용률

            } catch (Exception e) {
                // 메트릭 수집 중 발생한 예외를 로그로 기록 (WARN 레벨)
                // 메트릭 수집 실패가 애플리케이션 전체에 영향을 주지 않도록 예외 처리
                log.warn("HikariCP 메트릭 수집 중 오류 발생: {}", e.getMessage());
            }
        }
    }
}