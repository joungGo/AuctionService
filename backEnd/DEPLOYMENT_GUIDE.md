# 배포 가이드

## 환경 설정

### 프론트엔드 (Vercel) 환경 변수 설정

Vercel 대시보드에서 다음 환경 변수를 설정하세요:

```bash
# API Base URL
NEXT_PUBLIC_API_URL=https://bidflow.cloud/api

# WebSocket URL - AWS Load Balancer DNS 사용
NEXT_PUBLIC_WS_URL=wss://Load-Balancer-114836647.ap-southeast-2.elb.amazonaws.com/ws

# Backend URL for API proxy (server-side only)
BACKEND_URL=http://52.65.242.120:8080
```

### 백엔드 (AWS) 설정

#### 1. Load Balancer 설정 확인
- HTTPS 리스너(443)가 올바르게 설정되어 있는지 확인
- WebSocket 규칙이 `/ws*` 경로와 `Upgrade: websocket` 헤더를 처리하도록 설정
- Target Group이 올바른 EC2 인스턴스를 가리키고 있는지 확인

#### 2. 보안 그룹 설정
- Load Balancer 보안 그룹: HTTPS(443) 포트 허용
- EC2 보안 그룹: Load Balancer에서 8080 포트 허용

#### 3. 백엔드 애플리케이션 설정
- `application.yml`의 origin 설정에 Load Balancer DNS 추가
- WebSocket 설정이 Load Balancer 환경에 최적화되어 있는지 확인
- Spring Boot 3.4.3 호환 WebSocket 설정 적용

#### 4. WebSocket 최적화 설정
- Tomcat 연결 풀 설정: `max-connections: 8192`
- 스레드 풀 설정: `max: 200`, `min-spare: 10`
- HTTP 헤더 크기: `max-http-header-size: 8192`

## WebSocket 연결 문제 해결

### Mixed Content 에러 해결
1. 프론트엔드에서 `wss://` 프로토콜 사용 확인
2. Load Balancer DNS 사용으로 직접 EC2 IP 연결 방지
3. 환경 변수 `NEXT_PUBLIC_WS_URL` 설정 확인

### 연결 실패 시 확인사항
1. Load Balancer Health Check 상태 확인
2. 백엔드 로그에서 WebSocket 연결 요청 확인
3. CORS 설정에서 Load Balancer DNS 허용 확인
4. 네트워크 연결 및 방화벽 설정 확인

## 모니터링

### 로그 확인
```bash
# WebSocket 연결 관련 로그
tail -f logs/bidflow-application.log | grep -E "(WebSocket|STOMP)"

# Load Balancer 관련 로그
tail -f logs/bidflow-application.log | grep -E "(X-Forwarded|Forward)"
```

### 성능 모니터링
- AWS CloudWatch를 통한 Load Balancer 메트릭 모니터링
- 백엔드 애플리케이션 로그 모니터링
- WebSocket 연결 수 및 메시지 처리량 모니터링 