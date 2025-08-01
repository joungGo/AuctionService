# HikariCP 모니터링 체크리스트

## 📋 사전 준비 사항

### ✅ Spring Boot 애플리케이션 설정
- [ ] `build.gradle`에 의존성 추가 완료
  - [ ] `spring-boot-starter-actuator`
  - [ ] `micrometer-registry-prometheus`
- [ ] `application.yml` 설정 완료
  - [ ] Actuator 엔드포인트 노출 설정
  - [ ] HikariCP 메트릭 활성화
  - [ ] Prometheus 메트릭 활성화
- [ ] HikariCP 메트릭 설정 클래스 생성 완료
  - [ ] `HikariMetricsConfig.java`
  - [ ] `HikariMetricsCollector.java`

### ✅ 모니터링 인프라 설정
- [ ] Prometheus 설정 완료
  - [ ] `prometheus-server.yml` 생성
  - [ ] `hikaricp_alerts.yml` 생성
- [ ] Grafana 설정 완료
  - [ ] 데이터소스 설정 (`prometheus-datasource.yml`)
  - [ ] 대시보드 설정 (`hikaricp-dashboard.json`)
- [ ] Docker Compose 설정 완료
  - [ ] `docker-compose.yml` 생성
  - [ ] `.gitignore`에 모니터링 파일 추가

## 🚀 배포 및 실행

### ✅ Docker 컨테이너 실행
- [ ] 모니터링 스택 시작
  ```bash
  docker-compose up -d
  ```
- [ ] 컨테이너 상태 확인
  ```bash
  docker ps
  ```
- [ ] 로그 확인
  ```bash
  docker-compose logs prometheus
  docker-compose logs grafana
  ```

### ✅ Spring Boot 애플리케이션 실행
- [ ] 애플리케이션 시작
  ```bash
  ./gradlew bootRun
  ```
- [ ] Actuator 엔드포인트 확인
  - [ ] http://localhost:8080/actuator/health
  - [ ] http://localhost:8080/actuator/metrics
  - [ ] http://localhost:8080/actuator/prometheus

## 🔍 모니터링 검증

### ✅ Prometheus 검증
- [ ] Prometheus UI 접속: http://localhost:9090
- [ ] Targets 페이지에서 Spring Boot 앱 상태 확인
- [ ] Graph 페이지에서 HikariCP 메트릭 확인
  - [ ] `hikaricp_connections_active`
  - [ ] `hikaricp_connections_idle`
  - [ ] `hikaricp_pool_usage_percentage`

### ✅ Grafana 검증
- [ ] Grafana UI 접속: http://localhost:3001
- [ ] 로그인: admin/admin
- [ ] 데이터소스 확인
  - [ ] Prometheus 데이터소스 연결 상태
- [ ] 대시보드 확인
  - [ ] HikariCP Connection Pool Monitoring 대시보드 로드
  - [ ] 메트릭 데이터 표시 확인

## 📊 메트릭 검증

### ✅ HikariCP 메트릭 확인
- [ ] 기본 메트릭
  - [ ] 활성 커넥션 수
  - [ ] 유휴 커넥션 수
  - [ ] 총 커넥션 수
  - [ ] 대기 중인 스레드 수
- [ ] 성능 메트릭
  - [ ] 커넥션 획득 시간
  - [ ] 커넥션 사용 시간
  - [ ] 풀 사용률

### ✅ 알림 규칙 검증
- [ ] Prometheus 알림 규칙 확인
  - [ ] 높은 커넥션 풀 사용률 알림
  - [ ] 커넥션 획득 타임아웃 알림
  - [ ] 사용 가능한 커넥션 없음 알림

## 🧪 테스트 시나리오

### ✅ 부하 테스트
- [ ] 데이터베이스 연결 부하 생성
- [ ] 커넥션 풀 사용률 증가 확인
- [ ] 알림 트리거 확인
- [ ] 메트릭 변화 추이 확인

### ✅ 장애 시나리오 테스트
- [ ] 데이터베이스 연결 끊김 시뮬레이션
- [ ] 커넥션 풀 고갈 상황 시뮬레이션
- [ ] 알림 동작 확인

## 📈 운영 모니터링

### ✅ 정기 점검 항목
- [ ] 일일 메트릭 리뷰
- [ ] 주간 성능 분석
- [ ] 월간 트렌드 분석
- [ ] 알림 설정 최적화

### ✅ 성능 최적화
- [ ] 커넥션 풀 크기 조정
- [ ] 타임아웃 설정 최적화
- [ ] 메트릭 수집 주기 조정

## 🔧 문제 해결

### ✅ 일반적인 문제들
- [ ] Prometheus 타겟 연결 실패
- [ ] Grafana 데이터소스 연결 실패
- [ ] 메트릭 수집 지연
- [ ] 알림 미발송

### ✅ 로그 확인 방법
- [ ] Spring Boot 애플리케이션 로그
- [ ] Prometheus 로그
- [ ] Grafana 로그
- [ ] Docker 컨테이너 로그

## 📚 참고 자료

### 📖 문서
- [Spring Boot Actuator 공식 문서](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Micrometer 공식 문서](https://micrometer.io/docs)
- [Prometheus 공식 문서](https://prometheus.io/docs/)
- [Grafana 공식 문서](https://grafana.com/docs/)

### 🔗 유용한 링크
- [HikariCP GitHub](https://github.com/brettwooldridge/HikariCP)
- [Spring Boot Metrics](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.metrics) 