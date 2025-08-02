package org.example.bidflow.global.config;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * HikariCP 커넥션 풀의 메트릭을 Micrometer에 등록하는 설정 클래스
 * MeterBinder를 통해 애플리케이션 시작 시점에 모든 메트릭을 한 번에 등록한다.
 */
@Configuration
public class HikariMetricsConfig {

    /**
     * HikariCP 메트릭을 등록하는 MeterBinder 빈을 생성
     * MeterBinder는 Micrometer의 인터페이스로, 메트릭 등록 로직을 캡슐화한다.
     *
     * @param dataSource HikariCP 데이터소스 (Spring에서 자동 주입)
     * @return MeterBinder 인스턴스 - 메트릭 등록 로직을 담은 함수형 인터페이스
     */
    @Bean
    public MeterBinder hikariMetrics(DataSource dataSource) {
        // MeterBinder는 함수형 인터페이스이므로 람다 표현식으로 구현
        // registry -> { ... } 형태로 메트릭 등록 로직을 정의
        return registry -> {
            // DataSource가 HikariDataSource인지 타입 체크
            // HikariCP가 아닌 다른 커넥션 풀 구현체인 경우 메트릭 등록을 건너뛴다
            if (dataSource instanceof HikariDataSource) {
                // HikariDataSource로 캐스팅하여 HikariCP 전용 기능에 접근
                HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
                // JMX MBean을 통해 실시간 커넥션 풀 상태 정보에 접근
                HikariPoolMXBean poolMXBean = hikariDataSource.getHikariPoolMXBean();

                // === 커넥션 개수 관련 Gauge 메트릭 등록 ===
                // Gauge: 특정 시점의 값을 나타내는 메트릭 (현재 상태를 측정)

                // 현재 사용 중인 활성 커넥션 수
                // bean -> bean.getActiveConnections(): 메트릭 값을 가져오는 함수
                Gauge.builder("hikaricp.connections.active", poolMXBean, bean -> bean.getActiveConnections())
                        .description("현재 활성 커넥션 수")
                        .register(registry);

                // 현재 사용하지 않고 있는 유휴 커넥션 수
                // 풀에서 대기 중인 커넥션들의 개수
                Gauge.builder("hikaricp.connections.idle", poolMXBean, bean -> bean.getIdleConnections())
                        .description("현재 유휴 커넥션 수")
                        .register(registry);

                // 전체 커넥션 수 (활성 + 유휴)
                // 실제로 생성되어 풀에 존재하는 모든 커넥션의 총합
                Gauge.builder("hikaricp.connections.total", poolMXBean, bean -> bean.getTotalConnections())
                        .description("총 커넥션 수")
                        .register(registry);

                // === 시간 측정 관련 Timer 메트릭 등록 ===
                // Timer: 이벤트의 지속 시간과 발생 빈도를 측정하는 메트릭

                // 커넥션 획득에 걸리는 시간 측정
                // 애플리케이션이 커넥션을 요청했을 때부터 실제로 받을 때까지의 시간
                Timer.builder("hikaricp.connections.acquire")
                        .description("커넥션 획득 시간")
                        // 백분위수(percentile) 측정: 50%, 95%, 99% 지점의 응답 시간
                        // 예: 95% 요청이 100ms 이내에 커넥션을 받았다는 의미
                        .publishPercentiles(0.5, 0.95, 0.99)
                        // 히스토그램 형태로 분포 정보도 함께 수집
                        // 시간대별 요청 분포를 시각화할 수 있음
                        .publishPercentileHistogram()
                        .register(registry);

                // 커넥션 사용 시간 측정
                // 커넥션을 획득한 후부터 반납할 때까지의 시간 (실제 DB 작업 시간)
                Timer.builder("hikaricp.connections.usage")
                        .description("커넥션 사용 시간")
                        // 동일하게 백분위수와 히스토그램 정보 수집
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .publishPercentileHistogram()
                        .register(registry);
            }
        };
    }
}