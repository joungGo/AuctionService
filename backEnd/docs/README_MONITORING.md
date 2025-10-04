# HikariCP 모니터링 시스템

Spring Boot 애플리케이션의 HikariCP 커넥션 풀을 실시간으로 모니터링하는 시스템입니다.

## 🎯 개요

이 시스템은 다음 기술 스택을 사용하여 HikariCP 커넥션 풀의 상태를 실시간으로 모니터링합니다:

- **Spring Boot Actuator**: 애플리케이션 메트릭 노출
- **Micrometer**: 메트릭 수집 및 표준화
- **Prometheus**: 메트릭 수집 및 저장
- **Grafana**: 메트릭 시각화 및 대시보드
- **Docker Compose**: 모니터링 인프라 오케스트레이션

## 🏗️ 아키텍처

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Spring Boot   │    │    Prometheus   │    │     Grafana     │
│   Application   │───▶│   (Metrics DB)  │───▶│  (Dashboard)    │
│                 │    │                 │    │                 │
│ - HikariCP      │    │ - Scrape Config │    │ - Data Source   │
│ - Actuator      │    │ - Alert Rules   │    │ - Dashboards    │
│ - Micrometer    │    │ - Time Series   │    │ - Alerts        │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## 🚀 빠른 시작

### 1. 모니터링 인프라 시작

```bash
# 모니터링 스택 시작
docker-compose up -d

# 상태 확인
docker ps
```

### 2. Spring Boot 애플리케이션 시작

```bash
# 애플리케이션 실행
./gradlew bootRun
```

### 3. 모니터링 대시보드 접속

- **Prometheus**: http://localhost:9090
- **Grafana**: http://localhost:3001 (admin/admin)

## 📊 모니터링 메트릭

### HikariCP 커넥션 풀 메트릭

| 메트릭명 | 설명 | 임계값 |
|---------|------|--------|
| `hikaricp_connections_active` | 활성 커넥션 수 | 경고: 15, 심각: 18 |
| `hikaricp_connections_idle` | 유휴 커넥션 수 | 경고: < 2 |
| `hikaricp_connections_total` | 총 커넥션 수 | - |
| `hikaricp_connections_pending` | 대기 중인 스레드 수 | 경고: > 0 |
| `hikaricp_pool_usage_percentage` | 커넥션 풀 사용률 | 경고: 80%, 심각: 95% |

### 성능 메트릭

| 메트릭명 | 설명 | 임계값 |
|---------|------|--------|
| `hikaricp_connections_acquire_seconds` | 커넥션 획득 시간 | 경고: 1초, 심각: 5초 |
| `hikaricp_connections_usage_seconds` | 커넥션 사용 시간 | - |

## 🔔 알림 규칙

시스템은 다음 상황에서 자동으로 알림을 발생시킵니다:

1. **높은 커넥션 풀 사용률** (80% 초과)
2. **커넥션 획득 타임아웃** (1초 초과)
3. **사용 가능한 커넥션 없음** (대기 스레드 발생)
4. **심각한 커넥션 풀 사용률** (95% 초과)

## 📁 프로젝트 구조

```
backEnd/
├── docker-compose.yml              # 모니터링 인프라 설정
├── prometheus-server.yml           # Prometheus 설정
├── hikaricp_alerts.yml            # 알림 규칙
├── grafana/                       # Grafana 설정
│   └── provisioning/
│       ├── datasources/
│       │   └── prometheus-datasource.yml
│       └── dashboards/
│           ├── dashboard.yml
│           └── hikaricp-dashboard.json
├── src/main/java/org/example/bidflow/
│   └── global/config/
│       ├── HikariMetricsConfig.java    # HikariCP 메트릭 설정
│       └── HikariMetricsCollector.java # 메트릭 수집기
├── MONITORING_CHECKLIST.md        # 모니터링 체크리스트
├── DEPLOYMENT_GUIDE.md           # 배포 및 운영 가이드
└── README_MONITORING.md          # 이 파일
```

## 🛠️ 설정 파일

### application.yml

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    enable:
      hikaricp: true
    distribution:
      percentiles-histogram:
        hikaricp.connections: true
      percentiles:
        hikaricp.connections: 0.5, 0.95, 0.99
```

### build.gradle

```gradle
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'io.micrometer:micrometer-registry-prometheus'
}
```

## 🔧 문제 해결

### 일반적인 문제들

1. **Prometheus 타겟 연결 실패**
   - Spring Boot 앱이 실행 중인지 확인
   - Actuator 엔드포인트 접근 가능한지 확인

2. **Grafana 데이터소스 연결 실패**
   - Prometheus 컨테이너가 실행 중인지 확인
   - 네트워크 설정 확인

3. **메트릭 수집 지연**
   - 스크랩 간격 설정 확인
   - 애플리케이션 로그 확인

### 로그 확인

```bash
# Prometheus 로그
docker-compose logs prometheus

# Grafana 로그
docker-compose logs grafana

# Spring Boot 앱 로그
./gradlew bootRun
```

## 📈 성능 최적화

### 커넥션 풀 설정 최적화

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20      # 트래픽에 따라 조정
      minimum-idle: 5            # 최소 유휴 커넥션
      connection-timeout: 30000  # 커넥션 타임아웃
      idle-timeout: 600000       # 유휴 타임아웃
      max-lifetime: 1800000      # 최대 수명
```

### 모니터링 설정 최적화

```yaml
# prometheus-server.yml
scrape_configs:
  - job_name: 'spring-boot-app'
    scrape_interval: 10s  # 수집 주기 조정
```

## 🔄 백업 및 복구

### 설정 파일 백업

```bash
# 설정 파일 백업
cp prometheus-server.yml prometheus-server.yml.backup
cp hikaricp_alerts.yml hikaricp_alerts.yml.backup
cp -r grafana grafana.backup
```

### 데이터 백업

```bash
# Prometheus 데이터 백업
docker exec auction-prometheus tar -czf /prometheus/backup.tar.gz /prometheus

# Grafana 데이터 백업
docker exec auction-grafana tar -czf /var/lib/grafana/backup.tar.gz /var/lib/grafana
```

## 📚 추가 자료

- [MONITORING_CHECKLIST.md](MONITORING_CHECKLIST.md) - 상세한 체크리스트
- [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md) - 배포 및 운영 가이드

## 🤝 기여

이 모니터링 시스템을 개선하려면:

1. 이슈를 생성하여 문제점이나 개선사항을 제안
2. Pull Request를 통해 코드 개선 제안
3. 문서 개선 제안

## 📄 라이선스

이 프로젝트는 MIT 라이선스 하에 배포됩니다. 