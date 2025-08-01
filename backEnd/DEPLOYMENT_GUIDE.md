# HikariCP 모니터링 배포 및 운영 가이드

## 🚀 배포 단계

### 1단계: 모니터링 인프라 배포

```bash
# 1. 모니터링 스택 시작
docker-compose up -d

# 2. 컨테이너 상태 확인
docker ps

# 3. 로그 확인
docker-compose logs prometheus
docker-compose logs grafana
```

### 2단계: Spring Boot 애플리케이션 배포

```bash
# 1. 애플리케이션 빌드
./gradlew build -x test

# 2. 애플리케이션 실행
./gradlew bootRun

# 또는 JAR 파일로 실행
java -jar build/libs/BidFlow-0.0.1-SNAPSHOT.jar
```

### 3단계: 모니터링 검증

#### Prometheus 검증
1. 브라우저에서 http://localhost:9090 접속
2. Status → Targets 메뉴 확인
3. `spring-boot-app` 타겟이 UP 상태인지 확인
4. Graph 메뉴에서 `hikaricp_connections_active` 검색

#### Grafana 검증
1. 브라우저에서 http://localhost:3001 접속
2. 로그인: admin/admin
3. Configuration → Data Sources에서 Prometheus 연결 확인
4. Dashboards에서 HikariCP 대시보드 확인

## 📊 모니터링 메트릭 설명

### HikariCP 기본 메트릭

| 메트릭명 | 설명 | 임계값 |
|---------|------|--------|
| `hikaricp_connections_active` | 현재 활성 커넥션 수 | 경고: 15, 심각: 18 |
| `hikaricp_connections_idle` | 현재 유휴 커넥션 수 | 경고: < 2 |
| `hikaricp_connections_total` | 총 커넥션 수 | - |
| `hikaricp_connections_pending` | 대기 중인 스레드 수 | 경고: > 0 |
| `hikaricp_pool_usage_percentage` | 커넥션 풀 사용률 | 경고: 80%, 심각: 95% |

### 성능 메트릭

| 메트릭명 | 설명 | 임계값 |
|---------|------|--------|
| `hikaricp_connections_acquire_seconds` | 커넥션 획득 시간 | 경고: 1초, 심각: 5초 |
| `hikaricp_connections_usage_seconds` | 커넥션 사용 시간 | - |

## 🔔 알림 규칙 설명

### 1. 높은 커넥션 풀 사용률 (HighConnectionPoolUsage)
- **조건**: 사용률 > 80%
- **지속시간**: 5분
- **심각도**: Warning
- **대응**: 커넥션 풀 크기 증가 고려

### 2. 커넥션 획득 타임아웃 (ConnectionAcquisitionTimeout)
- **조건**: 평균 획득 시간 > 1초
- **지속시간**: 2분
- **심각도**: Critical
- **대응**: 데이터베이스 성능 확인

### 3. 사용 가능한 커넥션 없음 (NoAvailableConnections)
- **조건**: 대기 중인 스레드 > 0
- **지속시간**: 1분
- **심각도**: Critical
- **대응**: 즉시 조치 필요

### 4. 심각한 커넥션 풀 사용률 (CriticalConnectionPoolUsage)
- **조건**: 사용률 > 95%
- **지속시간**: 2분
- **심각도**: Critical
- **대응**: 즉시 조치 필요

## 🛠️ 운영 관리

### 일일 점검 항목

```bash
# 1. 컨테이너 상태 확인
docker ps

# 2. 로그 확인
docker-compose logs --tail=100 prometheus
docker-compose logs --tail=100 grafana

# 3. 메트릭 확인
curl http://localhost:8080/actuator/metrics/hikaricp.connections.active
curl http://localhost:8080/actuator/metrics/hikaricp.pool.usage.percentage
```

### 주간 점검 항목

1. **성능 트렌드 분석**
   - 커넥션 풀 사용률 패턴
   - 커넥션 획득 시간 트렌드
   - 대기 시간 패턴

2. **설정 최적화**
   - 커넥션 풀 크기 조정
   - 타임아웃 설정 조정
   - 알림 임계값 조정

### 월간 점검 항목

1. **용량 계획**
   - 트래픽 증가에 따른 예측
   - 데이터베이스 성능 분석
   - 인프라 확장 계획

2. **보안 점검**
   - 접근 권한 검토
   - 로그 보안 검토
   - 백업 정책 검토

## 🔧 문제 해결

### 일반적인 문제들

#### 1. Prometheus 타겟 연결 실패

**증상**: Prometheus UI에서 `spring-boot-app` 타겟이 DOWN 상태

**해결 방법**:
```bash
# 1. Spring Boot 앱 상태 확인
curl http://localhost:8080/actuator/health

# 2. Prometheus 엔드포인트 확인
curl http://localhost:8080/actuator/prometheus

# 3. 네트워크 연결 확인
docker exec auction-prometheus wget -qO- http://host.docker.internal:8080/actuator/prometheus
```

#### 2. Grafana 데이터소스 연결 실패

**증상**: Grafana에서 Prometheus 데이터소스 연결 오류

**해결 방법**:
1. Grafana UI에서 Configuration → Data Sources
2. Prometheus 데이터소스 설정 확인
3. URL이 `http://prometheus:9090`인지 확인
4. Test Connection 버튼으로 연결 테스트

#### 3. 메트릭 수집 지연

**증상**: Grafana에서 최신 데이터가 표시되지 않음

**해결 방법**:
```bash
# 1. Prometheus 설정 확인
docker exec auction-prometheus cat /etc/prometheus/prometheus.yml

# 2. 스크랩 간격 확인 (기본: 10초)
# 3. Spring Boot 앱 로그 확인
```

### 로그 확인 방법

#### Spring Boot 애플리케이션 로그
```bash
# 애플리케이션 실행 중 로그 확인
./gradlew bootRun

# 또는 JAR 실행 시 로그 확인
java -jar app.jar --logging.level.org.example.bidflow=DEBUG
```

#### Prometheus 로그
```bash
# Prometheus 컨테이너 로그 확인
docker-compose logs prometheus

# 실시간 로그 확인
docker-compose logs -f prometheus
```

#### Grafana 로그
```bash
# Grafana 컨테이너 로그 확인
docker-compose logs grafana

# 실시간 로그 확인
docker-compose logs -f grafana
```

## 📈 성능 최적화

### 커넥션 풀 최적화

#### 1. 풀 크기 조정
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20      # 기본값: 10
      minimum-idle: 5            # 기본값: 10
```

#### 2. 타임아웃 설정 조정
```yaml
spring:
  datasource:
    hikari:
      connection-timeout: 30000  # 30초
      idle-timeout: 600000       # 10분
      max-lifetime: 1800000      # 30분
```

### 메트릭 수집 최적화

#### 1. 수집 주기 조정
```yaml
# prometheus-server.yml
scrape_configs:
  - job_name: 'spring-boot-app'
    scrape_interval: 10s  # 기본값: 15s
```

#### 2. 메트릭 필터링
```yaml
# application.yml
management:
  metrics:
    enable:
      hikaricp: true
    distribution:
      percentiles-histogram:
        hikaricp.connections: true
      percentiles:
        hikaricp.connections: 0.5, 0.95, 0.99
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

### 복구 방법
```bash
# 설정 파일 복구
cp prometheus-server.yml.backup prometheus-server.yml
cp hikaricp_alerts.yml.backup hikaricp_alerts.yml
cp -r grafana.backup grafana

# 컨테이너 재시작
docker-compose restart prometheus grafana
```

## 📚 추가 리소스

### 공식 문서
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Micrometer](https://micrometer.io/docs)
- [Prometheus](https://prometheus.io/docs/)
- [Grafana](https://grafana.com/docs/)

### 커뮤니티 리소스
- [Spring Boot Metrics Examples](https://github.com/spring-projects/spring-boot/tree/main/spring-boot-samples)
- [Prometheus Best Practices](https://prometheus.io/docs/practices/)
- [Grafana Dashboards](https://grafana.com/grafana/dashboards/) 